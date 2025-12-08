package com.lumina.engine

import android.content.res.AssetManager
import android.util.Log
import android.view.Surface
import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Engine - Kotlin wrapper for C++ JNI bridge
 */
class NativeEngine : NativeBridge {

    companion object {
        private const val TAG = "NativeEngine"

        init {
            try {
                System.loadLibrary("lumina_engine")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
    }

    private val isInitialized = AtomicBoolean(false)
    private val gson = Gson()

    // Native methods
    private external fun nativeInit(assetManager: AssetManager): Boolean
    private external fun nativeShutdown()
    private external fun nativeUpdateState(jsonState: String): Boolean
    private external fun nativeSetRenderMode(mode: Int)
    private external fun nativeSetSurface(surface: Surface?)
    private external fun nativeRenderFrame()
    private external fun nativeGetFrameTimingJson(): String
    private external fun nativeGetVersion(): String
    private external fun nativeGetVideoTextureId(): Int

    override fun initialize(): Boolean {
        if (isInitialized.get()) {
            Log.w(TAG, "Engine already initialized")
            return true
        }

        synchronized(this) {
            if (isInitialized.get()) return true

            return try {
                val assetManager = LuminaApplication.instance.assets
                val initialized = nativeInit(assetManager)
                isInitialized.set(initialized)
                if (initialized) {
                    Log.i(TAG, "Engine initialized, version: ${nativeGetVersion()}")
                }
                initialized
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize engine: ${e.message}")
                false
            }
        }
    }

    override fun updateState(jsonState: String) {
        if (!isInitialized.get()) {
            Log.w(TAG, "Cannot update state - not initialized")
            return
        }
        nativeUpdateState(jsonState)
    }

    override fun setRenderMode(mode: Int) {
        if (!isInitialized.get()) return
        nativeSetRenderMode(mode)
    }

    override fun getFrameTiming(): FrameTiming {
        if (!isInitialized.get()) return FrameTiming()
        
        return try {
            val json = nativeGetFrameTimingJson()
            gson.fromJson(json, FrameTiming::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse frame timing: ${e.message}")
            FrameTiming()
        }
    }

    override fun shutdown() {
        if (!isInitialized.getAndSet(false)) return

        Log.i(TAG, "Shutting down engine")
        nativeShutdown()
    }

    fun setSurface(surface: Surface?) {
        if (!isInitialized.get()) return
        nativeSetSurface(surface)
    }

    fun renderFrame() {
        if (!isInitialized.get()) return
        nativeRenderFrame()
    }

    fun getVersion(): String {
        return if (isInitialized.get()) nativeGetVersion() else "N/A"
    }

    fun getVideoTextureId(): Int {
        return if (isInitialized.get()) nativeGetVideoTextureId() else 0
    }
}
