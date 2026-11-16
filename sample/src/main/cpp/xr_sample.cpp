#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include <android/log.h>
#include <atomic>
#include <thread>
#include <vector>
#include <string>
#include <cstring>
#include <algorithm>
#include <stdexcept>
#include <chrono>

namespace {
void check(XrResult r, const char* op) { if (XR_FAILED(r)) throw std::runtime_error(std::string(op)+": "+std::to_string(r)); }
struct Host {
    JavaVM* vm{}; jobject activity{}; std::thread thread; std::atomic<bool> stop{false}, resumed{false};
    JNIEnv* env{}; XrInstance instance{}; XrSystemId system{}; XrSession session{};
    XrSpace local{}, aim{}; XrSwapchain swapchain{}; XrActionSet actions{}; XrAction pose{}, click{};
    XrSessionState state{XR_SESSION_STATE_UNKNOWN}; bool running=false;
    EGLDisplay display=EGL_NO_DISPLAY; EGLContext context=EGL_NO_CONTEXT; EGLSurface surface=EGL_NO_SURFACE;
    std::vector<XrSwapchainImageOpenGLESKHR> images;
    XrEnvironmentBlendMode blend=XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    jmethodID create{}, draw{}, input{}, status{};
    Host(JNIEnv* e,jobject a) { e->GetJavaVM(&vm); activity=e->NewGlobalRef(a); }
    ~Host() { stop=true; if(thread.joinable()) thread.join(); JNIEnv* e{}; vm->GetEnv(reinterpret_cast<void**>(&e),JNI_VERSION_1_6); if(e) e->DeleteGlobalRef(activity); }
    void report(const std::string& s) {
        __android_log_print(ANDROID_LOG_INFO,"SpatialOpenXR", "%s",s.c_str());
        if(status) { auto text=env->NewStringUTF(s.c_str()); env->CallVoidMethod(activity,status,text); env->DeleteLocalRef(text); }
    }
    void javaCheck() { if(env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); throw std::runtime_error("Java callback failed"); } }
    void setup() {
        auto cls=env->GetObjectClass(activity);
        create=env->GetMethodID(cls,"createProducer","()V");
        draw=env->GetMethodID(cls,"drawProducer","(I)Z");
        input=env->GetMethodID(cls,"inputRay","(FFFFFFZZ)V");
        status=env->GetMethodID(cls,"reportStatus","(Ljava/lang/String;)V"); env->DeleteLocalRef(cls); javaCheck();
        PFN_xrInitializeLoaderKHR initialize{};
        check(xrGetInstanceProcAddr(XR_NULL_HANDLE,"xrInitializeLoaderKHR",reinterpret_cast<PFN_xrVoidFunction*>(&initialize)),"loader init proc");
        XrLoaderInitInfoAndroidKHR loader{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR}; loader.applicationVM=vm; loader.applicationContext=activity;
        check(initialize(reinterpret_cast<XrLoaderInitInfoBaseHeaderKHR*>(&loader)),"loader init");
        uint32_t count=0; check(xrEnumerateInstanceExtensionProperties(nullptr,0,&count,nullptr),"extensions count");
        std::vector<XrExtensionProperties> available(count,{XR_TYPE_EXTENSION_PROPERTIES});
        check(xrEnumerateInstanceExtensionProperties(nullptr,count,&count,available.data()),"extensions");
        const char* required[]={XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME};
        for(auto name:required) if(std::none_of(available.begin(),available.end(),[&](auto& x){return !strcmp(name,x.extensionName);})) throw std::runtime_error(std::string("Missing required graphics/platform extension: ")+name);
        XrInstanceCreateInfoAndroidKHR android{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR}; android.applicationVM=vm; android.applicationActivity=activity;
        XrInstanceCreateInfo ci{XR_TYPE_INSTANCE_CREATE_INFO}; ci.next=&android; strcpy(ci.applicationInfo.applicationName,"Spatial UI Sample");
        ci.applicationInfo.apiVersion=XR_API_VERSION_1_0; ci.enabledExtensionCount=2; ci.enabledExtensionNames=required;
        check(xrCreateInstance(&ci,&instance),"instance");
        XrSystemGetInfo si{XR_TYPE_SYSTEM_GET_INFO}; si.formFactor=XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY; check(xrGetSystem(instance,&si,&system),"system");
        uint32_t bc=0; check(xrEnumerateEnvironmentBlendModes(instance,system,XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,0,&bc,nullptr),"blend count");
        std::vector<XrEnvironmentBlendMode> modes(bc); check(xrEnumerateEnvironmentBlendModes(instance,system,XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,bc,&bc,modes.data()),"blend modes");
        if(modes.empty()) throw std::runtime_error("No stereo blend modes"); blend=modes.front();
        PFN_xrGetOpenGLESGraphicsRequirementsKHR requirements{};
        check(xrGetInstanceProcAddr(instance,"xrGetOpenGLESGraphicsRequirementsKHR",reinterpret_cast<PFN_xrVoidFunction*>(&requirements)),"graphics proc");
        XrGraphicsRequirementsOpenGLESKHR req{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR}; check(requirements(instance,system,&req),"graphics requirements");
        display=eglGetDisplay(EGL_DEFAULT_DISPLAY); if(!eglInitialize(display,nullptr,nullptr)) throw std::runtime_error("eglInitialize");
        EGLint attrs[]={EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE};
        EGLConfig config{}; EGLint configs=0; if(!eglChooseConfig(display,attrs,&config,1,&configs)||!configs) throw std::runtime_error("EGL config");
        EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE}, pa[]={EGL_WIDTH,16,EGL_HEIGHT,16,EGL_NONE};
        context=eglCreateContext(display,config,EGL_NO_CONTEXT,ca); surface=eglCreatePbufferSurface(display,config,pa);
        if(context==EGL_NO_CONTEXT||surface==EGL_NO_SURFACE||!eglMakeCurrent(display,surface,surface,context)) throw std::runtime_error("EGL context");
        GLint major=0,minor=0; glGetIntegerv(GL_MAJOR_VERSION,&major); glGetIntegerv(GL_MINOR_VERSION,&minor);
        auto version=XR_MAKE_VERSION(major,minor,0); if(version<req.minApiVersionSupported||version>req.maxApiVersionSupported) throw std::runtime_error("GLES version outside runtime requirements");
        XrGraphicsBindingOpenGLESAndroidKHR binding{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR}; binding.display=display; binding.config=config; binding.context=context;
        XrSessionCreateInfo sc{XR_TYPE_SESSION_CREATE_INFO}; sc.next=&binding; sc.systemId=system; check(xrCreateSession(instance,&sc,&session),"session");
        XrReferenceSpaceCreateInfo rs{XR_TYPE_REFERENCE_SPACE_CREATE_INFO}; rs.referenceSpaceType=XR_REFERENCE_SPACE_TYPE_LOCAL; rs.poseInReferenceSpace.orientation.w=1;
        check(xrCreateReferenceSpace(session,&rs,&local),"local space");
        XrActionSetCreateInfo ac{XR_TYPE_ACTION_SET_CREATE_INFO}; strcpy(ac.actionSetName,"ui"); strcpy(ac.localizedActionSetName,"UI"); check(xrCreateActionSet(instance,&ac,&actions),"action set");
        auto action=[&](const char* name,XrActionType type,XrAction* result){ XrActionCreateInfo a{XR_TYPE_ACTION_CREATE_INFO}; strcpy(a.actionName,name); strcpy(a.localizedActionName,name); a.actionType=type; check(xrCreateAction(actions,&a,result),"action"); };
        action("aim",XR_ACTION_TYPE_POSE_INPUT,&pose); action("select",XR_ACTION_TYPE_BOOLEAN_INPUT,&click);
        auto path=[&](const char* s){XrPath p{};check(xrStringToPath(instance,s,&p),"path");return p;};
        XrActionSuggestedBinding bindings[]={{pose,path("/user/hand/right/input/aim/pose")},{click,path("/user/hand/right/input/select/click")}};
        XrInteractionProfileSuggestedBinding suggestion{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING}; suggestion.interactionProfile=path("/interaction_profiles/khr/simple_controller"); suggestion.countSuggestedBindings=2; suggestion.suggestedBindings=bindings;
        check(xrSuggestInteractionProfileBindings(instance,&suggestion),"simple controller binding");
        XrSessionActionSetsAttachInfo attach{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO}; attach.countActionSets=1; attach.actionSets=&actions; check(xrAttachSessionActionSets(session,&attach),"attach");
        XrActionSpaceCreateInfo as{XR_TYPE_ACTION_SPACE_CREATE_INFO}; as.action=pose; as.poseInActionSpace.orientation.w=1; check(xrCreateActionSpace(session,&as,&aim),"aim space");
        uint32_t fc=0; check(xrEnumerateSwapchainFormats(session,0,&fc,nullptr),"formats count"); std::vector<int64_t> formats(fc); check(xrEnumerateSwapchainFormats(session,fc,&fc,formats.data()),"formats");
        if(std::find(formats.begin(),formats.end(),GL_SRGB8_ALPHA8)==formats.end()) throw std::runtime_error("Sample requires sRGB RGBA swapchain");
        XrSwapchainCreateInfo sw{XR_TYPE_SWAPCHAIN_CREATE_INFO}; sw.usageFlags=XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT; sw.format=GL_SRGB8_ALPHA8; sw.sampleCount=1; sw.width=1200; sw.height=800; sw.faceCount=1; sw.arraySize=1; sw.mipCount=1;
        check(xrCreateSwapchain(session,&sw,&swapchain),"swapchain"); uint32_t ic=0; check(xrEnumerateSwapchainImages(swapchain,0,&ic,nullptr),"image count"); images.resize(ic,{XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR}); check(xrEnumerateSwapchainImages(swapchain,ic,&ic,reinterpret_cast<XrSwapchainImageBaseHeader*>(images.data())),"images");
        env->CallVoidMethod(activity,create); javaCheck(); report("Initialized; waiting for session READY. Right simple-controller aim/select.");
    }
    void frame() {
        XrFrameWaitInfo wait{XR_TYPE_FRAME_WAIT_INFO}; XrFrameState fs{XR_TYPE_FRAME_STATE}; check(xrWaitFrame(session,&wait,&fs),"wait frame");
        XrFrameBeginInfo begin{XR_TYPE_FRAME_BEGIN_INFO}; check(xrBeginFrame(session,&begin),"begin frame");
        XrCompositionLayerQuad quad{XR_TYPE_COMPOSITION_LAYER_QUAD}; bool ready=false;
        try {
            if(state==XR_SESSION_STATE_FOCUSED && resumed) {
                XrActiveActionSet active{actions,XR_NULL_PATH}; XrActionsSyncInfo sync{XR_TYPE_ACTIONS_SYNC_INFO}; sync.countActiveActionSets=1; sync.activeActionSets=&active; check(xrSyncActions(session,&sync),"sync");
                XrActionStateGetInfo gi{XR_TYPE_ACTION_STATE_GET_INFO}; gi.action=pose; XrActionStatePose ps{XR_TYPE_ACTION_STATE_POSE}; check(xrGetActionStatePose(session,&gi,&ps),"pose state");
                gi.action=click; XrActionStateBoolean cs{XR_TYPE_ACTION_STATE_BOOLEAN}; check(xrGetActionStateBoolean(session,&gi,&cs),"click state");
                XrSpaceLocation loc{XR_TYPE_SPACE_LOCATION}; check(xrLocateSpace(aim,local,fs.predictedDisplayTime,&loc),"locate aim");
                const auto flags=XR_SPACE_LOCATION_POSITION_VALID_BIT|XR_SPACE_LOCATION_ORIENTATION_VALID_BIT; bool valid=ps.isActive && (loc.locationFlags&flags)==flags;
                auto q=loc.pose.orientation; auto p=loc.pose.position;
                env->CallVoidMethod(activity,input,p.x,p.y,p.z,-2.f*(q.x*q.z+q.w*q.y),-2.f*(q.y*q.z-q.w*q.x),-1.f+2.f*(q.x*q.x+q.y*q.y),jboolean(valid),jboolean(cs.isActive&&cs.currentState)); javaCheck();
            } else { env->CallVoidMethod(activity,input,0.f,0.f,0.f,0.f,0.f,-1.f,jboolean(false),jboolean(false)); javaCheck(); }
            if(fs.shouldRender) {
                uint32_t index=0; XrSwapchainImageAcquireInfo ai{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO}; check(xrAcquireSwapchainImage(swapchain,&ai,&index),"acquire");
                XrSwapchainImageWaitInfo wi{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO}; wi.timeout=XR_INFINITE_DURATION; check(xrWaitSwapchainImage(swapchain,&wi),"wait image");
                ready=env->CallBooleanMethod(activity,draw,jint(images[index].image));
                XrSwapchainImageReleaseInfo ri{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO}; check(xrReleaseSwapchainImage(swapchain,&ri),"release image"); javaCheck();
                quad.space=local; quad.eyeVisibility=XR_EYE_VISIBILITY_BOTH; quad.layerFlags=XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
                quad.subImage.swapchain=swapchain; quad.subImage.imageRect.extent={1200,800}; quad.pose.orientation.w=1; quad.pose.position={0,0,-2}; quad.size={1.5f,1.f};
            }
        } catch(...) { XrFrameEndInfo end{XR_TYPE_FRAME_END_INFO}; end.displayTime=fs.predictedDisplayTime; end.environmentBlendMode=blend; xrEndFrame(session,&end); throw; }
        const XrCompositionLayerBaseHeader* layer=reinterpret_cast<XrCompositionLayerBaseHeader*>(&quad);
        XrFrameEndInfo end{XR_TYPE_FRAME_END_INFO}; end.displayTime=fs.predictedDisplayTime; end.environmentBlendMode=blend; end.layerCount=ready?1:0; end.layers=ready?&layer:nullptr; check(xrEndFrame(session,&end),"end frame");
    }
    void run() {
        vm->AttachCurrentThread(&env,nullptr);
        try { setup(); while(!stop) {
            XrEventDataBuffer event{XR_TYPE_EVENT_DATA_BUFFER}; XrResult r;
            while((r=xrPollEvent(instance,&event))==XR_SUCCESS) {
                if(event.type==XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
                    state=reinterpret_cast<XrEventDataSessionStateChanged*>(&event)->state;
                    if(state==XR_SESSION_STATE_READY) { XrSessionBeginInfo b{XR_TYPE_SESSION_BEGIN_INFO}; b.primaryViewConfigurationType=XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO; check(xrBeginSession(session,&b),"begin session"); running=true; }
                    if(state==XR_SESSION_STATE_STOPPING) { running=false; check(xrEndSession(session),"end session"); }
                    if(state==XR_SESSION_STATE_EXITING||state==XR_SESSION_STATE_LOSS_PENDING) stop=true;
                } else if(event.type==XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING) stop=true;
                event={XR_TYPE_EVENT_DATA_BUFFER};
            }
            check(r,"poll event"); if(stop) break;
            if(running) frame(); else std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }} catch(const std::exception& e) { report(e.what()); }
        auto cls=env->GetObjectClass(activity); auto close=env->GetMethodID(cls,"closeProducer","()V");
        if(close) env->CallVoidMethod(activity,close); env->DeleteLocalRef(cls);
        if(env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        if(swapchain) xrDestroySwapchain(swapchain); if(aim) xrDestroySpace(aim); if(local) xrDestroySpace(local); if(session) xrDestroySession(session); if(actions) xrDestroyActionSet(actions); if(instance) xrDestroyInstance(instance);
        if(display!=EGL_NO_DISPLAY) { eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT); if(surface!=EGL_NO_SURFACE) eglDestroySurface(display,surface); if(context!=EGL_NO_CONTEXT) eglDestroyContext(display,context); eglTerminate(display); }
        vm->DetachCurrentThread();
    }
};
}
extern "C" JNIEXPORT jlong JNICALL Java_org_spatialopenxr_sample_XrActivity_startNative(JNIEnv* e,jobject a) { auto h=new Host(e,a); h->thread=std::thread([h]{h->run();}); return reinterpret_cast<jlong>(h); }
extern "C" JNIEXPORT void JNICALL Java_org_spatialopenxr_sample_XrActivity_resumeNative(JNIEnv*,jobject,jlong p,jboolean resumed) { if(p) reinterpret_cast<Host*>(p)->resumed=resumed; }
extern "C" JNIEXPORT void JNICALL Java_org_spatialopenxr_sample_XrActivity_stopNative(JNIEnv*,jobject,jlong p) { delete reinterpret_cast<Host*>(p); }
