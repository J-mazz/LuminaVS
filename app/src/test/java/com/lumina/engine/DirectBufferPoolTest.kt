package com.lumina.engine

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class DirectBufferPoolTest {

    @Test
    fun testPoolReturnsSameInstanceForSameSize() {
        val b1 = DirectBufferPool.getDirectBuffer(1024)
        val b2 = DirectBufferPool.getDirectBuffer(512)
        // b1 still sufficient; should return same instance
        assertSame(b1, b2)
    }

    @Test
    fun testPoolAllocatesNewForLargerSize() {
        val b1 = DirectBufferPool.getDirectBuffer(1024)
        val b2 = DirectBufferPool.getDirectBuffer(2048)
        // b2 must be a new buffer with capacity >= second request
        assertTrue(b2.capacity() >= 2048)
        assertTrue(b1 !== b2)
    }

    @Test
    fun testPoolShrinksAfterLargeRequest() {
        val big = DirectBufferPool.getDirectBuffer(16 * 1024 * 1024) // 16MB request
        val small = DirectBufferPool.getDirectBuffer(1024) // should shrink to at most DEFAULT_MAX_POOLED_CAPACITY
        assertTrue(small.capacity() <= 8 * 1024 * 1024)
    }
}
