package com.lumina.engine

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.camera.view.PreviewView

/**
 * CameraController wires CameraX Preview, ImageCapture, and VideoCapture with lifecycle.
 * Supports camera switching, pinch-to-zoom, tap-to-focus, and orientation-aware outputs.
 */
class CameraController(private val context: Context) {

    companion object {
        private const val TAG = "CameraController"
    }

    private val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: androidx.camera.core.ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        externalSurface: Surface? = null,
        // [FIX] New parameter for frame analysis callback
        analyzer: androidx.camera.core.ImageAnalysis.Analyzer? = null
    ): Result<Unit> {
        val provider = cameraProviderFuture.get()
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val targetSurface = externalSurface?.takeIf { it.isValid }

        // 1. Preview Use Case
        preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build().also { previewInstance ->
                if (targetSurface != null) {
                    // GLES Path (SurfaceTexture)
                    previewInstance.setSurfaceProvider { request ->
                        request.provideSurface(targetSurface, ContextCompat.getMainExecutor(context)) { }
                    }
                } else {
                    // Standard View Path
                    previewInstance.setSurfaceProvider(previewView.surfaceProvider)
                }
            }

        // 2. Image Capture
        imageCapture = ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // 3. Video Capture
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.UHD, Quality.FHD, Quality.HD)))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        val useCases = mutableListOf<androidx.camera.core.UseCase>(preview!!, imageCapture!!, videoCapture!!)

        // 4. [FIX] Image Analysis (Vulkan Input)
        if (analyzer != null) {
            imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            
            imageAnalysis?.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
            useCases.add(imageAnalysis!!)
        }

        return try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun switchCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        externalSurface: Surface? = null,
        analyzer: androidx.camera.core.ImageAnalysis.Analyzer? = null
    ): Result<Unit> {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        return startCamera(lifecycleOwner, previewView, externalSurface, analyzer)
    }

    fun takePhoto(onResult: (Result<String>) -> Unit) {
        val capture = imageCapture ?: return onResult(Result.failure(IllegalStateException("ImageCapture not ready")))

        val name = "lumina_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LuminaVS")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onResult(Result.success(outputFileResults.savedUri?.toString() ?: ""))
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(Result.failure(exception))
                }
            }
        )
    }

    fun startRecording(onStatus: (VideoRecordEvent) -> Unit) {
        val vc = videoCapture ?: return

        val name = "lumina_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LuminaVS")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        activeRecording?.stop()

        activeRecording = vc.output
            .prepareRecording(context, mediaStoreOutput)
            .apply { withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                onStatus(event)
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                }
            }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun currentZoomRatio(): Float {
        return camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
    }

    fun tapToFocus(previewView: PreviewView, x: Float, y: Float) {
        val factory: MeteringPointFactory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun shutdown() {
        activeRecording?.stop()
        activeRecording = null
        imageAnalysis?.clearAnalyzer()
        val provider = cameraProviderFuture.get()
        provider.unbindAll()
    }
}