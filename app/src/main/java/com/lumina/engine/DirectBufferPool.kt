package com.lumina.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Global ThreadLocal pool for Direct ByteBuffers to avoid allocating
 * a new buffer every frame. Each thread has its own buffer instance.
 */
object DirectBufferPool {
    private val TLS = ThreadLocal<ByteBuffer?>()

    fun getDirectBuffer(minSize: Int): ByteBuffer {
        val existing = TLS.get()
        if (existing == null || existing.capacity() < minSize) {
            val nb = ByteBuffer.allocateDirect(minSize).order(ByteOrder.nativeOrder())
            TLS.set(nb)
            return nb
        }
        existing.clear()
        return existing
    }
}
