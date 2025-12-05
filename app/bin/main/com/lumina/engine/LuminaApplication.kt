package com.lumina.engine

import android.app.Application
import android.content.res.AssetManager
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Lumina Application - Global application state and initialization
 */
class LuminaApplication : Application() {

    companion object {
        private const val TAG = "LuminaApp"
        
        lateinit var instance: LuminaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "Lumina Virtual Studio initializing...")
        
        // Initialize Python runtime (Chaquopy)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
            Log.i(TAG, "Python runtime started")
        }
    }
}
