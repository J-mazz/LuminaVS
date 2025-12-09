package com.lumina.engine.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.testing.fakes.FakeImageInfo
import androidx.camera.testing.fakes.FakeImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumina.engine.CachingImageAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class CachingImageAnalyzerTest {
    class FakeNativeEngine {
        var lastBuffer: ByteBuffer? = null
        var lastWidth: Int = 0
        var lastHeight: Int = 0
        fun upload(buffer: ByteBuffer, width: Int, height: Int) {
            lastBuffer = buffer
            lastWidth = width
            lastHeight = height
        }
    }

    @Test
    fun analyzerReusesBufferAndUploads() {
        val native = FakeNativeEngine()
        val analyzer: ImageAnalysis.Analyzer = CachingImageAnalyzer { buf, w, h -> native.upload(buf, w, h) }

        val width = 16
        val height = 16
        val byteCount = width * height * 4

        val buffer = ByteBuffer.allocate(byteCount)
        for (i in 0 until byteCount) buffer.put(i.toByte())
        buffer.rewind()

        val img = FakeImageProxy(buffer, width, height, FakeImageInfo(1L, 0L))
        analyzer.analyze(img)

        assertEquals(width, native.lastWidth)
        assertEquals(height, native.lastHeight)
        assertEquals(byteCount, native.lastBuffer?.capacity())

        // Analyze a second time with the same size to ensure continuity
        val buffer2 = ByteBuffer.allocate(byteCount)
        for (i in 0 until byteCount) buffer2.put((i * 2).toByte())
        buffer2.rewind()
        val img2 = FakeImageProxy(buffer2, width, height, FakeImageInfo(2L, 0L))
        analyzer.analyze(img2)

        assertEquals(width, native.lastWidth)
        assertEquals(height, native.lastHeight)
        assertEquals(byteCount, native.lastBuffer?.capacity())
    }
}
