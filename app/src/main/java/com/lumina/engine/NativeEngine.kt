package com.lumina.engine

import android.content.res.AssetManager
import android.util.Log
import android.view.Surface
import com.google.gson.Gson

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

    private var isInitialized = false
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

    override fun initialize(): Boolean {
        if (isInitialized) {
            Log.w(TAG, "Engine already initialized")
            return true
        }

        return try {
            val assetManager = LuminaApplication.instance.assets
            isInitialized = nativeInit(assetManager)
            if (isInitialized) {
                Log.i(TAG, "Engine initialized, version: ${nativeGetVersion()}")
            }
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine: ${e.message}")
            false
        }
    }

    override fun updateState(jsonState: String) {
        if (!isInitialized) {
            Log.w(TAG, "Cannot update state - not initialized")
            return
        }
        nativeUpdateState(jsonState)
    }

    override fun setRenderMode(mode: Int) {
        if (!isInitialized) return
        nativeSetRenderMode(mode)
    }

    override fun getFrameTiming(): FrameTiming {
        if (!isInitialized) return FrameTiming()
        
        return try {
            val json = nativeGetFrameTimingJson()
            gson.fromJson(json, FrameTiming::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse frame timing: ${e.message}")
            FrameTiming()
        }
    }

    override fun shutdown() {
        if (!isInitialized) return
        
        Log.i(TAG, "Shutting down engine")
        nativeShutdown()
        isInitialized = false
    }

    fun setSurface(surface: Surface?) {
        if (!isInitialized) return
        nativeSetSurface(surface)
    }

    fun renderFrame() {
        if (!isInitialized) return
        nativeRenderFrame()
    }

    fun getVersion(): String {
        return if (isInitialized) nativeGetVersion() else "N/A"
    }
}
