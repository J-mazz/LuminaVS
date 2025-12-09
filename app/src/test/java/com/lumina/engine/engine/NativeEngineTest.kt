package com.lumina.engine.engine

import com.lumina.engine.NativeEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeEngineTest {
    @Test
    fun `getVideoTextureId returns zero when engine not initialized`() {
        val engine = NativeEngine()
        val id = engine.getVideoTextureId()
        assertEquals(0, id)
    }

    @Test
    fun `uploadCameraFrame returns early without crash when not initialized`() {
        val engine = NativeEngine()
        // Ensure this does not throw even though native isn't initialized
        engine.uploadCameraFrame(java.nio.ByteBuffer.allocateDirect(16), 4, 4)
    }
}
