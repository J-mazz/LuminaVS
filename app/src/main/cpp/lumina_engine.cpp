#include "lumina_engine.h"

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <algorithm>

#include "json_parser.h"
#include "renderer_gles.h"

#define LOG_TAG "LuminaEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

JavaVM* g_vm = nullptr;

LuminaEngineCore& LuminaEngineCore::getInstance() {
    static LuminaEngineCore instance;
    return instance;
}

LuminaEngineCore::LuminaEngineCore() = default;
LuminaEngineCore::~LuminaEngineCore() { shutdown(); }

bool LuminaEngineCore::initialize(JNIEnv* env, jobject assetManager) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (initialized_) {
        LOGW("Engine already initialized");
        return true;
    }

    LOGI("Initializing Lumina Engine Core v%d.%d.%d", LUMINA_VERSION_MAJOR, LUMINA_VERSION_MINOR, LUMINA_VERSION_PATCH);

    assetManager_ = env->NewGlobalRef(assetManager);
    state_ = std::make_unique<lumina::LuminaState>();

    if (!initializeGraphics()) {
        LOGE("Failed to initialize graphics");
        return false;
    }

    glRenderer_ = std::make_unique<GLRenderer>();
    glRenderer_->initialize();

    initialized_ = true;
    LOGI("Engine initialized successfully");
    return true;
}

void LuminaEngineCore::shutdown(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!initialized_) return;

    LOGI("Shutting down Lumina Engine");

    if (nativeWindow_) {
        ANativeWindow_release(nativeWindow_);
        nativeWindow_ = nullptr;
    }

    if (assetManager_) {
        bool didAttach = false;
        if (!env && g_vm) {
            if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
                if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                    didAttach = true;
                } else {
                    env = nullptr;
                }
            }
        }

        if (env) {
            env->DeleteGlobalRef(assetManager_);
        }
        assetManager_ = nullptr;

        if (didAttach && g_vm) {
            g_vm->DetachCurrentThread();
        }
    }

    shutdownGraphics();
    glRenderer_.reset();
    state_.reset();
    initialized_ = false;
}

bool LuminaEngineCore::updateStateFromJson(const std::string& json) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!initialized_) {
        LOGE("Cannot update state - engine not initialized");
        return false;
    }

    lumina::json::JsonParser parser(json);
    auto rootOpt = parser.parse();
    if (!rootOpt || !rootOpt->isObject()) {
        LOGE("Failed to parse state JSON");
        return false;
    }

    const auto& rootObj = *rootOpt->asObject();

    auto getNumber = [&](const char* key, double fallback) {
        auto it = rootObj.find(key);
        if (it != rootObj.end()) {
            if (auto n = it->second.asNumber()) return *n;
        }
        return fallback;
    };

    auto getObj = [&](const char* key) -> const lumina::json::JsonValue* {
        auto it = rootObj.find(key);
        if (it != rootObj.end() && it->second.isObject()) return &it->second;
        return nullptr;
    };

    auto getArray = [&](const char* key) -> const lumina::json::JsonValue::array_t* {
        auto it = rootObj.find(key);
        if (it != rootObj.end() && it->second.isArray()) return it->second.asArray();
        return nullptr;
    };

    auto clampUInt = [](int value, int minV, int maxV) {
        return static_cast<uint32_t>(std::max(minV, std::min(value, maxV)));
    };

    int width = static_cast<int>(getNumber("width", state_->width));
    int height = static_cast<int>(getNumber("height", state_->height));
    if (width > 0 && height > 0) {
        state_->setDimensions(static_cast<uint32_t>(width), static_cast<uint32_t>(height));
        surfaceWidth_ = width;
        surfaceHeight_ = height;
        if (glRenderer_) glRenderer_->onSurfaceSize(width, height);
    }

    state_->renderMode = static_cast<lumina::RenderMode>(
        clampUInt(static_cast<int>(getNumber("renderMode", static_cast<int>(state_->renderMode))), 0, 4));

    state_->processingState = static_cast<lumina::ProcessingState>(
        clampUInt(static_cast<int>(getNumber("processingState", static_cast<int>(state_->processingState))), 0, 3));

    uint32_t requestedEffectCount = clampUInt(static_cast<int>(getNumber("activeEffectCount", state_->activeEffectCount)), 0, 4);
    uint32_t parsedEffects = 0;

    if (auto touchObj = getObj("touchPosition")) {
        const auto* o = touchObj->asObject();
        if (o) {
            float x = static_cast<float>(lumina::json::getNumberField(*o, "x", 0.0));
            float y = static_cast<float>(lumina::json::getNumberField(*o, "y", 0.0));
            state_->touchPosition = lumina::Vec2(x, y);
        }
    }

    if (auto deltaObj = getObj("touchDelta")) {
        const auto* o = deltaObj->asObject();
        if (o) {
            float x = static_cast<float>(lumina::json::getNumberField(*o, "x", 0.0));
            float y = static_cast<float>(lumina::json::getNumberField(*o, "y", 0.0));
            state_->touchDelta = lumina::Vec2(x, y);
        }
    }

    state_->touchPressure = static_cast<float>(getNumber("touchPressure", state_->touchPressure));
    state_->touchState = clampUInt(static_cast<int>(getNumber("touchState", state_->touchState)), 0, 3);

    if (auto effects = getArray("effects")) {
        for (size_t i = 0; i < effects->size() && i < 4; ++i) {
            const auto& val = (*effects)[i];
            if (!val.isObject()) continue;
            const auto* obj = val.asObject();
            lumina::EffectParams params;
            params.type = static_cast<lumina::EffectType>(
                clampUInt(static_cast<int>(lumina::json::getNumberField(*obj, "type", static_cast<int>(params.type))), 0, 7));
            params.intensity = static_cast<float>(lumina::json::getNumberField(*obj, "intensity", params.intensity));
            params.param1 = static_cast<float>(lumina::json::getNumberField(*obj, "param1", params.param1));
            params.param2 = static_cast<float>(lumina::json::getNumberField(*obj, "param2", params.param2));
            params.tintColor = lumina::json::parseColor(*obj, "tintColor", params.tintColor);
            params.center = lumina::json::parseVec2(*obj, "center", params.center);
            params.scale = lumina::json::parseVec2(*obj, "scale", params.scale);
            state_->effects[i] = params;
            parsedEffects++;
        }
    }

    state_->activeEffectCount = std::min(requestedEffectCount, parsedEffects);

    if (auto uiObj = getObj("uiStyle")) {
        if (const auto* o = uiObj->asObject()) {
            state_->uiStyle.backgroundColor = lumina::json::parseColor(*o, "backgroundColor", state_->uiStyle.backgroundColor);
            state_->uiStyle.borderColor = lumina::json::parseColor(*o, "borderColor", state_->uiStyle.borderColor);
            state_->uiStyle.blurRadius = static_cast<float>(lumina::json::getNumberField(*o, "blurRadius", state_->uiStyle.blurRadius));
            state_->uiStyle.transparency = static_cast<float>(lumina::json::getNumberField(*o, "transparency", state_->uiStyle.transparency));
            state_->uiStyle.borderWidth = static_cast<float>(lumina::json::getNumberField(*o, "borderWidth", state_->uiStyle.borderWidth));
            state_->uiStyle.cornerRadius = static_cast<float>(lumina::json::getNumberField(*o, "cornerRadius", state_->uiStyle.cornerRadius));
            state_->uiStyle.saturation = static_cast<float>(lumina::json::getNumberField(*o, "saturation", state_->uiStyle.saturation));
            state_->uiStyle.brightness = static_cast<float>(lumina::json::getNumberField(*o, "brightness", state_->uiStyle.brightness));
        }
    }

    if (auto camObj = getObj("camera")) {
        if (const auto* o = camObj->asObject()) {
            state_->camera.position = lumina::json::parseVec3(*o, "position", state_->camera.position);
            state_->camera.lookAt = lumina::json::parseVec3(*o, "lookAt", state_->camera.lookAt);
            state_->camera.fov = static_cast<float>(lumina::json::getNumberField(*o, "fov", state_->camera.fov));
            state_->camera.nearPlane = static_cast<float>(lumina::json::getNumberField(*o, "nearPlane", state_->camera.nearPlane));
            state_->camera.farPlane = static_cast<float>(lumina::json::getNumberField(*o, "farPlane", state_->camera.farPlane));
        }
    }

    state_->incrementStateId();
    LOGD("State updated: renderMode=%d, size=%ux%u, effects=%u", static_cast<int>(state_->renderMode), state_->width, state_->height, state_->activeEffectCount);
    return true;
}

void LuminaEngineCore::setRenderMode(int mode) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_) return;
    state_->renderMode = static_cast<lumina::RenderMode>(mode);
    LOGI("Render mode set to: %d", mode);
}

void LuminaEngineCore::setSurfaceWindow(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (nativeWindow_) {
        ANativeWindow_release(nativeWindow_);
    }

    nativeWindow_ = window;

    if (window) {
        int width = ANativeWindow_getWidth(window);
        int height = ANativeWindow_getHeight(window);
        state_->setDimensions(width, height);
        surfaceWidth_ = width;
        surfaceHeight_ = height;
        LOGI("Surface set: %dx%d", width, height);
        recreateWindowSurface();
    } else {
        if (glRenderer_) glRenderer_->onContextLost();
        if (eglSurface_ != EGL_NO_SURFACE && eglDisplay_ != EGL_NO_DISPLAY) {
            eglDestroySurface(eglDisplay_, eglSurface_);
            eglSurface_ = EGL_NO_SURFACE;
        }
        surfaceWidth_ = surfaceHeight_ = 0;
    }
}

void LuminaEngineCore::renderFrame() {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!initialized_ || !nativeWindow_) return;

    if (!useVulkan_) {
        if (eglSurface_ == EGL_NO_SURFACE) {
            if (!recreateWindowSurface()) return;
        }
        if (!eglMakeCurrent(eglDisplay_, eglSurface_, eglSurface_, eglContext_)) {
            EGLint err = eglGetError();
            LOGE("eglMakeCurrent failed: 0x%x", err);
            if (err == EGL_CONTEXT_LOST || err == EGL_BAD_CONTEXT) {
                if (!recoverEglContext()) return;
            } else {
                return;
            }
        }
    }

    updateFrameTiming();
    performRender();

    if (!useVulkan_) {
        if (!eglSwapBuffers(eglDisplay_, eglSurface_)) {
            EGLint err = eglGetError();
            LOGE("eglSwapBuffers failed: 0x%x", err);
            if (err == EGL_BAD_SURFACE || err == EGL_CONTEXT_LOST) {
                recoverEglContext();
            }
        }
    }
}

lumina::FrameTiming LuminaEngineCore::getFrameTiming() const {
    return state_ ? state_->timing : lumina::FrameTiming();
}

const lumina::LuminaState* LuminaEngineCore::getState() const {
    return state_.get();
}

GLuint LuminaEngineCore::getVideoTextureId() const {
    return glRenderer_ ? glRenderer_->getInputTextureId() : 0;
}

bool LuminaEngineCore::initializeGraphics() {
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

bool LuminaEngineCore::initializeVulkan() {
    LOGD("Vulkan init stub");
    return false;
}

bool LuminaEngineCore::initializeGLES() {
    LOGI("Initializing EGL/GLES3");

    eglDisplay_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay_ == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return false;
    }

    if (!eglInitialize(eglDisplay_, nullptr, nullptr)) {
        LOGE("eglInitialize failed: 0x%x", eglGetError());
        return false;
    }

    const EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE
    };

    EGLint numConfigs = 0;
    if (!eglChooseConfig(eglDisplay_, attribs, &eglConfig_, 1, &numConfigs) || numConfigs < 1) {
        LOGE("eglChooseConfig failed: 0x%x", eglGetError());
        return false;
    }

    const EGLint contextAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    eglContext_ = eglCreateContext(eglDisplay_, eglConfig_, EGL_NO_CONTEXT, contextAttribs);
    if (eglContext_ == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%x", eglGetError());
        return false;
    }

    // Create a tiny pbuffer so we can bind a context before a window surface exists
    const EGLint pbufferAttribs[] = {
        EGL_WIDTH, 1,
        EGL_HEIGHT, 1,
        EGL_NONE
    };
    eglSurface_ = eglCreatePbufferSurface(eglDisplay_, eglConfig_, pbufferAttribs);
    if (eglSurface_ == EGL_NO_SURFACE) {
        LOGE("eglCreatePbufferSurface failed: 0x%x", eglGetError());
        return false;
    }

    if (!eglMakeCurrent(eglDisplay_, eglSurface_, eglSurface_, eglContext_)) {
        LOGE("eglMakeCurrent (pbuffer) failed: 0x%x", eglGetError());
        return false;
    }

    surfaceWidth_ = 1;
    surfaceHeight_ = 1;
    eglSwapInterval(eglDisplay_, 1);
    return true;
}

void LuminaEngineCore::shutdownGraphics() {
    if (useVulkan_) {
        // Vulkan cleanup
    } else {
        if (glRenderer_) glRenderer_->destroy();
        if (eglDisplay_ != EGL_NO_DISPLAY) {
            if (eglSurface_ != EGL_NO_SURFACE) {
                eglDestroySurface(eglDisplay_, eglSurface_);
                eglSurface_ = EGL_NO_SURFACE;
            }
            if (eglContext_ != EGL_NO_CONTEXT) {
                eglDestroyContext(eglDisplay_, eglContext_);
                eglContext_ = EGL_NO_CONTEXT;
            }
            eglTerminate(eglDisplay_);
            eglDisplay_ = EGL_NO_DISPLAY;
        }
    }
}

bool LuminaEngineCore::recreateWindowSurface() {
    if (useVulkan_) return true;

    if (eglDisplay_ == EGL_NO_DISPLAY || eglContext_ == EGL_NO_CONTEXT) {
        LOGW("EGL not initialized; cannot create window surface");
        return false;
    }

    if (eglSurface_ != EGL_NO_SURFACE) {
        eglDestroySurface(eglDisplay_, eglSurface_);
        eglSurface_ = EGL_NO_SURFACE;
    }

    if (!nativeWindow_) {
        LOGW("No native window to create surface");
        return false;
    }

    eglSurface_ = eglCreateWindowSurface(eglDisplay_, eglConfig_, nativeWindow_, nullptr);
    if (eglSurface_ == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        return false;
    }

    if (!eglMakeCurrent(eglDisplay_, eglSurface_, eglSurface_, eglContext_)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        return false;
    }

    surfaceWidth_ = ANativeWindow_getWidth(nativeWindow_);
    surfaceHeight_ = ANativeWindow_getHeight(nativeWindow_);
    if (glRenderer_) glRenderer_->onSurfaceSize(surfaceWidth_, surfaceHeight_);

    LOGI("EGL surface ready: %dx%d", surfaceWidth_, surfaceHeight_);
    return true;
}

bool LuminaEngineCore::recoverEglContext() {
    LOGW("Recovering EGL context");

    if (glRenderer_) glRenderer_->onContextLost();

    if (eglSurface_ != EGL_NO_SURFACE) {
        eglDestroySurface(eglDisplay_, eglSurface_);
        eglSurface_ = EGL_NO_SURFACE;
    }
    if (eglContext_ != EGL_NO_CONTEXT) {
        eglDestroyContext(eglDisplay_, eglContext_);
        eglContext_ = EGL_NO_CONTEXT;
    }

    if (eglDisplay_ == EGL_NO_DISPLAY) {
        eglDisplay_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (eglDisplay_ == EGL_NO_DISPLAY) return false;
        if (!eglInitialize(eglDisplay_, nullptr, nullptr)) return false;
    }

    if (!eglConfig_) {
        const EGLint attribs[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_BLUE_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_RED_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 24,
            EGL_STENCIL_SIZE, 8,
            EGL_NONE
        };
        EGLint numConfigs = 0;
        if (!eglChooseConfig(eglDisplay_, attribs, &eglConfig_, 1, &numConfigs) || numConfigs < 1) {
            LOGE("eglChooseConfig (recover) failed: 0x%x", eglGetError());
            return false;
        }
    }

    const EGLint contextAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    eglContext_ = eglCreateContext(eglDisplay_, eglConfig_, EGL_NO_CONTEXT, contextAttribs);
    if (eglContext_ == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext (recover) failed: 0x%x", eglGetError());
        return false;
    }

    if (glRenderer_) glRenderer_->initialize();
    return recreateWindowSurface();
}

void LuminaEngineCore::updateFrameTiming() {
    auto now = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration<float>(now - lastFrameTime_);

    state_->timing.deltaTime = duration.count();
    state_->timing.totalTime += state_->timing.deltaTime;
    state_->timing.frameCount++;

    if (state_->timing.deltaTime > 0) {
        float instantFps = 1.0f / state_->timing.deltaTime;
        state_->timing.fps = state_->timing.fps * 0.9f + instantFps * 0.1f;
    }

    lastFrameTime_ = now;
}

void LuminaEngineCore::performRender() {
    if (useVulkan_) {
        // Vulkan render path placeholder
        return;
    }
    if (glRenderer_) {
        glRenderer_->render(*state_);
    }
}
