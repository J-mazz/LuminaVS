#include "renderer_vulkan.h"

#include <android/log.h>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>

#define LOG_TAG "LuminaVulkan"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
// Single-flight semaphore/fence per swapchain image; no separate constant needed.

template <typename T>
T makeStruct(VkStructureType sType) {
    T t{};
    t.sType = sType;
    t.pNext = nullptr;
    return t;
}

bool hasExtension(const std::vector<VkExtensionProperties>& exts, const char* name) {
    return std::any_of(exts.begin(), exts.end(), [&](const auto& e) {
        return strcmp(e.extensionName, name) == 0;
    });
}
}

// Prefer generated shader header if available (produced by the Gradle task)
#if __has_include("generated/shaders_generated.h")
#include "generated/shaders_generated.h"
#endif


bool VulkanRenderer::initialize(ANativeWindow* window) {
    if (initialized_) return true;

    if (!createInstance()) return false;
    if (!createSurface(window)) return false;
    if (!pickPhysicalDevice()) return false;
    if (!createDevice()) return false;
    if (!createCommandPool()) return false;
    if (!createSwapchain()) return false;
    if (!createRenderPass()) return false;
    if (!createDescriptorSetLayout()) return false;
    if (!createPipelineLayout()) return false;
    if (!createGraphicsPipeline()) return false;
    if (!createFramebuffers()) return false;
    if (!createTextureResources()) return false;
    if (!createSampler()) return false;
    if (!createDescriptorPoolAndSets()) return false;
    if (!createSyncObjects()) return false;
    if (!recordCommandBuffers()) return false;

    initialized_ = true;
    return true;
}
bool VulkanRenderer::render(const lumina::LuminaState& state) {
    if (!initialized_) return false;

    // [FIX] 1. Map High-Level State -> Low-Level GPU Struct
    const lumina::EffectParams* active = (state.activeEffectCount > 0) ? &state.effects[0] : nullptr;

    effectParams_.time = state.timing.totalTime;
    effectParams_.intensity = active ? active->intensity : 0.0f;
    effectParams_.effectType = active ? static_cast<int>(active->type) : 0;

    // Manual copy for safety across JNI/ABI boundaries
    if (active) {
        effectParams_.tint[0] = active->tintColor.r;
        effectParams_.tint[1] = active->tintColor.g;
        effectParams_.tint[2] = active->tintColor.b;
        effectParams_.tint[3] = active->tintColor.a;

        effectParams_.center[0] = active->center.x;
        effectParams_.center[1] = active->center.y;

        effectParams_.scale[0] = active->scale.x;
        effectParams_.scale[1] = active->scale.y;

        effectParams_.params[0] = active->param1;
        effectParams_.params[1] = active->param2;
    } else {
        // Defaults
        effectParams_.tint[0] = 1.0f; effectParams_.tint[1] = 1.0f;
        effectParams_.tint[2] = 1.0f; effectParams_.tint[3] = 1.0f;
        effectParams_.center[0] = 0.5f; effectParams_.center[1] = 0.5f;
        effectParams_.scale[0] = 1.0f; effectParams_.scale[1] = 1.0f;
    }

    // Update resolution uniform
    effectParams_.resolution[0] = static_cast<float>(swapchain_.width);
    effectParams_.resolution[1] = static_cast<float>(swapchain_.height);

    // [FIX] 2. Standard Vulkan Render Loop
    const size_t frameIndex = currentFrame_ % swapchain_.images.size();
    
    // Wait for the fence to ensure we can write to this frame's resources
    vkWaitForFences(device_, 1, &swapchain_.inFlightFences[frameIndex], VK_TRUE, UINT64_MAX);

    uint32_t imageIndex = 0;
    VkResult acquire = vkAcquireNextImageKHR(device_, swapchain_.swapchain, UINT64_MAX, swapchain_.imageAvailable[frameIndex], VK_NULL_HANDLE, &imageIndex);
    
    if (acquire == VK_ERROR_OUT_OF_DATE_KHR) return recreate(window_);
    if (acquire != VK_SUCCESS && acquire != VK_SUBOPTIMAL_KHR) return false;

    vkResetFences(device_, 1, &swapchain_.inFlightFences[frameIndex]);

    // [CRITICAL] Record commands *now* to capture the latest effectParams_
    recordCommandBuffer(imageIndex);

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    
    VkSemaphore waitSemaphores[] = {swapchain_.imageAvailable[frameIndex]};
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = waitSemaphores;
    submitInfo.pWaitDstStageMask = waitStages;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &swapchain_.commandBuffers[imageIndex];
    
    VkSemaphore signalSemaphores[] = {swapchain_.renderFinished[frameIndex]};
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = signalSemaphores;

    if (vkQueueSubmit(graphicsQueue_, 1, &submitInfo, swapchain_.inFlightFences[frameIndex]) != VK_SUCCESS) {
        return false;
    }

    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = signalSemaphores;
    VkSwapchainKHR swapchains[] = {swapchain_.swapchain};
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = swapchains;
    presentInfo.pImageIndices = &imageIndex;

    VkResult present = vkQueuePresentKHR(graphicsQueue_, &presentInfo);
    if (present == VK_ERROR_OUT_OF_DATE_KHR || present == VK_SUBOPTIMAL_KHR) {
        return recreate(window_);
    }

    currentFrame_++;
    return true;
}
void VulkanRenderer::setEffectParams(const EffectParams& params) {
    effectParams_ = params;
}
void VulkanRenderer::destroy() {
    // Destroy non-swapchain resources
    if (textureSampler_ != VK_NULL_HANDLE) {
        vkDestroySampler(device_, textureSampler_, nullptr);
        textureSampler_ = VK_NULL_HANDLE;
    }
    if (textureView_ != VK_NULL_HANDLE) {
        vkDestroyImageView(device_, textureView_, nullptr);
        textureView_ = VK_NULL_HANDLE;
    }
    if (textureImage_ != VK_NULL_HANDLE) {
        vkDestroyImage(device_, textureImage_, nullptr);
        textureImage_ = VK_NULL_HANDLE;
    }
    if (textureMemory_ != VK_NULL_HANDLE) {
        vkFreeMemory(device_, textureMemory_, nullptr);
        textureMemory_ = VK_NULL_HANDLE;
    }
    if (staging_.buffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(device_, staging_.buffer, nullptr);
        staging_.buffer = VK_NULL_HANDLE;
    }
    if (staging_.memory != VK_NULL_HANDLE) {
        vkFreeMemory(device_, staging_.memory, nullptr);
        staging_.memory = VK_NULL_HANDLE;
    }
    if (descriptorPool_ != VK_NULL_HANDLE) {
        vkDestroyDescriptorPool(device_, descriptorPool_, nullptr);
        descriptorPool_ = VK_NULL_HANDLE;
    }
    if (descriptorSetLayout_ != VK_NULL_HANDLE) {
        vkDestroyDescriptorSetLayout(device_, descriptorSetLayout_, nullptr);
        descriptorSetLayout_ = VK_NULL_HANDLE;
    }
    if (graphicsPipeline_ != VK_NULL_HANDLE) {
        vkDestroyPipeline(device_, graphicsPipeline_, nullptr);
        graphicsPipeline_ = VK_NULL_HANDLE;
    }
    if (pipelineLayout_ != VK_NULL_HANDLE) {
        vkDestroyPipelineLayout(device_, pipelineLayout_, nullptr);
        pipelineLayout_ = VK_NULL_HANDLE;
    }
    if (renderPass_ != VK_NULL_HANDLE) {
        vkDestroyRenderPass(device_, renderPass_, nullptr);
        renderPass_ = VK_NULL_HANDLE;
    }

    cleanupSwapchain();

    if (commandPool_ != VK_NULL_HANDLE) {
        vkDestroyCommandPool(device_, commandPool_, nullptr);
        commandPool_ = VK_NULL_HANDLE;
    }

    if (device_ != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(device_);
        vkDestroyDevice(device_, nullptr);
        device_ = VK_NULL_HANDLE;
    }

    if (surface_ != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(instance_, surface_, nullptr);
        surface_ = VK_NULL_HANDLE;
    }

    if (instance_ != VK_NULL_HANDLE) {
        vkDestroyInstance(instance_, nullptr);
        instance_ = VK_NULL_HANDLE;
    }

    initialized_ = false;
}

bool VulkanRenderer::recreate(ANativeWindow* window) {
    vkDeviceWaitIdle(device_);
    cleanupSwapchain();
    if (window) window_ = window;
    if (!createSurface(window_)) return false;
    if (!createSwapchain()) return false;
    if (!createRenderPass()) return false;
    if (!createGraphicsPipeline()) return false;
    if (!createFramebuffers()) return false;
    if (!createDescriptorPoolAndSets()) return false;
    if (!createSyncObjects()) return false;
    if (!recordCommandBuffers()) return false;
    return true;
}

// Removed legacy parameterless render() - use render(const LuminaState&) instead.

bool VulkanRenderer::recordCommandBuffer(uint32_t imageIndex) {
        if (imageIndex >= swapchain_.commandBuffers.size()) return false;

        VkCommandBuffer cmd = swapchain_.commandBuffers[imageIndex];
        vkResetCommandBuffer(cmd, 0);

        auto bi = makeStruct<VkCommandBufferBeginInfo>(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        bi.flags = VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT;
        if (vkBeginCommandBuffer(cmd, &bi) != VK_SUCCESS) {
            LOGE("vkBeginCommandBuffer failed for idx %u", imageIndex);
            return false;
        }

        VkClearValue clear{};
        clear.color = { {0.05f, 0.07f, 0.10f, 1.0f} };

        auto rp = makeStruct<VkRenderPassBeginInfo>(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
        rp.renderPass = renderPass_;
        rp.framebuffer = framebuffers_[imageIndex];
        rp.renderArea.offset = {0, 0};
        rp.renderArea.extent = { swapchain_.width, swapchain_.height };
        rp.clearValueCount = 1;
        rp.pClearValues = &clear;

        vkCmdBeginRenderPass(cmd, &rp, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline_);

        VkViewport viewport{};
        viewport.x = 0;
        viewport.y = 0;
        viewport.width = static_cast<float>(swapchain_.width);
        viewport.height = static_cast<float>(swapchain_.height);
        viewport.minDepth = 0.f;
        viewport.maxDepth = 1.f;
        vkCmdSetViewport(cmd, 0, 1, &viewport);

        VkRect2D scissor{ {0,0}, { swapchain_.width, swapchain_.height } };
        vkCmdSetScissor(cmd, 0, 1, &scissor);

        vkCmdPushConstants(cmd, pipelineLayout_, VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(EffectParams), &effectParams_);

        vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout_, 0, 1, &descriptorSets_[imageIndex], 0, nullptr);
        vkCmdDraw(cmd, 4, 1, 0, 0);

        vkCmdEndRenderPass(cmd);

        if (vkEndCommandBuffer(cmd) != VK_SUCCESS) {
            LOGE("vkEndCommandBuffer failed for idx %u", imageIndex);
            return false;
        }
        return true;
    }

bool VulkanRenderer::createInstance() {
    uint32_t extCount = 0;
    vkEnumerateInstanceExtensionProperties(nullptr, &extCount, nullptr);
    std::vector<VkExtensionProperties> exts(extCount);
    vkEnumerateInstanceExtensionProperties(nullptr, &extCount, exts.data());

    if (!hasExtension(exts, VK_KHR_SURFACE_EXTENSION_NAME) ||
        !hasExtension(exts, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME)) {
        LOGE("Required Vulkan surface extensions not available");
        return false;
    }

    const char* requiredExts[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
    };

    auto app = makeStruct<VkApplicationInfo>(VK_STRUCTURE_TYPE_APPLICATION_INFO);
    app.pApplicationName = "LuminaVS";
    app.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    app.pEngineName = "LuminaEngine";
    app.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    app.apiVersion = VK_API_VERSION_1_1;

    auto ci = makeStruct<VkInstanceCreateInfo>(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
    ci.pApplicationInfo = &app;
    ci.enabledExtensionCount = 2;
    ci.ppEnabledExtensionNames = requiredExts;

    VkResult res = vkCreateInstance(&ci, nullptr, &instance_);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateInstance failed: %d", res);
        return false;
    }
    return true;
}

bool VulkanRenderer::createSurface(ANativeWindow* window) {
    if (surface_ != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(instance_, surface_, nullptr);
        surface_ = VK_NULL_HANDLE;
    }

    if (!window) {
        LOGW("No native window provided for surface creation" );
        return false;
    }

    window_ = window;

    auto ci = makeStruct<VkAndroidSurfaceCreateInfoKHR>(VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR);
    ci.window = window;
    VkResult res = vkCreateAndroidSurfaceKHR(instance_, &ci, nullptr, &surface_);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateAndroidSurfaceKHR failed: %d", res);
        return false;
    }
    return true;
}

uint32_t VulkanRenderer::findGraphicsQueueFamily(VkPhysicalDevice device) {
    uint32_t count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, nullptr);
    std::vector<VkQueueFamilyProperties> props(count);
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, props.data());
    for (uint32_t i = 0; i < count; ++i) {
        if (props[i].queueCount > 0 && (props[i].queueFlags & VK_QUEUE_GRAPHICS_BIT)) {
            VkBool32 presentSupport = VK_FALSE;
            vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface_, &presentSupport);
            if (presentSupport) return i;
        }
    }
    return UINT32_MAX;
}

bool VulkanRenderer::pickPhysicalDevice() {
    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(instance_, &deviceCount, nullptr);
    if (deviceCount == 0) {
        LOGE("No Vulkan physical devices found");
        return false;
    }
    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(instance_, &deviceCount, devices.data());

    for (auto dev : devices) {
        uint32_t q = findGraphicsQueueFamily(dev);
        if (q != UINT32_MAX) {
            physicalDevice_ = dev;
            graphicsQueueFamily_ = q;
            return true;
        }
    }
    LOGE("No suitable graphics+present queue family found");
    return false;
}

bool VulkanRenderer::createDevice() {
    float priority = 1.0f;
    auto qci = makeStruct<VkDeviceQueueCreateInfo>(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
    qci.queueFamilyIndex = graphicsQueueFamily_;
    qci.queueCount = 1;
    qci.pQueuePriorities = &priority;

    const char* deviceExts[] = { VK_KHR_SWAPCHAIN_EXTENSION_NAME };

    auto ci = makeStruct<VkDeviceCreateInfo>(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
    ci.queueCreateInfoCount = 1;
    ci.pQueueCreateInfos = &qci;
    ci.enabledExtensionCount = 1;
    ci.ppEnabledExtensionNames = deviceExts;

    VkResult res = vkCreateDevice(physicalDevice_, &ci, nullptr, &device_);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateDevice failed: %d", res);
        return false;
    }
    vkGetDeviceQueue(device_, graphicsQueueFamily_, 0, &graphicsQueue_);
    return true;
}

bool VulkanRenderer::createCommandPool() {
    auto ci = makeStruct<VkCommandPoolCreateInfo>(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
    ci.queueFamilyIndex = graphicsQueueFamily_;
    ci.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    VkResult res = vkCreateCommandPool(device_, &ci, nullptr, &commandPool_);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateCommandPool failed: %d", res);
        return false;
    }
    return true;
}

bool VulkanRenderer::createSwapchain() {
    VkSurfaceCapabilitiesKHR caps{};
    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice_, surface_, &caps);

    uint32_t formatCount = 0;
    vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice_, surface_, &formatCount, nullptr);
    std::vector<VkSurfaceFormatKHR> formats(formatCount);
    vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice_, surface_, &formatCount, formats.data());

    uint32_t presentCount = 0;
    vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice_, surface_, &presentCount, nullptr);
    std::vector<VkPresentModeKHR> presents(presentCount);
    vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice_, surface_, &presentCount, presents.data());

    VkSurfaceFormatKHR chosenFormat = formats[0];
    for (const auto& f : formats) {
        if (f.format == VK_FORMAT_R8G8B8A8_UNORM && f.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
            chosenFormat = f;
            break;
        }
    }

    VkPresentModeKHR chosenPresent = VK_PRESENT_MODE_FIFO_KHR;
    for (const auto& pm : presents) {
        if (pm == VK_PRESENT_MODE_MAILBOX_KHR) { chosenPresent = pm; break; }
    }

    VkExtent2D extent = caps.currentExtent;
    if (extent.width == UINT32_MAX) {
        extent.width = 1280;
        extent.height = 720;
    }

    uint32_t imageCount = caps.minImageCount + 1;
    if (caps.maxImageCount > 0 && imageCount > caps.maxImageCount) {
        imageCount = caps.maxImageCount;
    }

    auto ci = makeStruct<VkSwapchainCreateInfoKHR>(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
    ci.surface = surface_;
    ci.minImageCount = imageCount;
    ci.imageFormat = chosenFormat.format;
    ci.imageColorSpace = chosenFormat.colorSpace;
    ci.imageExtent = extent;
    ci.imageArrayLayers = 1;
    ci.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    ci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ci.preTransform = caps.currentTransform;
    ci.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    ci.presentMode = chosenPresent;
    ci.clipped = VK_TRUE;
    ci.oldSwapchain = VK_NULL_HANDLE;

    VkResult res = vkCreateSwapchainKHR(device_, &ci, nullptr, &swapchain_.swapchain);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateSwapchainKHR failed: %d", res);
        return false;
    }

    uint32_t scImageCount = 0;
    vkGetSwapchainImagesKHR(device_, swapchain_.swapchain, &scImageCount, nullptr);
    swapchain_.images.resize(scImageCount);
    vkGetSwapchainImagesKHR(device_, swapchain_.swapchain, &scImageCount, swapchain_.images.data());

    swapchain_.width = extent.width;
    swapchain_.height = extent.height;
    swapchain_.format = chosenFormat.format;

    swapchain_.commandBuffers.resize(scImageCount);
    auto alloc = makeStruct<VkCommandBufferAllocateInfo>(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
    alloc.commandPool = commandPool_;
    alloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    alloc.commandBufferCount = scImageCount;
    VkResult ares = vkAllocateCommandBuffers(device_, &alloc, swapchain_.commandBuffers.data());
    if (ares != VK_SUCCESS) {
        LOGE("vkAllocateCommandBuffers failed: %d", ares);
        return false;
    }

    return true;
}

bool VulkanRenderer::createRenderPass() {
    VkAttachmentDescription colorAttach{};
    colorAttach.format = swapchain_.format;
    colorAttach.samples = VK_SAMPLE_COUNT_1_BIT;
    colorAttach.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    colorAttach.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    colorAttach.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    colorAttach.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    colorAttach.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    colorAttach.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference colorRef{};
    colorRef.attachment = 0;
    colorRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorRef;

    VkSubpassDependency dep{};
    dep.srcSubpass = VK_SUBPASS_EXTERNAL;
    dep.dstSubpass = 0;
    dep.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.srcAccessMask = 0;
    dep.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    auto ci = makeStruct<VkRenderPassCreateInfo>(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
    ci.attachmentCount = 1;
    ci.pAttachments = &colorAttach;
    ci.subpassCount = 1;
    ci.pSubpasses = &subpass;
    ci.dependencyCount = 1;
    ci.pDependencies = &dep;

    if (renderPass_ != VK_NULL_HANDLE) vkDestroyRenderPass(device_, renderPass_, nullptr);
    VkResult res = vkCreateRenderPass(device_, &ci, nullptr, &renderPass_);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateRenderPass failed: %d", res);
        return false;
    }
    return true;
}

bool VulkanRenderer::createDescriptorSetLayout() {
    VkDescriptorSetLayoutBinding samplerBinding{};
    samplerBinding.binding = 0;
    samplerBinding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    samplerBinding.descriptorCount = 1;
    samplerBinding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;

    auto ci = makeStruct<VkDescriptorSetLayoutCreateInfo>(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
    ci.bindingCount = 1;
    ci.pBindings = &samplerBinding;

    if (descriptorSetLayout_ != VK_NULL_HANDLE) vkDestroyDescriptorSetLayout(device_, descriptorSetLayout_, nullptr);
    VkResult res = vkCreateDescriptorSetLayout(device_, &ci, nullptr, &descriptorSetLayout_);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateDescriptorSetLayout failed: %d", res);
        return false;
    }
    return true;
}

bool VulkanRenderer::createPipelineLayout() {
    auto ci = makeStruct<VkPipelineLayoutCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
    ci.setLayoutCount = 1;
    ci.pSetLayouts = &descriptorSetLayout_;

    VkPushConstantRange push{};
    push.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    push.offset = 0;
    push.size = sizeof(EffectParams);
    ci.pushConstantRangeCount = 1;
    ci.pPushConstantRanges = &push;

    if (pipelineLayout_ != VK_NULL_HANDLE) vkDestroyPipelineLayout(device_, pipelineLayout_, nullptr);
    VkResult res = vkCreatePipelineLayout(device_, &ci, nullptr, &pipelineLayout_);
    if (res != VK_SUCCESS) {
        LOGE("vkCreatePipelineLayout failed: %d", res);
        return false;
    }
    return true;
}

bool VulkanRenderer::createGraphicsPipeline() {
    auto createShaderModule = [&](const std::vector<uint32_t>& code, VkShaderModule& out) {
        auto ci = makeStruct<VkShaderModuleCreateInfo>(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
        ci.codeSize = code.size() * sizeof(uint32_t);
        ci.pCode = code.data();
        return vkCreateShaderModule(device_, &ci, nullptr, &out) == VK_SUCCESS;
    };

    const std::vector<uint32_t> vs(kVertSpv.begin(), kVertSpv.end());
    const std::vector<uint32_t> fs(kFragSpv.begin(), kFragSpv.end());
    VkShaderModule vertModule = VK_NULL_HANDLE;
    VkShaderModule fragModule = VK_NULL_HANDLE;
    if (!createShaderModule(vs, vertModule) || !createShaderModule(fs, fragModule)) {
        LOGE("Failed to create shader modules");
        if (vertModule) vkDestroyShaderModule(device_, vertModule, nullptr);
        if (fragModule) vkDestroyShaderModule(device_, fragModule, nullptr);
        return false;
    }

    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = vertModule;
    stages[0].pName = "main";

    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = fragModule;
    stages[1].pName = "main";

    auto vertexInput = makeStruct<VkPipelineVertexInputStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
    auto inputAssembly = makeStruct<VkPipelineInputAssemblyStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
    inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    inputAssembly.primitiveRestartEnable = VK_FALSE;

    VkViewport viewport{};
    viewport.x = 0;
    viewport.y = 0;
    viewport.width = static_cast<float>(swapchain_.width);
    viewport.height = static_cast<float>(swapchain_.height);
    viewport.minDepth = 0.f;
    viewport.maxDepth = 1.f;

    VkRect2D scissor{};
    scissor.offset = {0, 0};
    scissor.extent = {swapchain_.width, swapchain_.height};

    auto viewportState = makeStruct<VkPipelineViewportStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
    viewportState.viewportCount = 1;
    viewportState.pViewports = &viewport;
    viewportState.scissorCount = 1;
    viewportState.pScissors = &scissor;

    auto raster = makeStruct<VkPipelineRasterizationStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
    raster.depthClampEnable = VK_FALSE;
    raster.rasterizerDiscardEnable = VK_FALSE;
    raster.polygonMode = VK_POLYGON_MODE_FILL;
    raster.cullMode = VK_CULL_MODE_NONE;
    raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    raster.depthBiasEnable = VK_FALSE;
    raster.lineWidth = 1.0f;

    auto msaa = makeStruct<VkPipelineMultisampleStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
    msaa.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    VkPipelineColorBlendAttachmentState blend{};
    blend.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    blend.blendEnable = VK_FALSE;

    auto blendState = makeStruct<VkPipelineColorBlendStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
    blendState.attachmentCount = 1;
    blendState.pAttachments = &blend;

    VkDynamicState dynamics[] = { VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR };
    auto dyn = makeStruct<VkPipelineDynamicStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO);
    dyn.dynamicStateCount = 2;
    dyn.pDynamicStates = dynamics;

    auto gp = makeStruct<VkGraphicsPipelineCreateInfo>(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
    gp.stageCount = 2;
    gp.pStages = stages;
    gp.pVertexInputState = &vertexInput;
    gp.pInputAssemblyState = &inputAssembly;
    gp.pViewportState = &viewportState;
    gp.pRasterizationState = &raster;
    gp.pMultisampleState = &msaa;
    gp.pDepthStencilState = nullptr;
    gp.pColorBlendState = &blendState;
    gp.pDynamicState = &dyn;
    gp.layout = pipelineLayout_;
    gp.renderPass = renderPass_;
    gp.subpass = 0;

    if (graphicsPipeline_ != VK_NULL_HANDLE) vkDestroyPipeline(device_, graphicsPipeline_, nullptr);
    VkResult res = vkCreateGraphicsPipelines(device_, VK_NULL_HANDLE, 1, &gp, nullptr, &graphicsPipeline_);
    vkDestroyShaderModule(device_, vertModule, nullptr);
    vkDestroyShaderModule(device_, fragModule, nullptr);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateGraphicsPipelines failed: %d", res);
        return false;
    }
    return true;
}

bool VulkanRenderer::createFramebuffers() {
    for (auto fb : framebuffers_) vkDestroyFramebuffer(device_, fb, nullptr);
    framebuffers_.clear();
    for (auto v : swapchain_.imageViews) if (v) vkDestroyImageView(device_, v, nullptr);
    swapchain_.imageViews.clear();

    framebuffers_.resize(swapchain_.images.size());
    swapchain_.imageViews.resize(swapchain_.images.size());

    for (size_t i = 0; i < swapchain_.images.size(); ++i) {
        auto vi = makeStruct<VkImageViewCreateInfo>(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
        vi.image = swapchain_.images[i];
        vi.viewType = VK_IMAGE_VIEW_TYPE_2D;
        vi.format = swapchain_.format;
        vi.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        vi.subresourceRange.levelCount = 1;
        vi.subresourceRange.layerCount = 1;
        if (vkCreateImageView(device_, &vi, nullptr, &swapchain_.imageViews[i]) != VK_SUCCESS) {
            LOGE("vkCreateImageView failed for swapchain image %zu", i);
            return false;
        }

        VkImageView attachments[] = { swapchain_.imageViews[i] };
        auto fi = makeStruct<VkFramebufferCreateInfo>(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
        fi.renderPass = renderPass_;
        fi.attachmentCount = 1;
        fi.pAttachments = attachments;
        fi.width = swapchain_.width;
        fi.height = swapchain_.height;
        fi.layers = 1;
        if (vkCreateFramebuffer(device_, &fi, nullptr, &framebuffers_[i]) != VK_SUCCESS) {
            LOGE("vkCreateFramebuffer failed for idx %zu", i);
            return false;
        }
    }
    return true;
}

bool VulkanRenderer::createTextureResources() {
    // For now, allocate a 1x1 texture to keep pipeline valid; real upload happens later.
    uint32_t width = 1, height = 1;
    auto ci = makeStruct<VkImageCreateInfo>(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
    ci.imageType = VK_IMAGE_TYPE_2D;
    ci.extent = { width, height, 1 };
    ci.mipLevels = 1;
    ci.arrayLayers = 1;
    ci.format = VK_FORMAT_R8G8B8A8_UNORM;
    ci.tiling = VK_IMAGE_TILING_OPTIMAL;
    ci.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    ci.usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    ci.samples = VK_SAMPLE_COUNT_1_BIT;
    ci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    if (vkCreateImage(device_, &ci, nullptr, &textureImage_) != VK_SUCCESS) {
        LOGE("vkCreateImage for texture failed");
        return false;
    }

    VkMemoryRequirements memReq{};
    vkGetImageMemoryRequirements(device_, textureImage_, &memReq);

    auto ai = makeStruct<VkMemoryAllocateInfo>(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
    ai.allocationSize = memReq.size;

    VkPhysicalDeviceMemoryProperties props{};
    vkGetPhysicalDeviceMemoryProperties(physicalDevice_, &props);
    uint32_t typeIndex = UINT32_MAX;
    for (uint32_t i = 0; i < props.memoryTypeCount; ++i) {
        if ((memReq.memoryTypeBits & (1 << i)) && (props.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
            typeIndex = i;
            break;
        }
    }
    if (typeIndex == UINT32_MAX) {
        LOGE("Failed to find device local memory for texture");
        return false;
    }
    ai.memoryTypeIndex = typeIndex;
    if (vkAllocateMemory(device_, &ai, nullptr, &textureMemory_) != VK_SUCCESS) {
        LOGE("vkAllocateMemory for texture failed");
        return false;
    }
    vkBindImageMemory(device_, textureImage_, textureMemory_, 0);

    // Transition to shader-read layout so descriptors are valid even before uploads.
    auto cbAlloc = makeStruct<VkCommandBufferAllocateInfo>(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
    cbAlloc.commandPool = commandPool_;
    cbAlloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbAlloc.commandBufferCount = 1;
    VkCommandBuffer cmd;
    if (vkAllocateCommandBuffers(device_, &cbAlloc, &cmd) != VK_SUCCESS) {
        LOGE("Failed to allocate temp command buffer for texture layout transition");
        return false;
    }
    auto bi = makeStruct<VkCommandBufferBeginInfo>(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
    bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmd, &bi);
    auto barrier = makeStruct<VkImageMemoryBarrier>(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
    barrier.srcAccessMask = 0;
    barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    barrier.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = textureImage_;
    barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.levelCount = 1;
    barrier.subresourceRange.layerCount = 1;
    vkCmdPipelineBarrier(cmd,
                         VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                         VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                         0,
                         0, nullptr,
                         0, nullptr,
                         1, &barrier);
    vkEndCommandBuffer(cmd);
    auto si = makeStruct<VkSubmitInfo>(VK_STRUCTURE_TYPE_SUBMIT_INFO);
    si.commandBufferCount = 1;
    si.pCommandBuffers = &cmd;
    vkQueueSubmit(graphicsQueue_, 1, &si, VK_NULL_HANDLE);
    vkQueueWaitIdle(graphicsQueue_);
    vkFreeCommandBuffers(device_, commandPool_, 1, &cmd);

    auto vi = makeStruct<VkImageViewCreateInfo>(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
    vi.image = textureImage_;
    vi.viewType = VK_IMAGE_VIEW_TYPE_2D;
    vi.format = VK_FORMAT_R8G8B8A8_UNORM;
    vi.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    vi.subresourceRange.levelCount = 1;
    vi.subresourceRange.layerCount = 1;
    if (vkCreateImageView(device_, &vi, nullptr, &textureView_) != VK_SUCCESS) {
        LOGE("vkCreateImageView for texture failed");
        return false;
    }
    return true;
}

bool VulkanRenderer::createSampler() {
    auto ci = makeStruct<VkSamplerCreateInfo>(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
    ci.magFilter = VK_FILTER_LINEAR;
    ci.minFilter = VK_FILTER_LINEAR;
    ci.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.anisotropyEnable = VK_FALSE;
    ci.maxAnisotropy = 1.0f;
    ci.borderColor = VK_BORDER_COLOR_INT_OPAQUE_BLACK;
    ci.unnormalizedCoordinates = VK_FALSE;
    ci.compareEnable = VK_FALSE;
    ci.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;
    if (vkCreateSampler(device_, &ci, nullptr, &textureSampler_) != VK_SUCCESS) {
        LOGE("vkCreateSampler failed");
        return false;
    }
    return true;
}

bool VulkanRenderer::createDescriptorPoolAndSets() {
    if (descriptorPool_ != VK_NULL_HANDLE) {
        vkDestroyDescriptorPool(device_, descriptorPool_, nullptr);
        descriptorPool_ = VK_NULL_HANDLE;
    }

    VkDescriptorPoolSize poolSize{};
    poolSize.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    poolSize.descriptorCount = static_cast<uint32_t>(swapchain_.images.size());

    auto pi = makeStruct<VkDescriptorPoolCreateInfo>(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
    pi.poolSizeCount = 1;
    pi.pPoolSizes = &poolSize;
    pi.maxSets = static_cast<uint32_t>(swapchain_.images.size());

    if (vkCreateDescriptorPool(device_, &pi, nullptr, &descriptorPool_) != VK_SUCCESS) {
        LOGE("vkCreateDescriptorPool failed");
        return false;
    }

    std::vector<VkDescriptorSetLayout> layouts(swapchain_.images.size(), descriptorSetLayout_);
    auto ai = makeStruct<VkDescriptorSetAllocateInfo>(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
    ai.descriptorPool = descriptorPool_;
    ai.descriptorSetCount = static_cast<uint32_t>(layouts.size());
    ai.pSetLayouts = layouts.data();

    descriptorSets_.resize(layouts.size());
    if (vkAllocateDescriptorSets(device_, &ai, descriptorSets_.data()) != VK_SUCCESS) {
        LOGE("vkAllocateDescriptorSets failed");
        return false;
    }

    for (size_t i = 0; i < descriptorSets_.size(); ++i) {
        VkDescriptorImageInfo ii{};
        ii.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        ii.imageView = textureView_;
        ii.sampler = textureSampler_;

        auto write = makeStruct<VkWriteDescriptorSet>(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        write.dstSet = descriptorSets_[i];
        write.dstBinding = 0;
        write.dstArrayElement = 0;
        write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        write.descriptorCount = 1;
        write.pImageInfo = &ii;
        vkUpdateDescriptorSets(device_, 1, &write, 0, nullptr);
    }
    return true;
}

bool VulkanRenderer::uploadTexture(const void* data, size_t size, uint32_t width, uint32_t height) {
    if (!data || size == 0 || width == 0 || height == 0) {
        LOGE("uploadTexture invalid arguments");
        return false;
    }

    const VkDeviceSize expected = static_cast<VkDeviceSize>(width) * static_cast<VkDeviceSize>(height) * 4;
    if (size < expected) {
        LOGE("uploadTexture size too small: got %zu, need at least %llu", size, static_cast<unsigned long long>(expected));
        return false;
    }

    auto findMemoryType = [&](uint32_t typeBits, VkMemoryPropertyFlags flags) -> std::optional<uint32_t> {
        VkPhysicalDeviceMemoryProperties props{};
        vkGetPhysicalDeviceMemoryProperties(physicalDevice_, &props);
        for (uint32_t i = 0; i < props.memoryTypeCount; ++i) {
            if ((typeBits & (1u << i)) && (props.memoryTypes[i].propertyFlags & flags) == flags) {
                return i;
            }
        }
        return std::nullopt;
    };

    VkDeviceSize imageSize = static_cast<VkDeviceSize>(size);

    // Recreate staging buffer if needed
    if (staging_.buffer == VK_NULL_HANDLE || staging_.size < imageSize) {
        if (staging_.buffer != VK_NULL_HANDLE) vkDestroyBuffer(device_, staging_.buffer, nullptr);
        if (staging_.memory != VK_NULL_HANDLE) vkFreeMemory(device_, staging_.memory, nullptr);
        staging_.buffer = VK_NULL_HANDLE;
        staging_.memory = VK_NULL_HANDLE;

        auto bi = makeStruct<VkBufferCreateInfo>(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bi.size = imageSize;
        bi.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
        bi.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateBuffer(device_, &bi, nullptr, &staging_.buffer) != VK_SUCCESS) {
            LOGE("vkCreateBuffer staging failed");
            return false;
        }
        VkMemoryRequirements memReq{};
        vkGetBufferMemoryRequirements(device_, staging_.buffer, &memReq);
        auto typeIndex = findMemoryType(memReq.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        if (!typeIndex) {
            LOGE("No suitable memory type for staging buffer");
            return false;
        }
        auto ai = makeStruct<VkMemoryAllocateInfo>(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        ai.allocationSize = memReq.size;
        ai.memoryTypeIndex = *typeIndex;
        if (vkAllocateMemory(device_, &ai, nullptr, &staging_.memory) != VK_SUCCESS) {
            LOGE("vkAllocateMemory staging failed");
            return false;
        }
        vkBindBufferMemory(device_, staging_.buffer, staging_.memory, 0);
        staging_.size = memReq.size;
    }

    // Upload data to staging
    void* mapped = nullptr;
    if (vkMapMemory(device_, staging_.memory, 0, imageSize, 0, &mapped) != VK_SUCCESS) {
        LOGE("vkMapMemory staging failed");
        return false;
    }
    memcpy(mapped, data, size);
    vkUnmapMemory(device_, staging_.memory);

    // Recreate texture image if dimensions change
    if (textureImage_ != VK_NULL_HANDLE) {
        vkDestroyImageView(device_, textureView_, nullptr);
        textureView_ = VK_NULL_HANDLE;
        vkDestroyImage(device_, textureImage_, nullptr);
        textureImage_ = VK_NULL_HANDLE;
        vkFreeMemory(device_, textureMemory_, nullptr);
        textureMemory_ = VK_NULL_HANDLE;
    }

    auto ci = makeStruct<VkImageCreateInfo>(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
    ci.imageType = VK_IMAGE_TYPE_2D;
    ci.extent = { width, height, 1 };
    ci.mipLevels = 1;
    ci.arrayLayers = 1;
    ci.format = VK_FORMAT_R8G8B8A8_UNORM;
    ci.tiling = VK_IMAGE_TILING_OPTIMAL;
    ci.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    ci.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
    ci.samples = VK_SAMPLE_COUNT_1_BIT;
    ci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateImage(device_, &ci, nullptr, &textureImage_) != VK_SUCCESS) {
        LOGE("vkCreateImage texture failed");
        return false;
    }
    VkMemoryRequirements imgReq{};
    vkGetImageMemoryRequirements(device_, textureImage_, &imgReq);
    auto imgType = findMemoryType(imgReq.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (!imgType) {
        LOGE("No suitable memory type for texture image");
        return false;
    }
    auto imgAlloc = makeStruct<VkMemoryAllocateInfo>(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
    imgAlloc.allocationSize = imgReq.size;
    imgAlloc.memoryTypeIndex = *imgType;
    if (vkAllocateMemory(device_, &imgAlloc, nullptr, &textureMemory_) != VK_SUCCESS) {
        LOGE("vkAllocateMemory texture failed");
        return false;
    }
    vkBindImageMemory(device_, textureImage_, textureMemory_, 0);

    // One-time command buffer for copy and layout transitions
    auto cbai = makeStruct<VkCommandBufferAllocateInfo>(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
    cbai.commandPool = commandPool_;
    cbai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbai.commandBufferCount = 1;
    VkCommandBuffer cmd;
    if (vkAllocateCommandBuffers(device_, &cbai, &cmd) != VK_SUCCESS) {
        LOGE("vkAllocateCommandBuffers for upload failed");
        return false;
    }

    auto beginInfo = makeStruct<VkCommandBufferBeginInfo>(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmd, &beginInfo);

    auto toTransfer = makeStruct<VkImageMemoryBarrier>(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
    toTransfer.srcAccessMask = 0;
    toTransfer.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toTransfer.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    toTransfer.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toTransfer.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toTransfer.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toTransfer.image = textureImage_;
    toTransfer.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    toTransfer.subresourceRange.levelCount = 1;
    toTransfer.subresourceRange.layerCount = 1;

    vkCmdPipelineBarrier(cmd,
                         VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                         VK_PIPELINE_STAGE_TRANSFER_BIT,
                         0,
                         0, nullptr,
                         0, nullptr,
                         1, &toTransfer);

    VkBufferImageCopy copy{};
    copy.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    copy.imageSubresource.layerCount = 1;
    copy.imageExtent = { width, height, 1 };
    vkCmdCopyBufferToImage(cmd, staging_.buffer, textureImage_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &copy);

    auto toShader = makeStruct<VkImageMemoryBarrier>(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
    toShader.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toShader.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    toShader.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toShader.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    toShader.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toShader.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toShader.image = textureImage_;
    toShader.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    toShader.subresourceRange.levelCount = 1;
    toShader.subresourceRange.layerCount = 1;

    vkCmdPipelineBarrier(cmd,
                         VK_PIPELINE_STAGE_TRANSFER_BIT,
                         VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                         0,
                         0, nullptr,
                         0, nullptr,
                         1, &toShader);

    vkEndCommandBuffer(cmd);

    auto si = makeStruct<VkSubmitInfo>(VK_STRUCTURE_TYPE_SUBMIT_INFO);
    si.commandBufferCount = 1;
    si.pCommandBuffers = &cmd;
    vkQueueSubmit(graphicsQueue_, 1, &si, VK_NULL_HANDLE);
    vkQueueWaitIdle(graphicsQueue_);
    vkFreeCommandBuffers(device_, commandPool_, 1, &cmd);

    // Recreate image view
    auto vi = makeStruct<VkImageViewCreateInfo>(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
    vi.image = textureImage_;
    vi.viewType = VK_IMAGE_VIEW_TYPE_2D;
    vi.format = VK_FORMAT_R8G8B8A8_UNORM;
    vi.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    vi.subresourceRange.levelCount = 1;
    vi.subresourceRange.layerCount = 1;
    if (vkCreateImageView(device_, &vi, nullptr, &textureView_) != VK_SUCCESS) {
        LOGE("vkCreateImageView texture failed");
        return false;
    }

    // Update descriptors to point to new view
    for (size_t i = 0; i < descriptorSets_.size(); ++i) {
        VkDescriptorImageInfo ii{};
        ii.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        ii.imageView = textureView_;
        ii.sampler = textureSampler_;

        auto write = makeStruct<VkWriteDescriptorSet>(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        write.dstSet = descriptorSets_[i];
        write.dstBinding = 0;
        write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        write.descriptorCount = 1;
        write.pImageInfo = &ii;
        vkUpdateDescriptorSets(device_, 1, &write, 0, nullptr);
    }

    return true;
}

bool VulkanRenderer::createSyncObjects() {
    const size_t frames = swapchain_.images.size();
    swapchain_.imageAvailable.resize(frames);
    swapchain_.renderFinished.resize(frames);
    swapchain_.inFlightFences.resize(frames);

    auto si = makeStruct<VkSemaphoreCreateInfo>(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
    auto fi = makeStruct<VkFenceCreateInfo>(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
    fi.flags = VK_FENCE_CREATE_SIGNALED_BIT;

    for (size_t i = 0; i < frames; ++i) {
        if (vkCreateSemaphore(device_, &si, nullptr, &swapchain_.imageAvailable[i]) != VK_SUCCESS ||
            vkCreateSemaphore(device_, &si, nullptr, &swapchain_.renderFinished[i]) != VK_SUCCESS ||
            vkCreateFence(device_, &fi, nullptr, &swapchain_.inFlightFences[i]) != VK_SUCCESS) {
            LOGE("Failed to create sync objects for frame %zu", i);
            return false;
        }
    }
    return true;
}

bool VulkanRenderer::recordCommandBuffers() {
    for (size_t i = 0; i < swapchain_.images.size(); ++i) {
        if (!recordCommandBuffer(static_cast<uint32_t>(i))) return false;
    }
    return true;
}

void VulkanRenderer::cleanupSwapchain() {
    for (auto fb : framebuffers_) if (fb) vkDestroyFramebuffer(device_, fb, nullptr);
    framebuffers_.clear();
    for (auto f : swapchain_.inFlightFences) if (f) vkDestroyFence(device_, f, nullptr);
    for (auto s : swapchain_.imageAvailable) if (s) vkDestroySemaphore(device_, s, nullptr);
    for (auto s : swapchain_.renderFinished) if (s) vkDestroySemaphore(device_, s, nullptr);
    swapchain_.inFlightFences.clear();
    swapchain_.imageAvailable.clear();
    swapchain_.renderFinished.clear();

    if (commandPool_ != VK_NULL_HANDLE && !swapchain_.commandBuffers.empty()) {
        vkFreeCommandBuffers(device_, commandPool_, static_cast<uint32_t>(swapchain_.commandBuffers.size()), swapchain_.commandBuffers.data());
    }
    swapchain_.commandBuffers.clear();
    for (auto v : swapchain_.imageViews) if (v) vkDestroyImageView(device_, v, nullptr);
    swapchain_.imageViews.clear();
    swapchain_.images.clear();

    if (swapchain_.swapchain != VK_NULL_HANDLE) {
        vkDestroySwapchainKHR(device_, swapchain_.swapchain, nullptr);
        swapchain_.swapchain = VK_NULL_HANDLE;
    }
}
