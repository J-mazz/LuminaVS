package com.lumina.engine.camera

import androidx.camera.testing.fakes.FakeImageInfo
import androidx.camera.testing.fakes.FakeImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumina.engine.DirectBufferPool
import com.lumina.engine.INativeEngine
import com.lumina.engine.CachingImageAnalyzer
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class CachingImageAnalyzerInstrumentationTest {
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
    fun poolingReusesBufferForSameSize() {
        val native = FakeNativeEngine()
        val analyzer = CachingImageAnalyzer { buffer, w, h -> native.uploadCameraFrame(buffer, w, h) }

        val width = 64
        val height = 64
        val byteCount = width * height * 4

        // ensure there's an initial pooled buffer
        val poolBuf1 = DirectBufferPool.getDirectBuffer(byteCount)

        val buffer = ByteBuffer.allocate(byteCount)
        for (i in 0 until byteCount) buffer.put(i.toByte())
        buffer.rewind()

        val img = FakeImageProxy(buffer, width, height, FakeImageInfo(1L, 0L))
        analyzer.analyze(img)

        val poolBuf2 = DirectBufferPool.getDirectBuffer(byteCount)
        // verify pool buffer hadn't reallocated
        assertSame(poolBuf1, poolBuf2)
    }

    @Test
    fun poolingExpandsOnLargerSize() {
        val native = FakeNativeEngine()
        val analyzer = CachingImageAnalyzer { buffer, w, h -> native.uploadCameraFrame(buffer, w, h) }

        val width = 64
        val height = 64
        val smallSize = width * height * 4
        val bigSize = 256 * 256 * 4

        val poolSmall = DirectBufferPool.getDirectBuffer(smallSize)

        val bigBuf = ByteBuffer.allocate(bigSize)
        for (i in 0 until bigSize) bigBuf.put(i.toByte())
        bigBuf.rewind()

        val img = FakeImageProxy(bigBuf, 256, 256, FakeImageInfo(1L, 0L))
        analyzer.analyze(img)

        val poolBig = DirectBufferPool.getDirectBuffer(bigSize)
        assertTrue(poolBig.capacity() >= bigSize)
        assertNotSame(poolSmall, poolBig)
    }
}
