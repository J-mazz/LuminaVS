package com.lumina.engine

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class DirectBufferPoolTest {
    @Test
    fun `getDirectBuffer reuses buffer when capacity is sufficient`() {
        // Use reflection to access the private ThreadLocal from CameraPreviewArea (test-only)
        val clazz = Class.forName("com.lumina.engine.ui.components.CameraPreviewAreaKt")
        val method = clazz.getDeclaredMethod("getDirectBuffer", Int::class.javaPrimitiveType)
        method.isAccessible = true

        val buf1 = method.invoke(null, 1024) as ByteBuffer
        val buf2 = method.invoke(null, 512) as ByteBuffer
        assertSame(buf1, buf2) // Should reuse same buffer when capacity >= needed
    }
}
