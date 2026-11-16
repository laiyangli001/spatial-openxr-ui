#pragma once
#include <bluevk/BlueVK.h>
#include <memory>
#include <vector>
namespace moonlight::xr {
struct VulkanQuadSource {
    bool video=false;
    VkFormat format=VK_FORMAT_R8G8B8A8_UNORM;
    VkSamplerYcbcrConversion conversion=VK_NULL_HANDLE;
    VkFilter filter=VK_FILTER_LINEAR;
};
// Converts Canvas gamma-premultiplied pixels to OpenXR linear-premultiplied sRGB.
class VulkanUiColorPass {
public:
    VulkanUiColorPass(VkDevice device,VkPhysicalDevice physicalDevice,VkQueue queue,uint32_t queueFamily,uint32_t width,uint32_t height,const std::vector<VkImage>& targets,VulkanQuadSource source={},uint32_t arraySize=1);
    /** Returns false when the frame is not on the fixed 320x180 depth grid. */
    bool setDepth(const float* data,uint32_t width,uint32_t height,float disparity,float convergence);
    /** Per-eye displacement fields, two floats per pixel (left,right) on the same grid as setDepth. */
    void setDepthOffsets(const float* offsets,uint32_t width,uint32_t height);
    void uploadDepth(VkCommandBuffer command);
    ~VulkanUiColorPass();
    void draw(VkCommandBuffer command,VkImage source,uint32_t target,const float* videoParameters=nullptr,const float* colorCheckParameters=nullptr);
private:
    struct State;
    std::unique_ptr<State> state;
};
}
