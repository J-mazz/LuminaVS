#pragma once

#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include <android/native_window.h>
#include <vector>
#include <optional>
#include <array>

// Minimal Vulkan renderer that clears the swapchain with a solid color.
// Designed as a production-ready starting point; can be extended with pipelines
// for textured rendering and effects.
class VulkanRenderer {
public:
    bool initialize(ANativeWindow* window);
    void destroy();

    // Render one frame; returns false on unrecoverable failure.
    bool render();

    // Recreate swapchain on surface changes (resize/rotation/out-of-date).
    bool recreate(ANativeWindow* window);

    // Upload an RGBA8 texture (e.g., camera frame) into GPU memory.
    bool uploadTexture(const void* data, size_t size, uint32_t width, uint32_t height);

    // Update effect parameters used as push constants by the fragment shader.
    struct EffectParams {
        float time = 0.0f;
        float intensity = 1.0f;
        int effectType = 0;
        float pad0 = 0.0f;
        float tint[4] = {1,1,1,1};
        float center[2] = {0.5f, 0.5f};
        float scale[2] = {1.0f, 1.0f};
        float params[2] = {0.0f, 0.0f};
        float resolution[2] = {1.0f, 1.0f};
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
    ANativeWindow* window_ = nullptr; // Not owned; managed by Java side

    bool initialized_ = false;

    // Embedded SPIR-V binaries (compiled from simple textured quad shaders)
    static const std::array<uint32_t, 359> kVertSpv;
    static const std::array<uint32_t, 819> kFragSpv;
};
