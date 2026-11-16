#pragma once
#include <bluevk/BlueVK.h>
#include <jni.h>
#include <openxr/openxr.h>
#include <memory>
#include <string>

namespace moonlight::xr {
struct VulkanSurfaceConfig {
    std::string id="main";
    int64_t generation=0;
    uint32_t width=1600,height=900;
    float widthMeters=2.4f,heightMeters=1.35f;
    bool staticImage=false;
};
// One Compose producer -> AHardwareBuffer -> Vulkan-owned XR Quad texture.
// The XR host must serialize calls with Filament submissions on the shared queue.
class VulkanComposeSurface {
public:
    VulkanComposeSurface(JNIEnv*,jobject,VkDevice,VkPhysicalDevice,VkQueue,uint32_t,XrSession,bool captureForQa,
                         VulkanSurfaceConfig config={});
    ~VulkanComposeSurface();
    VulkanComposeSurface(const VulkanComposeSurface&)=delete;
    VulkanComposeSurface& operator=(const VulkanComposeSurface&)=delete;
    bool update();
    bool ready() const;
    uint64_t updates() const;
    XrCompositionLayerQuad layer(XrSpace,const XrPosef&) const;
private:
    struct State;
    std::unique_ptr<State> state;
};
}
