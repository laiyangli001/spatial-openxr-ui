#include "vulkan_ui_color_pass.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>
#include <unordered_map>
#include <string>
#include <functional>
#include <cstring>
namespace moonlight::xr {
using namespace bluevk;
namespace {
const uint32_t vertexCode[]=
#include "ui_color.vert.inc"
;
const uint32_t fragmentCode[]=
#include "ui_color.frag.inc"
;
const uint32_t videoCode[]=
#include "video_color.frag.inc"
;
void checked(VkResult result) {
    if(result!=VK_SUCCESS) throw std::runtime_error("Vulkan UI color pass: "+std::to_string(result));
}
}
struct VulkanUiColorPass::State {
    VkDevice device;
    VkPhysicalDevice physicalDevice=VK_NULL_HANDLE;
    VkQueue queue;
    uint32_t queueFamily;
    uint32_t width,height,arraySize=1;
    VkRenderPass pass=VK_NULL_HANDLE;
    VkDescriptorSetLayout setLayout=VK_NULL_HANDLE;
    VkDescriptorPool pool=VK_NULL_HANDLE;
    VkPipelineLayout layout=VK_NULL_HANDLE;
    VkPipeline pipeline=VK_NULL_HANDLE;
    VkSampler sampler=VK_NULL_HANDLE;
    VulkanQuadSource source;
    std::vector<VkImageView> targets;
    std::vector<VkFramebuffer> frames;
    struct Source { VkImageView view; VkDescriptorSet set; };
    std::unordered_map<VkImage,Source> sources;
    VkImage depthImage=VK_NULL_HANDLE; VkDeviceMemory depthMemory=VK_NULL_HANDLE;
    VkImageView depthView=VK_NULL_HANDLE; VkSampler depthSampler=VK_NULL_HANDLE;
    VkBuffer depthStaging=VK_NULL_HANDLE; VkDeviceMemory depthStagingMemory=VK_NULL_HANDLE;
    void* depthMapped=nullptr; size_t depthCapacity=0;
    uint32_t depthW=0,depthH=0;
    bool depthDirty=false;
    VkImageLayout depthLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    State(VkDevice d,VkPhysicalDevice pd,VkQueue q,uint32_t qf,uint32_t w,uint32_t h,uint32_t n):device(d),physicalDevice(pd),queue(q),queueFamily(qf),width(w),height(h),arraySize(n) {}
    uint32_t memoryType(uint32_t bits,VkMemoryPropertyFlags flags) const {
        VkPhysicalDeviceMemoryProperties props{};
        vkGetPhysicalDeviceMemoryProperties(physicalDevice,&props);
        for(uint32_t i=0;i<props.memoryTypeCount;++i)
            if((bits&(1u<<i)) && (props.memoryTypes[i].propertyFlags&flags)==flags) return i;
        throw std::runtime_error("No compatible Vulkan memory type");
    }
    ~State() {
        if(pipeline) vkDestroyPipeline(device,pipeline,nullptr);
        if(layout) vkDestroyPipelineLayout(device,layout,nullptr);
        if(pool) vkDestroyDescriptorPool(device,pool,nullptr);
        for(auto& item:sources) vkDestroyImageView(device,item.second.view,nullptr);
        for(auto f:frames) vkDestroyFramebuffer(device,f,nullptr);
        for(auto v:targets) vkDestroyImageView(device,v,nullptr);
        if(sampler) vkDestroySampler(device,sampler,nullptr);
        if(depthSampler) vkDestroySampler(device,depthSampler,nullptr);
        if(depthView) vkDestroyImageView(device,depthView,nullptr);
        if(depthImage) vkDestroyImage(device,depthImage,nullptr);
        if(depthMemory) vkFreeMemory(device,depthMemory,nullptr);
        if(depthStaging) vkDestroyBuffer(device,depthStaging,nullptr);
        if(depthStagingMemory) vkFreeMemory(device,depthStagingMemory,nullptr);
        if(setLayout) vkDestroyDescriptorSetLayout(device,setLayout,nullptr);
        if(pass) vkDestroyRenderPass(device,pass,nullptr);
    }
    VkImageView view(VkImage image,VkFormat format,VkSamplerYcbcrConversion conversion=VK_NULL_HANDLE,uint32_t baseLayer=0) {
        VkSamplerYcbcrConversionInfo yuv{VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_INFO};yuv.conversion=conversion;
        VkImageViewCreateInfo info{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        if(conversion) info.pNext=&yuv;
        info.image=image;info.format=format;
        info.viewType=arraySize>1?VK_IMAGE_VIEW_TYPE_2D_ARRAY:VK_IMAGE_VIEW_TYPE_2D;
        info.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,baseLayer,1};
        VkImageView result;checked(vkCreateImageView(device,&info,nullptr,&result));return result;
    }
    void oneShot(const std::function<void(VkCommandBuffer)>& body) {
        VkCommandPoolCreateInfo cp{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        cp.flags=VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;cp.queueFamilyIndex=queueFamily;
        VkCommandPool commandPool;checked(vkCreateCommandPool(device,&cp,nullptr,&commandPool));
        VkCommandBufferAllocateInfo alloc{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        alloc.commandPool=commandPool;alloc.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;alloc.commandBufferCount=1;
        VkCommandBuffer command;checked(vkAllocateCommandBuffers(device,&alloc,&command));
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        begin.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        checked(vkBeginCommandBuffer(command,&begin));
        body(command);
        checked(vkEndCommandBuffer(command));
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};submit.commandBufferCount=1;submit.pCommandBuffers=&command;
        checked(vkQueueSubmit(queue,1,&submit,VK_NULL_HANDLE));
        vkQueueWaitIdle(queue);
        vkFreeCommandBuffers(device,commandPool,1,&command);
        vkDestroyCommandPool(device,commandPool,nullptr);
    }
    void createDepthImage(uint32_t w,uint32_t h) {
        if(depthImage && w==depthW && h==depthH) return;
        vkQueueWaitIdle(queue);
        if(depthView) { vkDestroyImageView(device,depthView,nullptr);depthView=VK_NULL_HANDLE; }
        if(depthImage) { vkDestroyImage(device,depthImage,nullptr);depthImage=VK_NULL_HANDLE; }
        if(depthMemory) { vkFreeMemory(device,depthMemory,nullptr);depthMemory=VK_NULL_HANDLE; }
        // RGBA8 keeps the depth plus the two eye fields in one UNSIGNED NORMALIZED
        // texture: R = normalized depth, G = left-field, B = right-field. RGBA8 is
        // the most portable sampled format and a quarter of the 32-bit traffic.
        VkImageCreateInfo info{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
        info.imageType=VK_IMAGE_TYPE_2D;info.format=VK_FORMAT_R8G8B8A8_UNORM;
        info.extent={w,h,1};info.mipLevels=1;info.arrayLayers=1;
        info.samples=VK_SAMPLE_COUNT_1_BIT;info.tiling=VK_IMAGE_TILING_OPTIMAL;
        info.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT;
        info.sharingMode=VK_SHARING_MODE_EXCLUSIVE;info.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        checked(vkCreateImage(device,&info,nullptr,&depthImage));
        VkMemoryRequirements req{};vkGetImageMemoryRequirements(device,depthImage,&req);
        VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        alloc.allocationSize=req.size;
        alloc.memoryTypeIndex=memoryType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        checked(vkAllocateMemory(device,&alloc,nullptr,&depthMemory));
        checked(vkBindImageMemory(device,depthImage,depthMemory,0));
        depthW=w;depthH=h;
        VkImageViewCreateInfo viewInfo{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        viewInfo.image=depthImage;viewInfo.viewType=VK_IMAGE_VIEW_TYPE_2D;viewInfo.format=VK_FORMAT_R8G8B8A8_UNORM;
        viewInfo.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        checked(vkCreateImageView(device,&viewInfo,nullptr,&depthView));
        oneShot([&](VkCommandBuffer cmd) {
            VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            barrier.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED;barrier.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            barrier.srcQueueFamilyIndex=barrier.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            barrier.image=depthImage;barrier.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
            barrier.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
            vkCmdPipelineBarrier(cmd,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,0,nullptr,0,nullptr,1,&barrier);
            VkClearColorValue clear{};clear.float32[0]=0.f;
            VkImageSubresourceRange range{VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
            vkCmdClearColorImage(cmd,depthImage,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,&clear,1,&range);
            barrier.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            barrier.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            barrier.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
            barrier.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
            vkCmdPipelineBarrier(cmd,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                0,0,nullptr,0,nullptr,1,&barrier);
        });
    }
    void updateDepthBinding() {
        if(!depthView) return;
        for(auto& [image,entry]:sources) {
            VkDescriptorImageInfo imageInfo{depthSampler,depthView,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
            VkWriteDescriptorSet write{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
            write.dstSet=entry.set;write.dstBinding=1;write.descriptorCount=1;
            write.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;write.pImageInfo=&imageInfo;
            vkUpdateDescriptorSets(device,1,&write,0,nullptr);
        }
    }
};
VulkanUiColorPass::VulkanUiColorPass(VkDevice device,VkPhysicalDevice physicalDevice,VkQueue queue,uint32_t queueFamily,uint32_t width,uint32_t height,const std::vector<VkImage>& targets,VulkanQuadSource source,uint32_t arraySize)
        :state(std::make_unique<State>(device,physicalDevice,queue,queueFamily,width,height,arraySize)) {
    auto& s=*state;
    s.source=source;
    VkAttachmentDescription attachment{};attachment.format=VK_FORMAT_R8G8B8A8_SRGB;
    attachment.samples=VK_SAMPLE_COUNT_1_BIT;attachment.loadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachment.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    attachment.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;attachment.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachment.initialLayout=attachment.finalLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{};sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;sub.colorAttachmentCount=1;sub.pColorAttachments=&ref;
    VkRenderPassCreateInfo rp{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};rp.attachmentCount=1;rp.pAttachments=&attachment;rp.subpassCount=1;rp.pSubpasses=&sub;
    checked(vkCreateRenderPass(device,&rp,nullptr,&s.pass));
    for(auto image:targets) {
        for(uint32_t layer=0;layer<arraySize;++layer) {
            s.targets.push_back(s.view(image,VK_FORMAT_R8G8B8A8_SRGB,VK_NULL_HANDLE,layer));
            VkFramebufferCreateInfo fb{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};fb.renderPass=s.pass;
            fb.attachmentCount=1;fb.pAttachments=&s.targets.back();fb.width=width;fb.height=height;fb.layers=1;
            VkFramebuffer frame;checked(vkCreateFramebuffer(device,&fb,nullptr,&frame));s.frames.push_back(frame);
        }
    }
    VkSamplerYcbcrConversionInfo yuv{VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_INFO};yuv.conversion=source.conversion;
    VkSamplerCreateInfo sampler{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};sampler.magFilter=sampler.minFilter=source.filter;
    sampler.mipmapMode=VK_SAMPLER_MIPMAP_MODE_NEAREST;
    sampler.addressModeU=sampler.addressModeV=sampler.addressModeW=VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    if(source.conversion) sampler.pNext=&yuv;
    checked(vkCreateSampler(device,&sampler,nullptr,&s.sampler));
    VkSamplerCreateInfo depthSampler{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
    depthSampler.magFilter=depthSampler.minFilter=VK_FILTER_LINEAR;
    depthSampler.mipmapMode=VK_SAMPLER_MIPMAP_MODE_NEAREST;
    depthSampler.addressModeU=depthSampler.addressModeV=depthSampler.addressModeW=VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    checked(vkCreateSampler(device,&depthSampler,nullptr,&s.depthSampler));
    VkDescriptorSetLayoutBinding bindings[2]{};
    bindings[0]={0,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,1,VK_SHADER_STAGE_FRAGMENT_BIT,source.conversion?&s.sampler:nullptr};
    bindings[1]={1,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,1,VK_SHADER_STAGE_FRAGMENT_BIT,nullptr};
    VkDescriptorSetLayoutCreateInfo ds{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};ds.bindingCount=2;ds.pBindings=bindings;
    checked(vkCreateDescriptorSetLayout(device,&ds,nullptr,&s.setLayout));
    VkDescriptorPoolSize poolSize{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,256};
    VkDescriptorPoolCreateInfo dp{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};dp.maxSets=16;dp.poolSizeCount=1;dp.pPoolSizes=&poolSize;
    checked(vkCreateDescriptorPool(device,&dp,nullptr,&s.pool));
    VkPipelineLayoutCreateInfo pl{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};pl.setLayoutCount=1;pl.pSetLayouts=&s.setLayout;
    VkPushConstantRange push{VK_SHADER_STAGE_FRAGMENT_BIT,0,96}; // crop..ai3d, six vec4
    pl.pushConstantRangeCount=1;pl.pPushConstantRanges=&push;
    checked(vkCreatePipelineLayout(device,&pl,nullptr,&s.layout));
    VkShaderModule modules[2]{};
    try {
        VkShaderModuleCreateInfo shader{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};shader.codeSize=sizeof(vertexCode);shader.pCode=vertexCode;
        checked(vkCreateShaderModule(device,&shader,nullptr,&modules[0]));
        shader.codeSize=source.video?sizeof(videoCode):sizeof(fragmentCode);shader.pCode=source.video?videoCode:fragmentCode;
        checked(vkCreateShaderModule(device,&shader,nullptr,&modules[1]));
        VkPipelineShaderStageCreateInfo stages[2]{};
        for(int i=0;i<2;++i) { stages[i].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
            stages[i].stage=i==0?VK_SHADER_STAGE_VERTEX_BIT:VK_SHADER_STAGE_FRAGMENT_BIT;stages[i].module=modules[i];stages[i].pName="main"; }
        VkPipelineVertexInputStateCreateInfo vi{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        VkPipelineInputAssemblyStateCreateInfo ia{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkViewport viewport{0,0,float(width),float(height),0,1};VkRect2D scissor{{0,0},{width,height}};
        VkPipelineViewportStateCreateInfo vp{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};vp.viewportCount=vp.scissorCount=1;vp.pViewports=&viewport;vp.pScissors=&scissor;
        VkPipelineRasterizationStateCreateInfo rs{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};rs.polygonMode=VK_POLYGON_MODE_FILL;rs.cullMode=VK_CULL_MODE_NONE;rs.lineWidth=1;
        VkPipelineMultisampleStateCreateInfo ms{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
        VkPipelineColorBlendAttachmentState blend{};blend.colorWriteMask=VK_COLOR_COMPONENT_R_BIT|VK_COLOR_COMPONENT_G_BIT|VK_COLOR_COMPONENT_B_BIT|VK_COLOR_COMPONENT_A_BIT;
        VkPipelineColorBlendStateCreateInfo cb{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};cb.attachmentCount=1;cb.pAttachments=&blend;
        VkGraphicsPipelineCreateInfo p{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};p.stageCount=2;p.pStages=stages;
        p.pVertexInputState=&vi;p.pInputAssemblyState=&ia;p.pViewportState=&vp;p.pRasterizationState=&rs;p.pMultisampleState=&ms;p.pColorBlendState=&cb;p.layout=s.layout;p.renderPass=s.pass;
        checked(vkCreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&p,nullptr,&s.pipeline));
    } catch(...) { for(auto module:modules) if(module) vkDestroyShaderModule(device,module,nullptr);throw; }
    for(auto module:modules) vkDestroyShaderModule(device,module,nullptr);
    // Fixed 320x180 depth for the AI 3D models; zero-initialized before the first frame.
    s.createDepthImage(320,180);
    s.depthLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    {
        VkBufferCreateInfo info{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        info.size=size_t(320)*size_t(180)*4;
        info.usage=VK_BUFFER_USAGE_TRANSFER_SRC_BIT;info.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
        checked(vkCreateBuffer(device,&info,nullptr,&s.depthStaging));
        VkMemoryRequirements req{};vkGetBufferMemoryRequirements(device,s.depthStaging,&req);
        VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};alloc.allocationSize=req.size;
        alloc.memoryTypeIndex=s.memoryType(req.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        checked(vkAllocateMemory(device,&alloc,nullptr,&s.depthStagingMemory));
        checked(vkBindBufferMemory(device,s.depthStaging,s.depthStagingMemory,0));
        checked(vkMapMemory(device,s.depthStagingMemory,0,VK_WHOLE_SIZE,0,&s.depthMapped));
        std::memset(s.depthMapped,0,size_t(320)*size_t(180)*4);
        s.depthCapacity=size_t(320)*size_t(180)*4;
    }
}
VulkanUiColorPass::~VulkanUiColorPass()=default;
bool VulkanUiColorPass::setDepth(const float* data,uint32_t width,uint32_t height,float,float) {
    if(!data || width==0 || height==0) return false;
    auto& s=*state;
    if(width!=s.depthW || height!=s.depthH) return false; // Depth models are fixed 320x180 for now.
    auto* staging=static_cast<uint8_t*>(s.depthMapped);
    const size_t count=size_t(width)*size_t(height);
    for(size_t i=0;i<count;++i) {
        const float depth=std::isfinite(data[i])?std::clamp(data[i],0.f,1.f):0.f;
        staging[i*4+0]=static_cast<uint8_t>(depth*255.f+0.5f);
        staging[i*4+1]=0;staging[i*4+2]=0;staging[i*4+3]=255;
    }
    s.depthDirty=true;
    return true;
}
void VulkanUiColorPass::setDepthOffsets(const float* offsets,uint32_t width,uint32_t height) {
    if(!offsets || width==0 || height==0) return;
    auto& s=*state;
    if(width!=s.depthW || height!=s.depthH) return;
    auto* staging=static_cast<uint8_t*>(s.depthMapped);
    const size_t count=size_t(width)*size_t(height);
    for(size_t i=0;i<count;++i) {
        const float left=std::isfinite(offsets[i*2+0])?std::clamp(offsets[i*2+0],0.f,1.f):0.5f;
        const float right=std::isfinite(offsets[i*2+1])?std::clamp(offsets[i*2+1],0.f,1.f):0.5f;
        staging[i*4+1]=static_cast<uint8_t>(left*255.f+0.5f);
        staging[i*4+2]=static_cast<uint8_t>(right*255.f+0.5f);
    }
    s.depthDirty=true;
}
void VulkanUiColorPass::uploadDepth(VkCommandBuffer command) {
    auto& s=*state;
    if(!s.depthDirty || !s.depthImage) return;
    VkBufferMemoryBarrier host{VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER};
    host.srcAccessMask=VK_ACCESS_HOST_WRITE_BIT;host.dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;
    host.buffer=s.depthStaging;host.offset=0;host.size=VK_WHOLE_SIZE;
    host.srcQueueFamilyIndex=host.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_HOST_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,
        0,0,nullptr,1,&host,0,nullptr);
    VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    barrier.oldLayout=s.depthLayout;barrier.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.srcQueueFamilyIndex=barrier.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    barrier.image=s.depthImage;barrier.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    barrier.srcAccessMask=VK_ACCESS_SHADER_READ_BIT;barrier.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
    vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,
        0,0,nullptr,0,nullptr,1,&barrier);
    VkBufferImageCopy copy{};copy.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
    copy.imageExtent={s.depthW,s.depthH,1};
    vkCmdCopyBufferToImage(command,s.depthStaging,s.depthImage,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,1,&copy);
    barrier.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    barrier.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;barrier.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
    vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
        0,0,nullptr,0,nullptr,1,&barrier);
    s.depthLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    s.depthDirty=false;
}void VulkanUiColorPass::draw(VkCommandBuffer command,VkImage source,uint32_t target,const float* videoParameters,const float* colorCheckParameters) {
    auto& s=*state;
    if(s.source.video && !videoParameters) throw std::runtime_error("Missing video sampling metadata");
    auto found=s.sources.find(source);
    if(found==s.sources.end()) {
        auto view=s.view(source,s.source.format,s.source.conversion);
        VkDescriptorSetAllocateInfo alloc{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};alloc.descriptorPool=s.pool;alloc.descriptorSetCount=1;alloc.pSetLayouts=&s.setLayout;
        VkDescriptorSet set;
        try { checked(vkAllocateDescriptorSets(s.device,&alloc,&set)); }
        catch(...) { vkDestroyImageView(s.device,view,nullptr);throw; }
        VkDescriptorImageInfo image{s.sampler,view,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkDescriptorImageInfo depth{s.depthSampler,s.depthView,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkWriteDescriptorSet writes[2]{};
        writes[0].sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;writes[0].dstSet=set;writes[0].dstBinding=0;
        writes[0].descriptorCount=1;writes[0].descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;writes[0].pImageInfo=&image;
        writes[1].sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;writes[1].dstSet=set;writes[1].dstBinding=1;
        writes[1].descriptorCount=1;writes[1].descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;writes[1].pImageInfo=&depth;
        vkUpdateDescriptorSets(s.device,2,writes,0,nullptr);
        found=s.sources.emplace(source,State::Source{view,set}).first;
    }
    uploadDepth(command);
    VkRenderPassBeginInfo begin{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};begin.renderPass=s.pass;
    begin.framebuffer=s.frames.at(target);begin.renderArea={{0,0},{s.width,s.height}};
    vkCmdBeginRenderPass(command,&begin,VK_SUBPASS_CONTENTS_INLINE);
    vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_GRAPHICS,s.pipeline);
    if(s.source.video) {
        float parameters[24]{};
        std::memcpy(parameters,videoParameters,24*sizeof(float));
        vkCmdPushConstants(command,s.layout,VK_SHADER_STAGE_FRAGMENT_BIT,0,96,parameters);
    } else {
        const float disabled[8]={0,0,0,0,1,1,1,1};
        vkCmdPushConstants(command,s.layout,VK_SHADER_STAGE_FRAGMENT_BIT,0,32,colorCheckParameters?colorCheckParameters:disabled);
    }
    vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_GRAPHICS,s.layout,0,1,&found->second.set,0,nullptr);
    vkCmdDraw(command,3,1,0,0);vkCmdEndRenderPass(command);
}
}
