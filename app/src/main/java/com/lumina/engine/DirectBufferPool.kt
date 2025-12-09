package com.lumina.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Global ThreadLocal pool for Direct ByteBuffers to avoid allocating
 * a new buffer every frame. Each thread has its own buffer instance.
 */
object DirectBufferPool {
    private val TLS = ThreadLocal<ByteBuffer?>()

    // Default max pooled capacity (in bytes). If a requested buffer grows beyond this,
    // we will cap the pool size to this value and shrink back down when smaller sizes are requested.
    private const val DEFAULT_MAX_POOLED_CAPACITY: Int = 8 * 1024 * 1024 // 8 MB

    // Multiplier to decide when to shrink down: if the pool capacity is more than
    // this multiplier * requested size, shrink to a reasonable smaller size.
    private const val SHRINK_MULTIPLIER = 2

    fun getDirectBuffer(minSize: Int): ByteBuffer {
        val maxCapacity = maxOf(minSize, DEFAULT_MAX_POOLED_CAPACITY)
        val existing = TLS.get()

        if (existing == null) {
            val cap = clampCapacity(minSize, maxCapacity)
            val nb = ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder())
            TLS.set(nb)
            return nb
        }

        // existing is available
        // If existing is large enough, decide whether to shrink to avoid unbounded growth
        if (existing.capacity() >= minSize) {
            if (shouldShrink(existing.capacity(), minSize)) {
                val newCap = clampCapacity(minSize, maxCapacity)
                val nb = ByteBuffer.allocateDirect(newCap).order(ByteOrder.nativeOrder())
                TLS.set(nb)
                return nb
            }
            existing.clear()
            return existing
        }

        // Need a larger buffer: grow but clamp to maxCapacity unless minSize is larger
        val newCap = clampCapacity(growCapacity(existing.capacity(), minSize), maxCapacity)
        val nb = ByteBuffer.allocateDirect(newCap).order(ByteOrder.nativeOrder())
        TLS.set(nb)
        return nb
    }

    private fun clampCapacity(requested: Int, maxCapacity: Int): Int {
        // Avoid very small allocations; round up to power-of-two for helpful growth behavior
        var cap = requested
        // Round up to next power of two (simple loop)
        var pow = 1
        while (pow < cap) pow = pow shl 1
        cap = pow
        // Cap it to maxCapacity
        if (cap > maxCapacity) cap = maxCapacity
        return cap
    }

    private fun shouldShrink(existingCapacity: Int, requestedSize: Int): Boolean {
        return existingCapacity >= (requestedSize * SHRINK_MULTIPLIER) && existingCapacity > DEFAULT_MAX_POOLED_CAPACITY
    }

    private fun growCapacity(existing: Int, requested: Int): Int {
        var nc = existing
        while (nc < requested) nc = if (nc == 0) 1 else nc shl 1
        return nc
    }
}
