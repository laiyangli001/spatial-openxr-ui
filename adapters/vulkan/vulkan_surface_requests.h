#pragma once
#include "vulkan_compose_surface.h"
#include <vector>
#include <optional>
#include "xr_menu_input.h"

namespace moonlight::xr {
struct VulkanSurfaceRequest {
    VulkanSurfaceConfig config;
    std::string parent="main";
    XrPosef local{{0,0,0,1},{0,0,0}};
    bool faceHead=false,modal=false,focusable=true,remove=false;
};
struct VulkanIconPlacement { bool visible=false; XrPosef local{{0,0,0,1},{0,0,0}}; float size=.22f; };
std::array<VulkanIconPlacement,5> vulkanIconPlacements(uint64_t session);
uint64_t beginVulkanUiSession(uint32_t capacity);
void endVulkanUiSession(uint64_t session);
std::vector<VulkanSurfaceRequest> takeVulkanSurfaceRequests(uint64_t session);
std::optional<ControllerRayFrame> takeVulkanMenuAnchor(uint64_t session);
std::optional<std::array<ControllerRayFrame,2>> vulkanTestInput(uint64_t session);
}
