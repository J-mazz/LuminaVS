#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <chrono>
#include <cstdlib>
#include <algorithm>
#include <cctype>
#include <jni.h>
#include <android/native_window_jni.h>
#include <cstdio>
#include <android/log.h>

#include "lumina_engine.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "LuminaJNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LuminaJNI", __VA_ARGS__)

extern JavaVM* g_vm;

extern "C" {


JNIEXPORT void JNICALL
Java_com_lumina_engine_NativeEngine_nativeShutdown(
    JNIEnv* env,
    jobject /* this */
) {
    LuminaEngineCore::getInstance().shutdown(env);
}

JNIEXPORT jboolean JNICALL
Java_com_lumina_engine_NativeEngine_nativeInit(
    JNIEnv* env,
    jobject /* this */,
    jobject assetManager
) {
    bool ok = LuminaEngineCore::getInstance().initialize(env, assetManager);
    return ok ? JNI_TRUE : JNI_FALSE;
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

JNIEXPORT jint JNICALL
Java_com_lumina_engine_NativeEngine_nativeGetVideoTextureId(
    JNIEnv* /* env */,
    jobject /* this */
) {
    return static_cast<jint>(LuminaEngineCore::getInstance().getVideoTextureId());
}

JNIEXPORT void JNICALL
Java_com_lumina_engine_NativeEngine_nativeUploadCameraFrame(
    JNIEnv* env,
    jobject /* this */,
    jobject buffer,
    jint width,
    jint height
) {
    if (!buffer) return;
    void* ptr = env->GetDirectBufferAddress(buffer);
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (!ptr || capacity <= 0) {
        LOGE("nativeUploadCameraFrame: buffer not direct or has no capacity");
        return;
    }
    LuminaEngineCore::getInstance().uploadCameraFrame(static_cast<uint8_t*>(ptr), static_cast<size_t>(capacity), static_cast<uint32_t>(width), static_cast<uint32_t>(height));
}

// JNI_OnLoad - Called when the library is loaded
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    LOGI("Lumina Engine JNI loaded");
    g_vm = vm;
    
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
