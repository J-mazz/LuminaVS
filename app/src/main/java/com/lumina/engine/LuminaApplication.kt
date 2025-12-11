package com.lumina.engine

import android.app.Application
import android.content.res.AssetManager
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

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

        // Schedule a background model download immediately on first run.
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .build()
        WorkManager.getInstance(this)
            .enqueueUniqueWork("lumina_model_download", ExistingWorkPolicy.KEEP, request)
    }
}
