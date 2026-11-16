#pragma once
#include "vulkan_compose_surface.h"
#include "xr_menu_input.h"
#include "controller_layer_state.h"
#include <functional>
#include <vector>
#include <ostream>

namespace moonlight::xr {
class VulkanUiLayers {
public:
    using Reporter=std::function<void(const std::string&)>;
    VulkanUiLayers(JNIEnv*,jobject,VkDevice,VkPhysicalDevice,VkQueue,uint32_t,XrSession,uint32_t,bool,Reporter,uint32_t projectionLayers=1);
    ~VulkanUiLayers();
    void prepare(XrSpace,const XrPosef& main,const XrPosef& head,
                 const std::array<ControllerRayFrame,2>& rays,float seconds,const ControllerLayerState& controllers);
    const std::vector<XrCompositionLayerQuad>& layers() const;
    void presentedFrame();
    std::array<bool,2> stabilizeInput(std::array<ControllerRayFrame,2>&,double time);
    int iconFrames(moonlight::IconFrame (&icons)[5]) const;
    bool hasBackground() const;
    bool adjusting() const;
    void recenterMain();
    void setVideoAspect(float aspect);
    bool controllerOverlay(size_t index) const;
    bool submitted(const std::string& id) const;
    void writeHudDiagnostics(std::ostream&) const;
private:
    struct State;
    std::unique_ptr<State> state;
};
}
