#ifndef LUMINA_ENGINE_H
#define LUMINA_ENGINE_H

#include <jni.h>
#include <string>
#include <android/native_window.h>
#include <chrono>
#include <memory>
#include <mutex>
#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include "engine_structs.h"

class GLRenderer;
class VulkanRenderer;

class LuminaEngineCore {
public:
    static LuminaEngineCore& getInstance();

    bool initialize(JNIEnv* env, jobject assetManager);
    void shutdown(JNIEnv* env = nullptr);

    bool updateStateFromJson(const std::string& json);
    void setRenderMode(int mode);
    void setSurfaceWindow(ANativeWindow* window);
    void renderFrame();
    GLuint getVideoTextureId() const;

    // Upload an RGBA8 camera frame (e.g., after AHardwareBuffer readback) into the active renderer.
    void uploadCameraFrame(const uint8_t* data, size_t size, uint32_t width, uint32_t height);

    lumina::FrameTiming getFrameTiming() const;
    const lumina::LuminaState* getState() const;

private:
    LuminaEngineCore();
    ~LuminaEngineCore();

    LuminaEngineCore(const LuminaEngineCore&) = delete;
    LuminaEngineCore& operator=(const LuminaEngineCore&) = delete;

    bool initializeGraphics();
    bool initializeVulkan();
    bool initializeGLES();
    void shutdownGraphics();

    bool recreateWindowSurface();
    bool recoverEglContext();
    void updateFrameTiming();
    void performRender();

    // Members
    std::unique_ptr<lumina::LuminaState> state_;
    bool initialized_ = false;
    bool useVulkan_ = false;

    jobject assetManager_ = nullptr;
    ANativeWindow* nativeWindow_ = nullptr;

    // EGL
    EGLDisplay eglDisplay_ = EGL_NO_DISPLAY;
    EGLConfig eglConfig_ = nullptr;
    EGLContext eglContext_ = EGL_NO_CONTEXT;
    EGLSurface eglSurface_ = EGL_NO_SURFACE;
    int surfaceWidth_ = 0;
    int surfaceHeight_ = 0;

    // Rendering
    std::unique_ptr<class GLRenderer> glRenderer_;
    std::unique_ptr<class VulkanRenderer> vkRenderer_;

    // Timing
    std::chrono::high_resolution_clock::time_point lastFrameTime_ =
        std::chrono::high_resolution_clock::now();

    mutable std::mutex mutex_;
};

#endif // LUMINA_ENGINE_H
