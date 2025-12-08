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

// Embedded SPIR-V: simple full-screen quad and textured fragment with basic effects.
const std::array<uint32_t, 359> VulkanRenderer::kVertSpv = {
    119734787, 65536, 851978, 50, 0, 131089, 1, 393227, 1, 1280527431, 1685353262, 808793134, 0, 196622, 0, 1, 524303, 0, 4, 1852399981, 0, 13, 27, 41, 196611, 2, 450, 655364, 1197427783, 1279741775, 1885560645, 1953718128, 1600482425, 1701734764, 1919509599, 1769235301, 25974, 524292, 1197427783, 1279741775, 1852399429, 1685417059, 1768185701, 1952671090, 6649449, 262149, 4, 1852399981, 0, 393221, 11, 1348430951, 1700164197, 2019914866, 0, 393222, 11, 0, 1348430951, 1953067887, 7237481, 458758, 11, 1, 1348430951, 1953393007, 1702521171, 0, 458758, 11, 2, 1130327143, 1148217708, 1635021673, 6644590, 458758, 11, 3, 1130327143, 1147956341, 1635021673, 6644590, 196613, 13, 0, 393221, 27, 1449094247, 1702130277, 1684949368, 30821, 327685, 30, 1701080681, 1818386808, 101, 196613, 41, 5657974, 327685, 47, 1701080681, 1818386808, 101, 327752, 11, 0, 11, 0, 327752, 11, 1, 11, 1, 327752, 11, 2, 11, 3, 327752, 11, 3, 11, 4, 196679, 11, 2, 262215, 27, 11, 42, 262215, 41, 30, 0, 131091, 2, 196641, 3, 2, 196630, 6, 32, 262167, 7, 6, 4, 262165, 8, 32, 0, 262187, 8, 9, 1, 262172, 10, 6, 9, 393246, 11, 7, 6, 10, 10, 262176, 12, 3, 11, 262203, 12, 13, 3, 262165, 14, 32, 1, 262187, 14, 15, 0, 262167, 16, 6, 2, 262187, 8, 17, 4, 262172, 18, 16, 17, 262187, 6, 19, 3212836864, 327724, 16, 20, 19, 19, 262187, 6, 21, 1065353216, 327724, 16, 22, 21, 19, 327724, 16, 23, 19, 21, 327724, 16, 24, 21, 21, 458796, 18, 25, 20, 22, 23, 24, 262176, 26, 1, 14, 262203, 26, 27, 1, 262176, 29, 7, 18, 262176, 31, 7, 16, 262187, 6, 34, 0, 262176, 38, 3, 7, 262176, 40, 3, 16, 262203, 40, 41, 3, 327724, 16, 42, 34, 21, 327724, 16, 43, 34, 34, 327724, 16, 44, 21, 34, 458796, 18, 45, 42, 24, 43, 44, 327734, 2, 4, 0, 3, 131320, 5, 262203, 29, 30, 7, 262203, 29, 47, 7, 262205, 14, 28, 27, 196670, 30, 25, 327745, 31, 32, 30, 28, 262205, 16, 33, 32, 327761, 6, 35, 33, 0, 327761, 6, 36, 33, 1, 458832, 7, 37, 35, 36, 34, 21, 327745, 38, 39, 13, 15, 196670, 39, 37, 262205, 14, 46, 27, 196670, 47, 45, 327745, 31, 48, 47, 46, 262205, 16, 49, 48, 196670, 41, 49, 65789, 65592
};

const std::array<uint32_t, 819> VulkanRenderer::kFragSpv = {
    119734787, 65536, 851978, 124, 0, 131089, 1, 393227, 1, 1280527431, 1685353262, 808793134, 0, 196622, 0, 1, 458767, 4, 4, 1852399981, 0, 100, 120, 196624, 4, 7, 196611, 2, 450, 655364, 1197427783, 1279741775, 1885560645, 1953718128, 1600482425, 1701734764, 1919509599, 1769235301, 25974, 524292, 1197427783, 1279741775, 1852399429, 1685417059, 1768185701, 1952671090, 6649449, 262149, 4, 1852399981, 0, 458757, 11, 1819308129, 1717978489, 678716261, 993289846, 0, 196613, 10, 99, 262149, 15, 1701209669, 7566435, 327686, 15, 0, 1701669236, 0, 393222, 15, 1, 1702129257, 1953067886, 121, 393222, 15, 2, 1701209701, 2035577955, 25968, 327686, 15, 3, 811884912, 0, 327686, 15, 4, 1953393012, 0, 327686, 15, 5, 1953391971, 29285, 327686, 15, 6, 1818321779, 101, 327686, 15, 7, 1634886000, 29549, 393222, 15, 8, 1869833586, 1769239916, 28271, 196613, 17, 25456, 262149, 28, 2036429415, 0, 196613, 98, 30325, 196613, 100, 5657974, 196613, 111, 99, 327685, 115, 2019906677, 1701999988, 0, 327685, 120, 1131705711, 1919904879, 0, 262149, 121, 1634886000, 109, 327752, 15, 0, 35, 0, 327752, 15, 1, 35, 4, 327752, 15, 2, 35, 8, 327752, 15, 3, 35, 12, 327752, 15, 4, 35, 16, 327752, 15, 5, 35, 32, 327752, 15, 6, 35, 40, 327752, 15, 7, 35, 48, 327752, 15, 8, 35, 56, 196679, 15, 2, 262215, 100, 30, 0, 262215, 115, 34, 0, 262215, 115, 33, 0, 262215, 120, 30, 0, 131091, 2, 196641, 3, 2, 196630, 6, 32, 262167, 7, 6, 4, 262176, 8, 7, 7, 262177, 9, 7, 8, 262165, 13, 32, 1, 262167, 14, 6, 2, 720926, 15, 6, 6, 13, 6, 7, 14, 14, 14, 14, 262176, 16, 9, 15, 262203, 16, 17, 9, 262187, 13, 18, 2, 262176, 19, 9, 13, 262187, 13, 22, 1, 131092, 23, 262176, 27, 7, 6, 262167, 29, 6, 3, 262187, 6, 32, 1050220167, 262187, 6, 33, 1058424226, 262187, 6, 34, 1038710997, 393260, 29, 35, 32, 33, 34, 262165, 39, 32, 0, 262187, 39, 40, 0, 262187, 39, 43, 1, 262187, 39, 46, 2, 262187, 6, 57, 1065353216, 262176, 62, 9, 6, 262187, 13, 73, 4, 262176, 74, 9, 7, 262187, 39, 87, 3, 262176, 97, 7, 14, 262176, 99, 1, 14, 262203, 99, 100, 1, 262187, 13, 102, 6, 262176, 103, 9, 14, 262187, 13, 107, 5, 589849, 112, 6, 1, 0, 0, 0, 1, 0, 196635, 113, 112, 262176, 114, 0, 113, 262203, 114, 115, 0, 262176, 119, 3, 7, 262203, 119, 120, 3, 327734, 2, 4, 0, 3, 131320, 5, 262203, 97, 98, 7, 262203, 8, 111, 7, 262203, 8, 121, 7, 262205, 14, 101, 100, 327745, 103, 104, 17, 102, 262205, 14, 105, 104, 327813, 14, 106, 101, 105, 327745, 103, 108, 17, 107, 262205, 14, 109, 108, 327809, 14, 110, 106, 109, 196670, 98, 110, 262205, 113, 116, 115, 262205, 14, 117, 98, 327767, 7, 118, 116, 117, 196670, 111, 118, 262205, 7, 122, 111, 196670, 121, 122, 327737, 7, 123, 11, 121, 196670, 120, 123, 65789, 65592, 327734, 7, 11, 0, 9, 196663, 8, 10, 131320, 12, 262203, 27, 28, 7, 327745, 19, 20, 17, 18, 262205, 13, 21, 20, 327850, 23, 24, 21, 22, 196855, 26, 0, 262394, 24, 25, 49, 131320, 25, 262205, 7, 30, 10, 524367, 29, 31, 30, 30, 0, 1, 2, 327828, 6, 36, 31, 35, 196670, 28, 36, 262205, 6, 37, 28, 393296, 29, 38, 37, 37, 37, 327745, 27, 41, 10, 40, 327761, 6, 42, 38, 0, 196670, 41, 42, 327745, 27, 44, 10, 43, 327761, 6, 45, 38, 1, 196670, 44, 45, 327745, 27, 47, 10, 46, 327761, 6, 48, 38, 2, 196670, 47, 48, 131321, 26, 131320, 49, 327745, 19, 50, 17, 18, 262205, 13, 51, 50, 327850, 23, 52, 51, 18, 196855, 54, 0, 262394, 52, 53, 54, 131320, 53, 262205, 7, 55, 10, 524367, 29, 56, 55, 55, 0, 1, 2, 262205, 7, 58, 10, 524367, 29, 59, 58, 58, 0, 1, 2, 393296, 29, 60, 57, 57, 57, 327811, 29, 61, 60, 59, 327745, 62, 63, 17, 22, 262205, 6, 64, 63, 393296, 29, 65, 64, 64, 64, 524300, 29, 66, 1, 46, 56, 61, 65, 327745, 27, 67, 10, 40, 327761, 6, 68, 66, 0, 196670, 67, 68, 327745, 27, 69, 10, 43, 327761, 6, 70, 66, 1, 196670, 69, 70, 327745, 27, 71, 10, 46, 327761, 6, 72, 66, 2, 196670, 71, 72, 131321, 54, 131320, 54, 131321, 26, 131320, 26, 327745, 74, 75, 17, 73, 262205, 7, 76, 75, 524367, 29, 77, 76, 76, 0, 1, 2, 262205, 7, 78, 10, 524367, 29, 79, 78, 78, 0, 1, 2, 327813, 29, 80, 79, 77, 327745, 27, 81, 10, 40, 327761, 6, 82, 80, 0, 196670, 81, 82, 327745, 27, 83, 10, 43, 327761, 6, 84, 80, 1, 196670, 83, 84, 327745, 27, 85, 10, 46, 327761, 6, 86, 80, 2, 196670, 85, 86, 393281, 62, 88, 17, 73, 87, 262205, 6, 89, 88, 327745, 27, 90, 10, 87, 262205, 6, 91, 90, 327813, 6, 92, 91, 89, 327745, 27, 93, 10, 87, 196670, 93, 92, 262205, 7, 94, 10, 131326, 94, 65592
};

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

bool VulkanRenderer::render() {
    if (!initialized_) return false;

    const size_t frameIndex = currentFrame_ % swapchain_.images.size();
    VkSemaphore imageAvailable = swapchain_.imageAvailable[frameIndex];
    VkSemaphore renderFinished = swapchain_.renderFinished[frameIndex];
    VkFence inFlight = swapchain_.inFlightFences[frameIndex];

    vkWaitForFences(device_, 1, &inFlight, VK_TRUE, UINT64_MAX);

    uint32_t imageIndex = 0;
    VkResult acquire = vkAcquireNextImageKHR(device_, swapchain_.swapchain, UINT64_MAX, imageAvailable, VK_NULL_HANDLE, &imageIndex);
    if (acquire == VK_ERROR_OUT_OF_DATE_KHR) {
        LOGW("Swapchain out of date; recreating");
        return recreate(window_);
    } else if (acquire != VK_SUCCESS && acquire != VK_SUBOPTIMAL_KHR) {
        LOGE("Failed to acquire swapchain image: %d", acquire);
        return false;
    }

    vkResetFences(device_, 1, &inFlight);

    // Refresh command buffer with latest effect parameters for this image
    if (!recordCommandBuffer(imageIndex)) {
        return false;
    }

    VkPipelineStageFlags waitStages[] = { VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT };
    auto submitInfo = makeStruct<VkSubmitInfo>(VK_STRUCTURE_TYPE_SUBMIT_INFO);
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = &imageAvailable;
    submitInfo.pWaitDstStageMask = waitStages;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &swapchain_.commandBuffers[imageIndex];
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = &renderFinished;

    VkResult submit = vkQueueSubmit(graphicsQueue_, 1, &submitInfo, inFlight);
    if (submit != VK_SUCCESS) {
        LOGE("vkQueueSubmit failed: %d", submit);
        return false;
    }

    auto presentInfo = makeStruct<VkPresentInfoKHR>(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = &renderFinished;
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = &swapchain_.swapchain;
    presentInfo.pImageIndices = &imageIndex;

    VkResult present = vkQueuePresentKHR(graphicsQueue_, &presentInfo);
    if (present == VK_ERROR_OUT_OF_DATE_KHR || present == VK_SUBOPTIMAL_KHR) {
        LOGW("Swapchain present suboptimal/out-of-date; recreating");
        return recreate(window_);
    } else if (present != VK_SUCCESS) {
        LOGE("vkQueuePresentKHR failed: %d", present);
        return false;
    }

    currentFrame_++;
    return true;
}

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
