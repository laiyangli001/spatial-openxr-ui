#include "vulkan_compose_surface.h"
#include "xr_color_contract.h"
#include "vulkan_ui_color_pass.h"
#define XR_USE_GRAPHICS_API_VULKAN
#include <openxr/openxr_platform.h>
#include <android/hardware_buffer.h>
#include <android/native_window_jni.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>
#include <poll.h>
#include <unistd.h>
#include <fstream>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace moonlight::xr {
using namespace bluevk;
namespace {
void checkVk(const char* call,VkResult result) {
    if(result!=VK_SUCCESS) throw std::runtime_error(std::string(call)+": "+std::to_string(result));
}
void checkXr(const char* call,XrResult result) {
    if(result!=XR_SUCCESS) throw std::runtime_error(std::string(call)+": "+std::to_string(result));
}
struct ImportedBuffer {
    VkImage image=VK_NULL_HANDLE;
    VkDeviceMemory memory=VK_NULL_HANDLE;
};
}
struct VulkanComposeSurface::State {
    JNIEnv* env; jobject activity;
    VulkanSurfaceConfig config;
    uint32_t width=1600,height=900;
    VkDevice device; VkPhysicalDevice physical; VkQueue queue; uint32_t family;
    AImageReader* reader=nullptr; AImage* pending=nullptr; int acquireFd=-1;
    VkCommandPool pool=VK_NULL_HANDLE; VkCommandBuffer command=VK_NULL_HANDLE;
    VkFence fence=VK_NULL_HANDLE;
    XrSwapchain chain=XR_NULL_HANDLE;
    std::vector<XrSwapchainImageVulkan2KHR> images;
    std::vector<bool> initialized;
    std::unique_ptr<VulkanUiColorPass> colorPass;
    std::unordered_map<AHardwareBuffer*,ImportedBuffer> imported;
    uint64_t count=0;
    bool producer=false;
    bool fenceSubmitted=false,leaseReady=false;
    bool captureForQa=false;
    uint32_t captures=0;
    jmethodID colorCheckRequest=nullptr,colorCheckCaptured=nullptr,colorCheckParameters=nullptr;
    VkBuffer captureBuffer=VK_NULL_HANDLE; VkDeviceMemory captureMemory=VK_NULL_HANDLE;
    State(JNIEnv* e,jobject a,VkDevice d,VkPhysicalDevice p,VkQueue q,uint32_t f)
        :env(e),activity(a),device(d),physical(p),queue(q),family(f) {
        jclass cls=env->GetObjectClass(activity);
        colorCheckParameters=env->GetMethodID(cls,"getColorCheckGpuParameters","()[F");
        colorCheckRequest=env->GetMethodID(cls,"getColorCheckCaptureRequest","()J");
        colorCheckCaptured=env->GetMethodID(cls,"onColorCheckCaptured","(J[BII)V");
        env->DeleteLocalRef(cls);
    }
    void ensureCaptureBuffer() {
        if(captureBuffer) return;
        VkBufferCreateInfo buffer{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO}; buffer.size=static_cast<VkDeviceSize>(width)*height*4;
        buffer.usage=VK_BUFFER_USAGE_TRANSFER_DST_BIT;
        checkVk("UI capture buffer",vkCreateBuffer(device,&buffer,nullptr,&captureBuffer));
        VkMemoryRequirements requirements{}; vkGetBufferMemoryRequirements(device,captureBuffer,&requirements);
        VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO}; allocation.allocationSize=requirements.size;
        allocation.memoryTypeIndex=memoryType(requirements.memoryTypeBits,VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        checkVk("UI capture memory",vkAllocateMemory(device,&allocation,nullptr,&captureMemory));
        checkVk("UI capture bind",vkBindBufferMemory(device,captureBuffer,captureMemory,0));
    }
    ~State() {
        if(producer) {
            jclass cls=env->GetObjectClass(activity);
            jstring id=env->NewStringUTF(config.id.c_str());
            env->CallVoidMethod(activity,env->GetMethodID(cls,"onNativeVulkanSurfaceStop","(Ljava/lang/String;J)V"),id,static_cast<jlong>(config.generation));
            env->DeleteLocalRef(id);
            env->DeleteLocalRef(cls);
        }
        if(fenceSubmitted) vkWaitForFences(device,1,&fence,VK_TRUE,UINT64_MAX);
        if(leaseReady) {
            XrSwapchainImageReleaseInfo release{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
            xrReleaseSwapchainImage(chain,&release);
        }
        colorPass.reset();
        if(pending) AImage_deleteAsync(pending,acquireFd);
        for(auto& entry:imported) {
            vkDestroyImage(device,entry.second.image,nullptr);
            vkFreeMemory(device,entry.second.memory,nullptr);
            AHardwareBuffer_release(entry.first);
        }
        if(chain) xrDestroySwapchain(chain);
        if(reader) AImageReader_delete(reader);
        if(captureBuffer) vkDestroyBuffer(device,captureBuffer,nullptr);
        if(captureMemory) vkFreeMemory(device,captureMemory,nullptr);
        if(fence) vkDestroyFence(device,fence,nullptr);
        if(pool) vkDestroyCommandPool(device,pool,nullptr);
    }
    uint32_t memoryType(uint32_t bits,VkMemoryPropertyFlags required=0) {
        VkPhysicalDeviceMemoryProperties props{}; vkGetPhysicalDeviceMemoryProperties(physical,&props);
        for(uint32_t i=0;i<props.memoryTypeCount;++i)
            if((bits&(1u<<i)) && (props.memoryTypes[i].propertyFlags&required)==required) return i;
        throw std::runtime_error("No AHardwareBuffer memory type");
    }
    ImportedBuffer& import(AHardwareBuffer* buffer) {
        auto found=imported.find(buffer); if(found!=imported.end()) return found->second;
        VkAndroidHardwareBufferFormatPropertiesANDROID format{VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID};
        VkAndroidHardwareBufferPropertiesANDROID properties{VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID}; properties.pNext=&format;
        checkVk("AHB properties",vkGetAndroidHardwareBufferPropertiesANDROID(device,buffer,&properties));
        if(format.format!=VK_FORMAT_R8G8B8A8_UNORM || !(format.formatFeatures&VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT))
            throw std::runtime_error("Compose AHB requires sampleable RGBA8; actual="+std::to_string(format.format));
        ImportedBuffer target;
        VkExternalMemoryImageCreateInfo external{VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO};
        external.handleTypes=VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;
        VkImageCreateInfo image{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO}; image.pNext=&external;
        image.imageType=VK_IMAGE_TYPE_2D; image.format=format.format; image.extent={width,height,1};
        image.mipLevels=1; image.arrayLayers=1; image.samples=VK_SAMPLE_COUNT_1_BIT;
        image.tiling=VK_IMAGE_TILING_OPTIMAL; image.usage=VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        image.sharingMode=VK_SHARING_MODE_EXCLUSIVE; image.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        checkVk("AHB image",vkCreateImage(device,&image,nullptr,&target.image));
        VkImportAndroidHardwareBufferInfoANDROID importedInfo{VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID}; importedInfo.buffer=buffer;
        VkMemoryDedicatedAllocateInfo dedicated{VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO}; dedicated.pNext=&importedInfo; dedicated.image=target.image;
        VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO}; allocation.pNext=&dedicated;
        allocation.allocationSize=properties.allocationSize; allocation.memoryTypeIndex=memoryType(properties.memoryTypeBits);
        VkResult result=vkAllocateMemory(device,&allocation,nullptr,&target.memory);
        if(result!=VK_SUCCESS) { vkDestroyImage(device,target.image,nullptr); checkVk("AHB memory",result); }
        result=vkBindImageMemory(device,target.image,target.memory,0);
        if(result!=VK_SUCCESS) {
            vkDestroyImage(device,target.image,nullptr); vkFreeMemory(device,target.memory,nullptr); checkVk("AHB bind",result);
        }
        AHardwareBuffer_acquire(buffer);
        return imported.emplace(buffer,target).first->second;
    }
};
VulkanComposeSurface::VulkanComposeSurface(JNIEnv* env,jobject activity,VkDevice device,
        VkPhysicalDevice physical,VkQueue queue,uint32_t family,XrSession session,bool captureForQa,VulkanSurfaceConfig config)
        :state(std::make_unique<State>(env,activity,device,physical,queue,family)) {
    auto& s=*state;
    s.config=std::move(config); s.width=s.config.width; s.height=s.config.height;
    const auto width=s.width,height=s.height;
    s.captureForQa=captureForQa;
    if(captureForQa) s.ensureCaptureBuffer();
    const auto usage=AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE|AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER;
    if(AImageReader_newWithUsage(width,height,AIMAGE_FORMAT_RGBA_8888,usage,3,&s.reader)!=AMEDIA_OK)
        throw std::runtime_error("Compose AImageReader allocation failed");
    ANativeWindow* window=nullptr;
    if(AImageReader_getWindow(s.reader,&window)!=AMEDIA_OK) throw std::runtime_error("Compose reader window failed");
    uint32_t n=0; checkXr("UI formats",xrEnumerateSwapchainFormats(session,0,&n,nullptr));
    std::vector<int64_t> formats(n); checkXr("UI formats",xrEnumerateSwapchainFormats(session,n,&n,formats.data()));
    bool supported=false; for(auto f:formats) supported|=f==VK_FORMAT_R8G8B8A8_SRGB;
    if(!supported) throw std::runtime_error("UI requires RGBA8 sRGB XR format");
    XrSwapchainCreateInfo info{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    if(s.config.staticImage) info.createFlags=XR_SWAPCHAIN_CREATE_STATIC_IMAGE_BIT;
    info.format=VK_FORMAT_R8G8B8A8_SRGB;
    colorAuditEvent(env,activity,"Vulkan UI selected=VK_FORMAT_R8G8B8A8_SRGB(43); AHB view=UNORM; shader=linear-premultiplied; attachment=sRGB");
    info.usageFlags=XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT|XR_SWAPCHAIN_USAGE_SAMPLED_BIT|XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT;
    // vkCmdCopyImageToBuffer is legal only on swapchains created with TRANSFER_SRC.
    if(captureForQa || s.config.id=="main") info.usageFlags|=XR_SWAPCHAIN_USAGE_TRANSFER_SRC_BIT;
    info.width=width; info.height=height; info.sampleCount=1; info.arraySize=1; info.faceCount=1; info.mipCount=1;
    checkXr("UI swapchain",xrCreateSwapchain(session,&info,&s.chain));
    checkXr("UI image count",xrEnumerateSwapchainImages(s.chain,0,&n,nullptr));
    s.images.resize(n,{XR_TYPE_SWAPCHAIN_IMAGE_VULKAN2_KHR}); s.initialized.resize(n,false);
    checkXr("UI images",xrEnumerateSwapchainImages(s.chain,n,&n,reinterpret_cast<XrSwapchainImageBaseHeader*>(s.images.data())));
    std::vector<VkImage> targets;for(auto& image:s.images) targets.push_back(image.image);
    s.colorPass=std::make_unique<VulkanUiColorPass>(device,physical,queue,family,width,height,targets);
    VkCommandPoolCreateInfo pool{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO}; pool.queueFamilyIndex=family; pool.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    checkVk("UI command pool",vkCreateCommandPool(device,&pool,nullptr,&s.pool));
    VkCommandBufferAllocateInfo allocate{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO}; allocate.commandPool=s.pool; allocate.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY; allocate.commandBufferCount=1;
    checkVk("UI command",vkAllocateCommandBuffers(device,&allocate,&s.command));
    VkFenceCreateInfo fence{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO}; fence.flags=VK_FENCE_CREATE_SIGNALED_BIT;
    checkVk("UI fence",vkCreateFence(device,&fence,nullptr,&s.fence));
    jobject surface=ANativeWindow_toSurface(env,window); jclass cls=env->GetObjectClass(activity);
    s.producer=true;
    jstring id=env->NewStringUTF(s.config.id.c_str());
    env->CallVoidMethod(activity,env->GetMethodID(cls,"onNativeVulkanSurfaceReady","(Ljava/lang/String;JLandroid/view/Surface;II)V"),
        id,static_cast<jlong>(s.config.generation),surface,static_cast<jint>(width),static_cast<jint>(height));
    env->DeleteLocalRef(id);
    env->DeleteLocalRef(surface); env->DeleteLocalRef(cls);
}
VulkanComposeSurface::~VulkanComposeSurface()=default;
bool VulkanComposeSurface::update() {
    auto& s=*state;
    if(s.config.staticImage && s.count>0) return false;
    const auto width=s.width,height=s.height;
    if(!s.pending) {
        const auto result=AImageReader_acquireLatestImageAsync(s.reader,&s.pending,&s.acquireFd);
        if(result==AMEDIA_IMGREADER_NO_BUFFER_AVAILABLE) return false;
        if(result!=AMEDIA_OK) throw std::runtime_error("Compose acquire failed: "+std::to_string(result));
    }
    if(s.acquireFd>=0) {
        pollfd item{s.acquireFd,POLLIN,0};
        if(poll(&item,1,0)<=0) return false;
        close(s.acquireFd); s.acquireFd=-1;
    }
    AHardwareBuffer* buffer=nullptr;
    if(AImage_getHardwareBuffer(s.pending,&buffer)!=AMEDIA_OK) throw std::runtime_error("Compose AHB missing");
    auto& imported=s.import(buffer);
    uint32_t index=0; XrSwapchainImageAcquireInfo acquire{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
    checkXr("UI acquire",xrAcquireSwapchainImage(s.chain,&acquire,&index));
    XrSwapchainImageWaitInfo wait{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO}; wait.timeout=XR_INFINITE_DURATION;
    checkXr("UI wait",xrWaitSwapchainImage(s.chain,&wait));
    s.leaseReady=true;
    checkVk("UI fence wait",vkWaitForFences(s.device,1,&s.fence,VK_TRUE,UINT64_MAX));
    checkVk("UI reset command",vkResetCommandBuffer(s.command,0));
    VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO}; begin.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    checkVk("UI begin",vkBeginCommandBuffer(s.command,&begin));
    VkImageMemoryBarrier barriers[2]{{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER},{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER}};
    barriers[0].image=imported.image; barriers[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_FOREIGN_EXT; barriers[0].dstQueueFamilyIndex=s.family;
    barriers[0].oldLayout=VK_IMAGE_LAYOUT_GENERAL; barriers[0].newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    barriers[0].dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
    barriers[1].image=s.images[index].image; barriers[1].srcQueueFamilyIndex=barriers[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    barriers[1].oldLayout=s.initialized[index]?VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:VK_IMAGE_LAYOUT_UNDEFINED;
    barriers[1].newLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL; barriers[1].dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    for(auto& b:barriers) b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    vkCmdPipelineBarrier(s.command,VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT|VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,0,0,nullptr,0,nullptr,2,barriers);
    float check[8]={0,0,0,0,1,1,1,1};
    if(s.config.id=="main") {
        auto parameters=static_cast<jfloatArray>(s.env->CallObjectMethod(s.activity,s.colorCheckParameters));
        if(parameters) {
            if(s.env->GetArrayLength(parameters)==8) s.env->GetFloatArrayRegion(parameters,0,8,check);
            s.env->DeleteLocalRef(parameters);
        }
    }
    s.colorPass->draw(s.command,imported.image,index,nullptr,check);
    const jlong colorRequest=s.config.id=="main" ? s.env->CallLongMethod(s.activity,s.colorCheckRequest) : 0;
    const bool qaCaptureNow=s.captureForQa && (s.captures==0 || (s.captures==1 && s.count>=90));
    const bool captureNow=qaCaptureNow || colorRequest>0;
    if(captureNow) s.ensureCaptureBuffer();
    if(captureNow) {
        auto captureBarrier=barriers[1]; captureBarrier.oldLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        captureBarrier.newLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        captureBarrier.srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT; captureBarrier.dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;
        vkCmdPipelineBarrier(s.command,VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,nullptr,0,nullptr,1,&captureBarrier);
        VkBufferImageCopy read{}; read.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1}; read.imageExtent={width,height,1};
        vkCmdCopyImageToBuffer(s.command,s.images[index].image,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,s.captureBuffer,1,&read);
        VkBufferMemoryBarrier visibility{VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER}; visibility.buffer=s.captureBuffer;
        visibility.size=VK_WHOLE_SIZE; visibility.srcQueueFamilyIndex=visibility.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        visibility.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; visibility.dstAccessMask=VK_ACCESS_HOST_READ_BIT;
        vkCmdPipelineBarrier(s.command,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_HOST_BIT,0,0,nullptr,1,&visibility,0,nullptr);
    }
    barriers[0].srcQueueFamilyIndex=s.family; barriers[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_FOREIGN_EXT;
    barriers[0].oldLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; barriers[0].newLayout=VK_IMAGE_LAYOUT_GENERAL;
    barriers[0].srcAccessMask=VK_ACCESS_SHADER_READ_BIT; barriers[0].dstAccessMask=0;
    barriers[1].oldLayout=captureNow?VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    barriers[1].newLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    barriers[1].srcAccessMask=captureNow?VK_ACCESS_TRANSFER_READ_BIT:VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    barriers[1].dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
    vkCmdPipelineBarrier(s.command,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT|VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,0,0,nullptr,0,nullptr,2,barriers);
    checkVk("UI end",vkEndCommandBuffer(s.command));
    checkVk("UI reset fence",vkResetFences(s.device,1,&s.fence));
    VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO}; submit.commandBufferCount=1; submit.pCommandBuffers=&s.command;
    checkVk("UI submit",vkQueueSubmit(s.queue,1,&submit,s.fence));
    s.fenceSubmitted=true;
    checkVk("UI complete",vkWaitForFences(s.device,1,&s.fence,VK_TRUE,UINT64_MAX));
    if(captureNow) {
        void* data=nullptr; checkVk("UI capture map",vkMapMemory(s.device,s.captureMemory,0,width*height*4,0,&data));
        const auto* rgba=static_cast<const uint8_t*>(data);
        if(colorRequest>0) {
            const auto size=static_cast<jsize>(width*height*4);
            jbyteArray pixels=s.env->NewByteArray(size);
            if(pixels) {
                s.env->SetByteArrayRegion(pixels,0,size,reinterpret_cast<const jbyte*>(rgba));
                s.env->CallVoidMethod(s.activity,s.colorCheckCaptured,colorRequest,pixels,static_cast<jint>(width),static_cast<jint>(height));
                s.env->DeleteLocalRef(pixels);
            }
            if(s.env->ExceptionCheck()) {s.env->ExceptionDescribe();s.env->ExceptionClear();}
        }
        if(qaCaptureNow) {
        std::vector<uint8_t> rgb(width*height*3);
        for(size_t p=0;p<width*height;++p) for(int c=0;c<3;++c) rgb[p*3+c]=rgba[p*4+c];
        const auto suffix=s.config.id=="main"?std::string("ui"):s.config.id;
        const auto path=std::string("/sdcard/Android/data/com.moonlightxr.nativeui.preview/files/moonlightxr_qa_vulkan_")+suffix+
            (s.captures==0?".ppm":"_after.ppm");
        std::ofstream file(path,std::ios::binary);
        file<<"P6\n"<<width<<" "<<height<<"\n255\n";
        file.write(reinterpret_cast<const char*>(rgb.data()),rgb.size());
        std::ofstream raw(path+".rgba",std::ios::binary);
        raw.write(reinterpret_cast<const char*>(rgba),static_cast<size_t>(width)*height*4);
        uint64_t clear=0,partial=0,opaque=0;
        for(size_t p=0;p<static_cast<size_t>(width)*height;++p) {
            const auto alpha=rgba[p*4+3];
            if(alpha==0) ++clear; else if(alpha==255) ++opaque; else ++partial;
        }
        std::ofstream metadata(path+".json");
        metadata<<"{\"id\":\""<<s.config.id<<"\",\"generation\":"<<s.config.generation
            <<",\"width\":"<<width<<",\"height\":"<<height
            <<",\"clearPixels\":"<<clear<<",\"partialAlphaPixels\":"<<partial<<",\"opaquePixels\":"<<opaque<<"}";
        if(file.good()) ++s.captures;
        }
        vkUnmapMemory(s.device,s.captureMemory);
    }
    XrSwapchainImageReleaseInfo release{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO}; checkXr("UI release",xrReleaseSwapchainImage(s.chain,&release));
    s.leaseReady=false; s.fenceSubmitted=false;
    s.initialized[index]=true; ++s.count;
    AImage_delete(s.pending); s.pending=nullptr;
    return true;
}
bool VulkanComposeSurface::ready() const { return state->count>0; }
uint64_t VulkanComposeSurface::updates() const { return state->count; }
XrCompositionLayerQuad VulkanComposeSurface::layer(XrSpace space,const XrPosef& pose) const {
    XrCompositionLayerQuad quad{XR_TYPE_COMPOSITION_LAYER_QUAD}; quad.space=space; quad.pose=pose;
    quad.layerFlags=XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
    quad.eyeVisibility=XR_EYE_VISIBILITY_BOTH; quad.size={state->config.widthMeters,state->config.heightMeters};
    quad.subImage.swapchain=state->chain; quad.subImage.imageRect.extent={static_cast<int32_t>(state->width),static_cast<int32_t>(state->height)};
    return quad;
}
}
