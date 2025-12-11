package com.lumina.engine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker to ensure model is downloaded. This allows the model to be downloaded even when
 * the app is not in the foreground right after install (subject to WorkManager constraints).
 */
class ModelDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val md = ModelDownloader(applicationContext)
            val ok = md.ensureModelAvailable()
            if (ok) Result.success() else Result.failure()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
