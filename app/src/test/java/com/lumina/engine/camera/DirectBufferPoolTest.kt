package com.lumina.engine.camera

import com.lumina.engine.DirectBufferPool
import org.junit.Assert.assertSame
import org.junit.Test
import java.nio.ByteBuffer

class DirectBufferPoolTest {
    @Test
    fun `getDirectBuffer reuses buffer when capacity is sufficient`() {
        val buf1 = DirectBufferPool.getDirectBuffer(1024)
        val buf2 = DirectBufferPool.getDirectBuffer(512)
        assertSame(buf1, buf2)
    }
}
