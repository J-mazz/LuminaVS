package com.lumina.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles downloading the Qwen 3 model on first launch.
 */
class ModelDownloader(private val context: Context) {

    sealed class DownloadState {
        object Idle : DownloadState()
        object Checking : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        object Completed : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state

    private val modelDir: File
        get() = File(context.filesDir, "models")

    private val modelFile: File
        get() = File(modelDir, MODEL_FILENAME)

    val isModelAvailable: Boolean
        get() = modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE

    val modelPath: String
        get() = modelFile.absolutePath

    suspend fun ensureModelAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            if (isModelAvailable) {
                _state.value = DownloadState.Completed
                return@withContext true
            }

            _state.value = DownloadState.Checking
            
            try {
                downloadModel()
                _state.value = DownloadState.Completed
                true
            } catch (e: Exception) {
                _state.value = DownloadState.Error(e.message ?: "Download failed")
                false
            }
        }
    }

    private suspend fun downloadModel() = withContext(Dispatchers.IO) {
        modelDir.mkdirs()

        val tempFile = File(modelDir, "$MODEL_FILENAME.tmp")
        
        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.connect()

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes
                            _state.value = DownloadState.Downloading(progress)
                        }
                    }
                }
            }

            // Rename temp file to final name
            tempFile.renameTo(modelFile)

        } finally {
            // Clean up temp file if it exists
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun deleteModel() {
        if (modelFile.exists()) {
            modelFile.delete()
        }
        _state.value = DownloadState.Idle
    }

    companion object {
        // Keep in sync with orchestrator.py MODEL_FILENAME
        private const val MODEL_FILENAME = "qwen3-1.7b-instruct-q4_k_m.gguf"
        private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen3-1.7B-Instruct-GGUF/resolve/main/qwen3-1.7b-instruct-q4_k_m.gguf"
        private const val MIN_MODEL_SIZE = 1_200_000_000L // ~1.2GB minimum
    }
}
