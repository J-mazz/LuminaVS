#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include "engine_structs.h"

// Logging macros
#define LOG_TAG "LuminaEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {

/**
 * Lumina Engine Core - Manages render state and GPU resources
 */
class LuminaEngineCore {
public:
    static LuminaEngineCore& getInstance() {
        static LuminaEngineCore instance;
        return instance;
    }

    bool initialize(JNIEnv* env, jobject assetManager) {
        std::lock_guard<std::mutex> lock(mutex_);
        
        if (initialized_) {
            LOGW("Engine already initialized");
            return true;
        }

        LOGI("Initializing Lumina Engine Core v%d.%d.%d",
             LUMINA_VERSION_MAJOR, LUMINA_VERSION_MINOR, LUMINA_VERSION_PATCH);

        // Store asset manager reference
        assetManager_ = env->NewGlobalRef(assetManager);

        // Initialize state
        state_ = std::make_unique<lumina::LuminaState>();
        
        // Initialize graphics (Vulkan or GLES fallback)
        if (!initializeGraphics()) {
            LOGE("Failed to initialize graphics");
            return false;
        }

        initialized_ = true;
        LOGI("Engine initialized successfully");
        return true;
    }

    void shutdown() {
        std::lock_guard<std::mutex> lock(mutex_);
        
        if (!initialized_) return;

        LOGI("Shutting down Lumina Engine");
        
        shutdownGraphics();
        state_.reset();
        initialized_ = false;
    }

    bool updateStateFromJson(const std::string& json) {
        std::lock_guard<std::mutex> lock(mutex_);
        
        if (!initialized_) {
            LOGE("Cannot update state - engine not initialized");
            return false;
        }

        // Parse JSON and update state
        // In production, use a proper JSON library like nlohmann/json or rapidjson
        LOGD("Updating state from JSON (length: %zu)", json.length());
        
        state_->incrementStateId();
        return true;
    }

    void setRenderMode(int mode) {
        std::lock_guard<std::mutex> lock(mutex_);
        
        if (!initialized_) return;
        
        state_->renderMode = static_cast<lumina::RenderMode>(mode);
        LOGI("Render mode set to: %d", mode);
    }

    void setSurfaceWindow(ANativeWindow* window) {
        std::lock_guard<std::mutex> lock(mutex_);
        
        nativeWindow_ = window;
        
        if (window) {
            int width = ANativeWindow_getWidth(window);
            int height = ANativeWindow_getHeight(window);
            state_->setDimensions(width, height);
            LOGI("Surface set: %dx%d", width, height);
        }
    }

    void renderFrame() {
        std::lock_guard<std::mutex> lock(mutex_);
        
        if (!initialized_ || !nativeWindow_) return;

        // Update timing
        updateFrameTiming();
        
        // Render based on current state
        performRender();
    }

    lumina::FrameTiming getFrameTiming() const {
        return state_ ? state_->timing : lumina::FrameTiming();
    }

    const lumina::LuminaState* getState() const {
        return state_.get();
    }

private:
    LuminaEngineCore() = default;
    ~LuminaEngineCore() { shutdown(); }
    
    LuminaEngineCore(const LuminaEngineCore&) = delete;
    LuminaEngineCore& operator=(const LuminaEngineCore&) = delete;

    bool initializeGraphics() {
        LOGI("Initializing graphics subsystem");
        
#ifdef LUMINA_USE_VULKAN
        if (initializeVulkan()) {
            LOGI("Vulkan initialized");
            useVulkan_ = true;
            return true;
        }
        LOGW("Vulkan init failed, falling back to GLES");
#endif

#ifdef LUMINA_USE_GLES3
        if (initializeGLES()) {
            LOGI("GLES 3 initialized");
            useVulkan_ = false;
            return true;
        }
#endif

        LOGE("No graphics API available");
        return false;
    }

    bool initializeVulkan() {
        // Vulkan initialization stub
        // Full implementation would create VkInstance, VkDevice, etc.
        LOGD("Vulkan init stub");
        return false; // Return false to fall back to GLES for now
    }

    bool initializeGLES() {
        // GLES initialization stub
        // Full implementation would create EGLContext, etc.
        LOGD("GLES init stub");
        return true;
    }

    void shutdownGraphics() {
        if (useVulkan_) {
            // Cleanup Vulkan resources
        } else {
            // Cleanup GLES resources
        }
    }

    void updateFrameTiming() {
        // Calculate frame timing
        auto now = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration<float>(now - lastFrameTime_);
        
        state_->timing.deltaTime = duration.count();
        state_->timing.totalTime += state_->timing.deltaTime;
        state_->timing.frameCount++;
        
        // Calculate FPS (rolling average)
        if (state_->timing.deltaTime > 0) {
            float instantFps = 1.0f / state_->timing.deltaTime;
            state_->timing.fps = state_->timing.fps * 0.9f + instantFps * 0.1f;
        }
        
        lastFrameTime_ = now;
    }

    void performRender() {
        // Apply effects based on state
        for (uint32_t i = 0; i < state_->activeEffectCount && i < 4; ++i) {
            applyEffect(state_->effects[i]);
        }
        
        // Render based on mode
        switch (state_->renderMode) {
            case lumina::RenderMode::PASSTHROUGH:
                renderPassthrough();
                break;
            case lumina::RenderMode::STYLIZED:
                renderStylized();
                break;
            case lumina::RenderMode::SEGMENTED:
                renderSegmented();
                break;
            case lumina::RenderMode::DEPTH_MAP:
                renderDepthMap();
                break;
            case lumina::RenderMode::NORMAL_MAP:
                renderNormalMap();
                break;
        }
    }

    void applyEffect(const lumina::EffectParams& effect) {
        // Effect application stub
        LOGD("Applying effect type: %d, intensity: %.2f", 
             static_cast<int>(effect.type), effect.intensity);
    }

    void renderPassthrough() { LOGD("Render: Passthrough"); }
    void renderStylized() { LOGD("Render: Stylized"); }
    void renderSegmented() { LOGD("Render: Segmented"); }
    void renderDepthMap() { LOGD("Render: Depth Map"); }
    void renderNormalMap() { LOGD("Render: Normal Map"); }

    // State
    std::unique_ptr<lumina::LuminaState> state_;
    bool initialized_ = false;
    bool useVulkan_ = false;
    
    // Resources
    jobject assetManager_ = nullptr;
    ANativeWindow* nativeWindow_ = nullptr;
    
    // Timing
    std::chrono::high_resolution_clock::time_point lastFrameTime_ = 
        std::chrono::high_resolution_clock::now();
    
    // Thread safety
    mutable std::mutex mutex_;
};

} // anonymous namespace

// ============================================================================
// JNI Exports
// ============================================================================

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_lumina_engine_NativeEngine_nativeInit(
    JNIEnv* env,
    jobject /* this */,
    jobject assetManager
) {
    return LuminaEngineCore::getInstance().initialize(env, assetManager) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_lumina_engine_NativeEngine_nativeShutdown(
    JNIEnv* /* env */,
    jobject /* this */
) {
    LuminaEngineCore::getInstance().shutdown();
}

JNIEXPORT jboolean JNICALL
Java_com_lumina_engine_NativeEngine_nativeUpdateState(
    JNIEnv* env,
    jobject /* this */,
    jstring jsonState
) {
    const char* json = env->GetStringUTFChars(jsonState, nullptr);
    bool result = LuminaEngineCore::getInstance().updateStateFromJson(json);
    env->ReleaseStringUTFChars(jsonState, json);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_lumina_engine_NativeEngine_nativeSetRenderMode(
    JNIEnv* /* env */,
    jobject /* this */,
    jint mode
) {
    LuminaEngineCore::getInstance().setRenderMode(mode);
}

JNIEXPORT void JNICALL
Java_com_lumina_engine_NativeEngine_nativeSetSurface(
    JNIEnv* env,
    jobject /* this */,
    jobject surface
) {
    ANativeWindow* window = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    LuminaEngineCore::getInstance().setSurfaceWindow(window);
}

JNIEXPORT void JNICALL
Java_com_lumina_engine_NativeEngine_nativeRenderFrame(
    JNIEnv* /* env */,
    jobject /* this */
) {
    LuminaEngineCore::getInstance().renderFrame();
}

JNIEXPORT jstring JNICALL
Java_com_lumina_engine_NativeEngine_nativeGetFrameTimingJson(
    JNIEnv* env,
    jobject /* this */
) {
    auto timing = LuminaEngineCore::getInstance().getFrameTiming();
    
    // Build JSON manually (replace with proper JSON library in production)
    char buffer[256];
    snprintf(buffer, sizeof(buffer),
        R"({"deltaTime":%.6f,"totalTime":%.2f,"frameCount":%llu,"fps":%.1f,"gpuTime":%.3f,"cpuTime":%.3f})",
        timing.deltaTime,
        timing.totalTime,
        static_cast<unsigned long long>(timing.frameCount),
        timing.fps,
        timing.gpuTime,
        timing.cpuTime
    );
    
    return env->NewStringUTF(buffer);
}

JNIEXPORT jstring JNICALL
Java_com_lumina_engine_NativeEngine_nativeGetVersion(
    JNIEnv* env,
    jobject /* this */
) {
    char version[32];
    snprintf(version, sizeof(version), "%d.%d.%d",
        LUMINA_VERSION_MAJOR, LUMINA_VERSION_MINOR, LUMINA_VERSION_PATCH);
    return env->NewStringUTF(version);
}

// JNI_OnLoad - Called when the library is loaded
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    LOGI("Lumina Engine JNI loaded");
    
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Failed to get JNI environment");
        return JNI_ERR;
    }
    
    return JNI_VERSION_1_6;
}

// JNI_OnUnload - Called when the library is unloaded
JNIEXPORT void JNI_OnUnload(JavaVM* /* vm */, void* /* reserved */) {
    LOGI("Lumina Engine JNI unloading");
    LuminaEngineCore::getInstance().shutdown();
}

} // extern "C"
