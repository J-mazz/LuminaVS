package com.lumina.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Lightweight video trimming utility using MediaExtractor/MediaMuxer.
 */
class VideoEditor(private val context: Context) {

    suspend fun trimVideo(
        inputUri: Uri,
        startMs: Long,
        endMs: Long
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (endMs <= startMs) {
            return@withContext Result.failure(IllegalArgumentException("End must be greater than start"))
        }

        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val outputFile = File(outputDir, "lumina_trim_${System.currentTimeMillis()}.mp4")

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(context, inputUri, null)
            if (extractor.trackCount == 0) {
                return@withContext Result.failure(IllegalStateException("No tracks to trim"))
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                    trackIndexMap[i] = muxer.addTrack(format)
                }
            }

            if (trackIndexMap.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No audio/video tracks found"))
            }

            val maxBufferSize = trackIndexMap.keys.mapNotNull { track ->
                runCatching { extractor.getTrackFormat(track).getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrNull()
            }.maxOrNull()?.coerceAtLeast(64 * 1024) ?: 256 * 1024

            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val info = MediaCodec.BufferInfo()

            val startUs = startMs * 1000
            val endUs = endMs * 1000

            muxer.start()

            for ((sourceTrack, muxerTrack) in trackIndexMap) {
                extractor.selectTrack(sourceTrack)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) {
                        extractor.unselectTrack(sourceTrack)
                        break
                    }

                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0 || sampleTime > endUs) {
                        extractor.unselectTrack(sourceTrack)
                        break
                    }

                    info.presentationTimeUs = sampleTime - startUs
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrack, buffer, info)
                    extractor.advance()
                }
            }

            muxer.stop()

            val scannedUri = suspendCancellableCoroutine<Uri?> { cont ->
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outputFile.absolutePath),
                    arrayOf("video/mp4")
                ) { _, uri ->
                    if (cont.isActive) cont.resume(uri)
                }
            }

            return@withContext Result.success(scannedUri ?: Uri.fromFile(outputFile))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            runCatching { extractor.release() }
            runCatching { muxer?.release() }
        }
    }
}
