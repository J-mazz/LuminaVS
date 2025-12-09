package com.lumina.engine

import java.nio.ByteBuffer

interface INativeEngine {
    fun getVideoTextureId(): Int
    fun uploadCameraFrame(buffer: ByteBuffer, width: Int, height: Int)
}
