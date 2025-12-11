package com.lumina.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.text.Charsets.UTF_8
import java.net.URL

/**
 * Handles downloading the Qwen 3 model on first launch.
 */
class ModelDownloader(
    private val context: Context,
    private val modelFilename: String = MODEL_FILENAME,
    private val modelUrl: String = MODEL_URL,
    private val minModelSize: Long = MIN_MODEL_SIZE,
    private val modelSha256: String = MODEL_SHA256,
    private val modelSha256Url: String = MODEL_SHA256_URL
) {

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
        get() = File(modelDir, modelFilename)

    val isModelAvailable: Boolean
        get() = modelFile.exists() && modelFile.length() > minModelSize

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
                val ok = downloadModel()
                if (!ok) throw Exception("Model download failed")
                _state.value = DownloadState.Completed
                true
            } catch (e: Exception) {
                _state.value = DownloadState.Error(e.message ?: "Download failed")
                false
            }
        }
    }

    private suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        modelDir.mkdirs()

        val tempFile = File(modelDir, "$MODEL_FILENAME.tmp")
        
        try {
            val url = URL(modelUrl)
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
            val renamed = tempFile.renameTo(modelFile)
            if (!renamed) throw Exception("Failed to move model file into place")

            // If a checksum is configured, verify it
            val expected = when {
                modelSha256.isNotBlank() -> modelSha256
                modelSha256Url.isNotBlank() -> downloadChecksumFromUrl(modelSha256Url)
                else -> ""
            }

            if (expected.isNotBlank()) {
                val actual = computeSha256(modelFile)
                if (!actual.equals(expected, ignoreCase = true)) {
                    // Corrupted or tampered file; delete and fail
                    modelFile.delete()
                    throw Exception("Checksum mismatch: expected $expected actual $actual")
                }
            }

            true

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

    private fun computeSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream(), md).use { dis ->
            val buffer = ByteArray(8192)
            while (dis.read(buffer) != -1) {
                // digest updated while reading
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadChecksumFromUrl(urlString: String): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.connect()
            conn.inputStream.bufferedReader(UTF_8).use { r ->
                r.readText().trim().split(Regex("\\s+"))[0]
            }
        } catch (t: Throwable) {
            ""
        }
    }

    companion object {
        // Keep in sync with orchestrator.py MODEL_FILENAME
        private const val MODEL_FILENAME = "qwen3-1.7b-instruct-q4_k_m.gguf"
        private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen3-1.7B-Instruct-GGUF/resolve/main/qwen3-1.7b-instruct-q4_k_m.gguf"
        // Optional: set to a verified SHA-256 checksum for the model to enforce verification.
        private const val MODEL_SHA256 = ""
        // Optional: URL which returns a checksum (hex or sha256sum-like text)
        private const val MODEL_SHA256_URL = ""
        private const val MIN_MODEL_SIZE = 1_200_000_000L // ~1.2GB minimum
    }
}
