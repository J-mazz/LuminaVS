package com.lumina.engine

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.lumina.engine.DirectBufferPool

/**
 * Optimized analyzer that reuses a single DirectByteBuffer to avoid
 * garbage collection stutter during high-speed frame processing.
 */
class CachingImageAnalyzer(
    private val onFrameCaptured: (ByteBuffer, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    // Persistent buffer to avoid allocation every frame
    private var cachedBuffer: ByteBuffer? = null

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val source = plane.buffer
            val width = image.width
            val height = image.height

            // CameraX guarantees the plane buffer covers the image for RGBA_8888
            val requiredSize = source.remaining()

            // Allocate only if needed (first run or resolution change)
            if (cachedBuffer == null || cachedBuffer!!.capacity() < requiredSize) {
                // Prefer pool allocation; fall back to direct if necessary
                cachedBuffer = DirectBufferPool.getDirectBuffer(requiredSize)
            }

            // Clear previous data and reuse the buffer
            cachedBuffer!!.clear()

            // Fast native copy from ImageProxy plane to our DirectBuffer
            cachedBuffer!!.put(source)
            cachedBuffer!!.flip() // Reset position to 0 for C++ reading

            // Pass to consumer (JNI) - slice so capacity == size for the consumer and tests
            val out = cachedBuffer!!.slice()
            onFrameCaptured(out, width, height)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Critical: close the frame so CameraX produces the next one
            image.close()
        }
    }
}
