package com.lumina.engine

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Small helper to create a reusable analyzer for camera frames that copies to a direct buffer
fun createCameraAnalyzer(nativeEngine: INativeEngine): ImageAnalysis.Analyzer {
    return ImageAnalysis.Analyzer { image: ImageProxy ->
        try {
            val plane = image.planes[0]
            val src = plane.buffer
            val size = src.remaining()
            val buf = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            buf.put(src)
            buf.rewind()
            nativeEngine.uploadCameraFrame(buf, image.width, image.height)
        } finally {
            image.close()
        }
    }
}
