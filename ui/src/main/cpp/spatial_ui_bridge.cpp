#include <jni.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <android/log.h>
#include <algorithm>
#include <cstring>
#include <limits>
#include <vector>

namespace {
constexpr const char* kTag = "MoonlightSpatialUI";

constexpr const char* kVertex = R"(#version 300 es
out vec2 uv;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    uv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
})";

constexpr const char* kFragment = R"(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
in vec2 uv;
uniform samplerExternalOES image;
uniform mat4 transform;
uniform vec4 checkMode;
uniform vec4 checkPicture;
out vec4 color;
void main() {
    // OES samples from this Canvas producer are gamma-premultiplied sRGB.
    // OpenXR expects linear-premultiplied RGB, even with an sRGB swapchain.
    vec4 encoded=texture(image,(transform*vec4(uv,0.0,1.0)).xy);
    vec3 straight=encoded.a>0.0 ? clamp(encoded.rgb/encoded.a,0.0,1.0) : vec3(0.0);
    bool checkPixel=checkMode.x>.5 && all(greaterThan(uv,vec2(.12))) && all(lessThan(uv,vec2(.88)));
    if (checkPixel) {
        straight=(straight*checkPicture.x-.5)*checkPicture.y+.5;
        straight=mix(vec3(dot(straight,vec3(.2126,.7152,.0722))),straight,checkPicture.w);
        straight=pow(clamp(straight,0.0,1.0),vec3(1.0/max(.01,checkPicture.z)));
        // Negative control: really corrupt GPU output. The CPU reference stays correct.
        if(checkMode.y>.5 && checkMode.y<1.5) straight=mix(pow((straight+.055)/1.055,vec3(2.4)),straight/12.92,
            lessThanEqual(straight,vec3(.04045)));
    }
    vec3 linear=mix(pow((straight+0.055)/1.055,vec3(2.4)),straight/12.92,
                    lessThanEqual(straight,vec3(0.04045)));
    // Debug A/B: test the proposed removal of input EOTF, without changing attachment format.
    if(checkPixel && checkMode.y>1.5) linear=straight;
    color=vec4(linear*encoded.a,encoded.a);
}
)";

GLuint compile(GLenum kind, const char* source) {
    const GLuint shader = glCreateShader(kind);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (ok != GL_TRUE) {
        char log[1024]{};
        glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        __android_log_print(ANDROID_LOG_ERROR, kTag, "shader: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint program() {
    static GLuint id = 0; // One GLES context per OpenXR render thread; recreate after context loss.
    if (id != 0 && glIsProgram(id)) return id;
    const GLuint vertex = compile(GL_VERTEX_SHADER, kVertex);
    const GLuint fragment = compile(GL_FRAGMENT_SHADER, kFragment);
    if (!vertex || !fragment) {
        if (vertex) glDeleteShader(vertex);
        if (fragment) glDeleteShader(fragment);
        return 0;
    }
    id = glCreateProgram();
    glAttachShader(id, vertex);
    glAttachShader(id, fragment);
    glLinkProgram(id);
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    GLint ok = GL_FALSE;
    glGetProgramiv(id, GL_LINK_STATUS, &ok);
    if (ok != GL_TRUE) {
        glDeleteProgram(id);
        id = 0;
    }
    return id;
}

bool hasSrgbWriteControl() {
    static const bool supported = [] {
        const auto* extensions = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
        if (!extensions) return false;
        constexpr const char* name = "GL_EXT_sRGB_write_control";
        const size_t length = std::strlen(name);
        for (const char* found = std::strstr(extensions, name); found;
             found = std::strstr(found + length, name)) {
            const bool start = found == extensions || found[-1] == ' ';
            const bool end = found[length] == '\0' || found[length] == ' ';
            if (start && end) return true;
        }
        return false;
    }();
    return supported;
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_moonlightxr_nativeui_NativeGlesBridge_createExternalTexture(JNIEnv*, jobject) {
    GLuint texture = 0;
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    return static_cast<jint>(texture);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moonlightxr_nativeui_NativeGlesBridge_copyToAcquiredSwapchain(
    JNIEnv* env, jobject, jint external, jint destination, jint width, jint height,
    jfloatArray matrix, jboolean directGlQaPattern, jint layerRole, jfloatArray colorCheckParameters) {
    if (external <= 0 || destination <= 0 || width <= 0 || height <= 0 || !matrix || env->GetArrayLength(matrix) < 16)
        return JNI_FALSE;
    const GLuint shader = directGlQaPattern ? 0 : program();
    if (!directGlQaPattern && !shader) return JNI_FALSE;

    GLint oldFbo = 0, oldProgram = 0, oldActiveTexture = 0, oldOesTexture = 0, oldViewport[4]{};
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &oldFbo);
    glGetIntegerv(GL_CURRENT_PROGRAM, &oldProgram);
    glGetIntegerv(GL_ACTIVE_TEXTURE, &oldActiveTexture);
    glGetIntegerv(GL_VIEWPORT, oldViewport);
    glActiveTexture(GL_TEXTURE0);
    glGetIntegerv(GL_TEXTURE_BINDING_EXTERNAL_OES, &oldOesTexture);
    const GLboolean blend = glIsEnabled(GL_BLEND);
    const GLboolean depth = glIsEnabled(GL_DEPTH_TEST);
    const GLboolean cull = glIsEnabled(GL_CULL_FACE);
    const bool srgbWriteControl = hasSrgbWriteControl();
    GLboolean srgbWriteWasEnabled = GL_FALSE;
    bool srgbWriteManaged = false;
    GLuint fbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                           static_cast<GLuint>(destination), 0);
    const bool ready = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    if (ready) {
        GLint attachmentEncoding = GL_NONE;
        glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
            GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING, &attachmentEncoding);
        if (srgbWriteControl) srgbWriteWasEnabled = glIsEnabled(GL_FRAMEBUFFER_SRGB_EXT);
        glViewport(0, 0, width, height);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        // The UI shader outputs linear-premultiplied RGB. The sRGB attachment must encode once.
        if (srgbWriteControl && !directGlQaPattern) {
            glEnable(GL_FRAMEBUFFER_SRGB_EXT);
            srgbWriteManaged = true;
        }
        static bool reported = false;
        if (!reported) {
            reported = true;
            __android_log_print(ANDROID_LOG_INFO, kTag,
                "GLES colour state extSrgbWrite=%d attachmentEncoding=0x%x before=%d forced=%d directPattern=%d",
                srgbWriteControl ? 1 : 0, attachmentEncoding,
                srgbWriteWasEnabled == GL_TRUE ? 1 : 0, srgbWriteManaged ? 1 : 0,
                directGlQaPattern ? 1 : 0);
        }
        if (directGlQaPattern) {
            GLfloat oldClear[4]{};
            glGetFloatv(GL_COLOR_CLEAR_VALUE, oldClear);
            if (layerRole == 0) glClearColor(0.9f, 0.1f, 0.6f, 1.f);
            else if (layerRole == 1) glClearColor(0.1f, 0.8f, 0.4f, 1.f);
            else glClearColor(0.1f, 0.4f, 0.9f, 1.f);
            glClear(GL_COLOR_BUFFER_BIT);
            glClearColor(oldClear[0], oldClear[1], oldClear[2], oldClear[3]);
        } else {
            glUseProgram(shader);
            glBindTexture(GL_TEXTURE_EXTERNAL_OES, static_cast<GLuint>(external));
            glUniform1i(glGetUniformLocation(shader, "image"), 0);
            jfloat values[16]{};
            env->GetFloatArrayRegion(matrix, 0, 16, values);
            glUniformMatrix4fv(glGetUniformLocation(shader, "transform"), 1, GL_FALSE, values);
            jfloat check[8]={0,0,0,0,1,1,1,1};
            if(colorCheckParameters && env->GetArrayLength(colorCheckParameters)==8)
                env->GetFloatArrayRegion(colorCheckParameters,0,8,check);
            glUniform4fv(glGetUniformLocation(shader,"checkMode"),1,check);
            glUniform4fv(glGetUniformLocation(shader,"checkPicture"),1,check+4);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }
#ifndef NDEBUG
        // A successful xrEndFrame only proves that a layer was submitted, not that Compose
        // supplied visible pixels. Sample each surface once without copying the whole frame.
        static int layerFrames[3]{};
        const int role = std::clamp(static_cast<int>(layerRole), 0, 2);
        const char* roleName[] = {"main", "popup", "extension"};
        int& frame = layerFrames[role];
        ++frame;
        if (frame == 1 || frame == 60 || frame == 180) {
            GLubyte center[4]{};
            GLubyte quarter[4]{};
            glReadPixels(width / 2, height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, center);
            glReadPixels(width / 4, height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, quarter);
            __android_log_print(ANDROID_LOG_INFO, kTag,
                "%s swapchain frame=%d center=%u,%u,%u,%u quarter=%u,%u,%u,%u",
                roleName[role], frame,
                center[0], center[1], center[2], center[3],
                quarter[0], quarter[1], quarter[2], quarter[3]);
            if (frame == 180) {
                std::vector<GLubyte> pixels(static_cast<size_t>(width) * height * 4);
                glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
                size_t colored = 0;
                size_t opaque = 0;
                GLubyte maxColor = 0;
                for (size_t i = 0; i < pixels.size(); i += 4) {
                    const GLubyte brightness = static_cast<GLubyte>(
                        std::max({pixels[i], pixels[i + 1], pixels[i + 2]}));
                    if (brightness > 8) ++colored;
                    if (pixels[i + 3] > 0) ++opaque;
                    maxColor = std::max(maxColor, brightness);
                }
                __android_log_print(ANDROID_LOG_INFO, kTag,
                    "%s swapchain coverage colored=%zu opaque=%zu total=%zu maxColor=%u",
                    roleName[role], colored, opaque,
                    static_cast<size_t>(width) * height, maxColor);
            }
        }
#endif
    }
    if (srgbWriteManaged) {
        if (srgbWriteWasEnabled == GL_TRUE) glEnable(GL_FRAMEBUFFER_SRGB_EXT);
        else glDisable(GL_FRAMEBUFFER_SRGB_EXT);
    }
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, static_cast<GLuint>(oldOesTexture));
    glActiveTexture(static_cast<GLenum>(oldActiveTexture));
    glUseProgram(static_cast<GLuint>(oldProgram));
    glBindFramebuffer(GL_FRAMEBUFFER, static_cast<GLuint>(oldFbo));
    glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
    if (blend) glEnable(GL_BLEND);
    if (depth) glEnable(GL_DEPTH_TEST);
    if (cull) glEnable(GL_CULL_FACE);
    glDeleteFramebuffers(1, &fbo);
    return ready && glGetError() == GL_NO_ERROR ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_moonlightxr_nativeui_NativeGlesBridge_readAcquiredTextureRgba(
    JNIEnv* env, jobject, jint texture, jint width, jint height) {
    // Product diagnostics: only read while the render thread owns the acquired image.
    // This path is invoked once on explicit user request, never during ordinary rendering.
    if (texture <= 0 || width <= 0 || height <= 0 || width>4096 || height>4096) return nullptr;
    const size_t byteCount = static_cast<size_t>(width) * static_cast<size_t>(height) * 4;
    if (byteCount > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;

    GLint oldFbo = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &oldFbo);
    GLuint fbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                           static_cast<GLuint>(texture), 0);
    jbyteArray result = nullptr;
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
        std::vector<GLubyte> pixels(byteCount);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
        if (glGetError() == GL_NO_ERROR) {
            result = env->NewByteArray(static_cast<jsize>(byteCount));
            if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(byteCount),
                                                 reinterpret_cast<const jbyte*>(pixels.data()));
        }
    }
    glBindFramebuffer(GL_FRAMEBUFFER, static_cast<GLuint>(oldFbo));
    glDeleteFramebuffers(1, &fbo);
    return result;
}
