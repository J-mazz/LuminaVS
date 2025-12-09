package com.lumina.engine

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.lumina.engine.DirectBufferPool

// Small helper to create a reusable analyzer for camera frames that copies to a direct buffer
fun createCameraAnalyzer(nativeEngine: INativeEngine): ImageAnalysis.Analyzer {
    return CachingImageAnalyzer { buffer, width, height ->
        // Buffer is already sliced in the analyzer; we can forward directly
        nativeEngine.uploadCameraFrame(buffer, width, height)
    }
}
