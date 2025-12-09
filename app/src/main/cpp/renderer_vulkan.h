#pragma once

#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include <android/native_window.h>
#include <vector>
#include <optional>
#include <array>

// [FIX] Required for LuminaState definition
#include "engine_structs.h"

class VulkanRenderer {
public:
    bool initialize(ANativeWindow* window);
    void destroy();

    // [FIX] Update signature to accept state for effect processing
    bool render(const lumina::LuminaState& state);
    
    bool recreate(ANativeWindow* window);
    
    // [FIX] Method to receive raw camera frames from Kotlin
    bool uploadTexture(const void* data, size_t size, uint32_t width, uint32_t height);

    // Matches GLSL layout(push_constant) uniform block alignment (std140)
    struct EffectParams {
        float time;
        float intensity;
        int effectType;
        float pad0;      // Padding for 16-byte alignment
        float tint[4];
        float center[2];
        float scale[2];
        float params[2]; // param1, param2
        float resolution[2];
    };

    void setEffectParams(const EffectParams& params);

private:
    struct SwapchainResources {
        VkSwapchainKHR swapchain = VK_NULL_HANDLE;
        std::vector<VkImage> images;
        std::vector<VkImageView> imageViews;
        std::vector<VkCommandBuffer> commandBuffers;
        std::vector<VkSemaphore> imageAvailable;
        std::vector<VkSemaphore> renderFinished;
        std::vector<VkFence> inFlightFences;
        uint32_t width = 0;
        uint32_t height = 0;
        VkFormat format = VK_FORMAT_UNDEFINED;
    };

    bool createInstance();
    bool createSurface(ANativeWindow* window);
    bool pickPhysicalDevice();
    bool createDevice();
    bool createCommandPool();
    bool createSwapchain();
    bool createSyncObjects();
    bool recordCommandBuffers();
    bool createRenderPass();
    bool createDescriptorSetLayout();
    bool createPipelineLayout();
    bool createGraphicsPipeline();
    bool createFramebuffers();
    bool createTextureResources();
    bool createSampler();
    bool createDescriptorPoolAndSets();
    bool recordCommandBuffer(uint32_t imageIndex);
    void cleanupSwapchain();

    uint32_t findGraphicsQueueFamily(VkPhysicalDevice device);

    VkInstance instance_ = VK_NULL_HANDLE;
    VkSurfaceKHR surface_ = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice_ = VK_NULL_HANDLE;
    VkDevice device_ = VK_NULL_HANDLE;
    VkQueue graphicsQueue_ = VK_NULL_HANDLE;
    uint32_t graphicsQueueFamily_ = 0;
    VkCommandPool commandPool_ = VK_NULL_HANDLE;

    VkRenderPass renderPass_ = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout_ = VK_NULL_HANDLE;
    VkPipeline graphicsPipeline_ = VK_NULL_HANDLE;
    std::vector<VkFramebuffer> framebuffers_;

    VkDescriptorSetLayout descriptorSetLayout_ = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool_ = VK_NULL_HANDLE;
    std::vector<VkDescriptorSet> descriptorSets_;

    VkImage textureImage_ = VK_NULL_HANDLE;
    VkDeviceMemory textureMemory_ = VK_NULL_HANDLE;
    VkImageView textureView_ = VK_NULL_HANDLE;
    VkSampler textureSampler_ = VK_NULL_HANDLE;

    struct StagingBuffer {
        VkBuffer buffer = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        VkDeviceSize size = 0;
    } staging_;

    EffectParams effectParams_{};

    SwapchainResources swapchain_{};
    size_t currentFrame_ = 0;
    ANativeWindow* window_ = nullptr;

    bool initialized_ = false;

    // NOTE: The SPIR-V arrays are generated at build time and included via generated/shaders_generated.h
    static const std::vector<uint32_t> kVertSpv;
    static const std::vector<uint32_t> kFragSpv;
};
