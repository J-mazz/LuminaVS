package com.lumina.engine.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.testing.fakes.FakeImageInfo
import androidx.camera.testing.fakes.FakeImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumina.engine.INativeEngine
import com.lumina.engine.createCameraAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class CameraAnalyzerTest {
    class FakeNativeEngine : INativeEngine {
        var lastBuffer: ByteBuffer? = null
        var lastWidth: Int = 0
        var lastHeight: Int = 0
        override fun getVideoTextureId(): Int = 0
        override fun uploadCameraFrame(buffer: ByteBuffer, width: Int, height: Int) {
            lastBuffer = buffer
            lastWidth = width
            lastHeight = height
        }
    }

    @Test
    fun analyzerUploadsBufferToNativeEngine() {
        val native = FakeNativeEngine()
        val analyzer: ImageAnalysis.Analyzer = createCameraAnalyzer(native)

        // Create a fake ImageProxy with a simple buffer
        val width = 12
        val height = 8
        val byteCount = width * height * 4
        val buffer = ByteBuffer.allocate(byteCount)
        for (i in 0 until byteCount) buffer.put(i.toByte())
        buffer.rewind()

        val img = FakeImageProxy(buffer, width, height, FakeImageInfo(1L, 0L))
        analyzer.analyze(img)

        // Verify native engine received the buffer and dims
        assertEquals(width, native.lastWidth)
        assertEquals(height, native.lastHeight)
        assertEquals(byteCount, native.lastBuffer?.capacity())
    }
}
