#include "vulkan_surface_requests.h"
#include "controller_layer_state.h"
#include <algorithm>
#include <cmath>
#include <mutex>
#include <unordered_map>

namespace moonlight::xr {
namespace {
std::mutex mutex;
uint64_t epoch=0,active=0;
uint32_t capacity=0;
std::unordered_map<std::string,int64_t> desired;
std::vector<VulkanSurfaceRequest> queue;
std::optional<ControllerRayFrame> openingRay;
std::optional<std::array<ControllerRayFrame,2>> testInput;
std::array<VulkanIconPlacement,5> icons{};
bool safeId(const std::string& id) {
    return !id.empty() && id.size()<=80 && std::all_of(id.begin(),id.end(),[](char c) {
        return (c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9') || c=='-' || c=='_';
    });
}
std::string string(JNIEnv* env,jstring value) {
    if(!value) return {};
    const char* chars=env->GetStringUTFChars(value,nullptr);
    std::string result=chars?chars:"";
    if(chars) env->ReleaseStringUTFChars(value,chars);
    return result;
}
}
uint64_t beginVulkanUiSession(uint32_t limit) {
    std::lock_guard<std::mutex> lock(mutex);
    active=++epoch; capacity=limit; desired.clear(); queue.clear(); openingRay.reset(); testInput.reset(); icons={}; return active;
}
std::array<VulkanIconPlacement,5> vulkanIconPlacements(uint64_t session) {
    std::lock_guard<std::mutex> lock(mutex);
    return active==session?icons:std::array<VulkanIconPlacement,5>{};
}
bool setVulkanIcon(JNIEnv* env,uint64_t session,int index,jfloatArray values) {
    if(index<0 || index>=5) return false;
    VulkanIconPlacement icon;
    if(values) {
        if(env->GetArrayLength(values)!=8) return false;
        float data[8]{}; env->GetFloatArrayRegion(values,0,8,data);
        for(float value:data) if(!std::isfinite(value)) return false;
        const float norm=data[3]*data[3]+data[4]*data[4]+data[5]*data[5]+data[6]*data[6];
        if(std::abs(norm-1)>.01f || data[7]<=0 || data[7]>2) return false;
        icon.visible=true; icon.local={{data[3],data[4],data[5],data[6]},{data[0],data[1],data[2]}}; icon.size=data[7];
    }
    std::lock_guard<std::mutex> lock(mutex);
    if(!active || active!=session) return false;
    icons[index]=icon; return true;
}
void endVulkanUiSession(uint64_t session) {
    std::lock_guard<std::mutex> lock(mutex);
    if(active==session) { active=0; desired.clear(); queue.clear(); openingRay.reset(); testInput.reset(); }
}
std::optional<std::array<ControllerRayFrame,2>> vulkanTestInput(uint64_t session) {
    std::lock_guard<std::mutex> lock(mutex);
    return active==session?testInput:std::nullopt;
}
bool setVulkanTestInput(JNIEnv* env,uint64_t session,jfloatArray values) {
#if defined(NDEBUG)
    return false;
#else
    std::optional<std::array<ControllerRayFrame,2>> result;
    if(values) {
        if(env->GetArrayLength(values)!=24) return false;
        float data[24]{}; env->GetFloatArrayRegion(values,0,24,data);
        for(float v:data) if(!std::isfinite(v)) return false;
        result.emplace();
        for(int hand=0;hand<2;++hand) {
            const int i=hand*12; auto& ray=(*result)[hand];
            ray.origin={data[i],data[i+1],data[i+2]}; ray.direction={data[i+3],data[i+4],data[i+5]};
            ray.pressed=data[i+6]>0; ray.scroll=data[i+7]; ray.valid=data[i+8]>0; ray.grip=data[i+9]>0;
            ray.stickX=std::clamp(data[i+10],-1.f,1.f); ray.stickY=std::clamp(data[i+11],-1.f,1.f);
            const float norm=ray.direction.x*ray.direction.x+ray.direction.y*ray.direction.y+ray.direction.z*ray.direction.z;
            if(ray.valid && std::abs(norm-1.f)>.01f) return false;
        }
    }
    std::lock_guard<std::mutex> lock(mutex);
    if(!active || active!=session) return false;
    testInput=result; return true;
#endif
}
std::vector<VulkanSurfaceRequest> takeVulkanSurfaceRequests(uint64_t session) {
    std::lock_guard<std::mutex> lock(mutex);
    std::vector<VulkanSurfaceRequest> result;
    if(active==session) result.swap(queue);
    return result;
}
std::optional<ControllerRayFrame> takeVulkanMenuAnchor(uint64_t session) {
    std::lock_guard<std::mutex> lock(mutex);
    auto result=active==session?openingRay:std::nullopt;
    if(active==session) openingRay.reset();
    return result;
}
void anchorVulkanMenu(JNIEnv* env,uint64_t session,jfloatArray values) {
    if(!values || env->GetArrayLength(values)!=6) return;
    float data[6]{}; env->GetFloatArrayRegion(values,0,6,data);
    for(float value:data) if(!std::isfinite(value)) return;
    const float length=std::sqrt(data[3]*data[3]+data[4]*data[4]+data[5]*data[5]);
    if(length<1e-6f) return;
    ControllerRayFrame ray; ray.valid=true; ray.origin={data[0],data[1],data[2]};
    ray.direction={data[3]/length,data[4]/length,data[5]/length};
    std::lock_guard<std::mutex> lock(mutex);
    if(active && active==session) openingRay=ray;
}
bool requestVulkanSurface(JNIEnv* env,uint64_t session,jstring id,int64_t generation,jstring parent,
                         int width,int height,jfloatArray values,bool faceHead,bool modal,bool focusable) {
    VulkanSurfaceRequest request; request.config.id=string(env,id); request.parent=string(env,parent);
    if(!safeId(request.config.id) || request.config.id=="main" || generation<=0 || width<=0 || height<=0 ||
       width>4096 || height>4096 || !values || env->GetArrayLength(values)!=9) return false;
    if(request.parent!="main" && request.parent!="free-menu" && request.parent!="category-menu" &&
       request.parent!="extension" && request.parent!="popup" && !controllerLayerParent(request.parent)) return false;
    float data[9]{}; env->GetFloatArrayRegion(values,0,9,data);
    for(float value:data) if(!std::isfinite(value)) return false;
    const float norm=data[5]*data[5]+data[6]*data[6]+data[7]*data[7]+data[8]*data[8];
    if(data[0]<=0 || data[1]<=0 || data[0]>20 || data[1]>20 || std::abs(norm-1)>0.01f) return false;
    request.config.generation=generation; request.config.width=width; request.config.height=height;
    request.config.widthMeters=data[0]; request.config.heightMeters=data[1];
    request.local.position={data[2],data[3],data[4]}; request.local.orientation={data[5],data[6],data[7],data[8]};
    request.faceHead=faceHead; request.modal=modal; request.focusable=focusable;
    std::lock_guard<std::mutex> lock(mutex);
    if(!active || active!=session || (desired.count(request.config.id)==0 && desired.size()>=capacity)) return false;
    auto found=desired.find(request.config.id);
    if(found!=desired.end() && generation<=found->second) return false;
    desired[request.config.id]=generation; queue.push_back(std::move(request)); return true;
}
extern "C" JNIEXPORT void JNICALL Java_com_moonlightxr_nativeui_NativeXrActivity_nativeAnchorVulkanMenu(
        JNIEnv* env,jobject,jlong session,jfloatArray values) {
    moonlight::xr::anchorVulkanMenu(env,session,values);
}
void releaseVulkanSurface(JNIEnv* env,uint64_t session,jstring id,int64_t generation) {
    const auto name=string(env,id);
    std::lock_guard<std::mutex> lock(mutex);
    if(!active || active!=session) return;
    const auto found=desired.find(name);
    if(found==desired.end() || found->second!=generation) return;
    desired.erase(found); VulkanSurfaceRequest request; request.config.id=name; request.config.generation=generation;
    request.remove=true; queue.push_back(std::move(request));
}
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_moonlightxr_nativeui_NativeXrActivity_nativeRequestVulkanSurface(
        JNIEnv* env,jobject,jlong session,jstring id,jlong generation,jstring parent,jint width,jint height,
        jfloatArray values,jboolean face,jboolean modal,jboolean focusable) {
    return moonlight::xr::requestVulkanSurface(env,session,id,generation,parent,width,height,values,face,modal,focusable);
}
extern "C" JNIEXPORT void JNICALL Java_com_moonlightxr_nativeui_NativeXrActivity_nativeReleaseVulkanSurface(
        JNIEnv* env,jobject,jlong session,jstring id,jlong generation) {
    moonlight::xr::releaseVulkanSurface(env,session,id,generation);
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_moonlightxr_nativeui_NativeXrActivity_nativeSetVulkanTestInput(
        JNIEnv* env,jobject,jlong session,jfloatArray values) {
    return moonlight::xr::setVulkanTestInput(env,session,values);
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_moonlightxr_nativeui_NativeXrActivity_nativeSetVulkanIcon(
        JNIEnv* env,jobject,jlong session,jint index,jfloatArray values) {
    return moonlight::xr::setVulkanIcon(env,session,index,values);
}
