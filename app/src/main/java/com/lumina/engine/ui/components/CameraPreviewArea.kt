package com.lumina.engine.ui.components

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
// ByteBuffer imports removed; buffer pool centralized in DirectBufferPool
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumina.engine.CameraController
import com.lumina.engine.CachingImageAnalyzer // [ADD THIS IMPORT]
import com.lumina.engine.INativeEngine
import com.lumina.engine.createCameraAnalyzer


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewArea(
    cameraController: CameraController,
    nativeEngine: INativeEngine? = null,
    onMessage: (String, Boolean) -> Unit,
    onVideoSaved: (String) -> Unit = {},
    onRequestModelDownload: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf("") }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var cameraSurface by remember { mutableStateOf<Surface?>(null) }
    val textureId = remember(nativeEngine) { nativeEngine?.getVideoTextureId() ?: 0 }
    val activeSurface = remember(cameraSurface, isRecording) {
        if (isRecording && cameraSurface != null) cameraSurface else null
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // [OPTIMIZATION] Create a persistent analyzer that reuses its buffer
    // This ensures the cached buffer is not lost when the UI recomposes.
    val vulkanAnalyzer = remember(nativeEngine) {
        if (nativeEngine != null) {
            CachingImageAnalyzer { buffer, width, height ->
                nativeEngine.uploadCameraFrame(buffer, width, height)
            }
        } else null
    }

    // GLES analyzer also persisted for reuse to avoid repeated analyzer allocations
    val glesAnalyzer = remember(nativeEngine) {
        if (nativeEngine != null && textureId != 0) {
            createCameraAnalyzer(nativeEngine)
        } else null
    }

    LaunchedEffect(textureId, previewSize) {
        if (textureId != 0 && previewSize != IntSize.Zero) {
            surfaceTexture?.release()
            cameraSurface?.release()

            val st = SurfaceTexture(textureId).apply {
                setDefaultBufferSize(previewSize.width, previewSize.height)
            }
            val surface = Surface(st)
            surfaceTexture = st
            cameraSurface = surface
        } else if (cameraSurface != null && textureId == 0) {
            cameraSurface?.release()
            surfaceTexture?.release()
            cameraSurface = null
            surfaceTexture = null
        }
    }

    LaunchedEffect(activeSurface) {
        // Determine if we need to feed frames manually (Vulkan mode)
        // If activeSurface is NULL (Vulkan) and we have a native engine, we attach the persistent analyzer.
        val useAnalyzer = (activeSurface == null && nativeEngine != null)

        val analyzerToUse = if (useAnalyzer) vulkanAnalyzer else null

        cameraController.startCamera(lifecycleOwner, previewView, activeSurface, analyzerToUse)
            .onFailure {
                lastMessage = "Camera error: ${it.message ?: "unknown"}";
                onMessage(lastMessage, true)
            }
    }

    DisposableEffect(lifecycleOwner, activeSurface) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        val useVulkanAnalyzer = (activeSurface == null && nativeEngine != null)
                        val analyzer = if (useVulkanAnalyzer) {
                            vulkanAnalyzer
                        } else {
                            glesAnalyzer
                        }
                        cameraController.startCamera(lifecycleOwner, previewView, activeSurface, analyzer)
                            .onFailure {
                                lastMessage = "Camera error: ${it.message ?: "unknown"}";
                                onMessage(lastMessage, true)
                            }
                    }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    cameraController.shutdown()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraController.shutdown()
            cameraSurface?.release()
            surfaceTexture?.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { previewSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        val current = cameraController.currentZoomRatio()
                        val newZoom = (current * zoomChange).coerceIn(1f, 8f)
                        cameraController.setZoomRatio(newZoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        cameraController.tapToFocus(previewView, offset.x, offset.y)
                        lastMessage = "Focusing..."
                    }
                },
            factory = { previewView }
        )
        // Top-right overflow menu
        var showMenu by remember { mutableStateOf(false) }
        var adaptiveLightingEnabled by remember { mutableStateOf(false) }
        var flashlightEnabled by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {}

            // Overflow menu in top-right
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
            ) {
                IconButton(
                    onClick = { showMenu = !showMenu },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    DropdownMenuItem(text = { Text("Switch Camera") }, onClick = {
                        val analyzer = if (activeSurface == null && nativeEngine != null) vulkanAnalyzer else glesAnalyzer
                        cameraController.switchCamera(lifecycleOwner, previewView, activeSurface, analyzer)
                            .onSuccess {
                                lastMessage = "Switched camera"
                                onMessage(lastMessage, false)
                                showMenu = false
                            }
                            .onFailure {
                                lastMessage = "Camera switch failed: ${it.message ?: "unknown"}"
                                onMessage(lastMessage, true)
                                showMenu = false
                            }
                    })
                    DropdownMenuItem(text = { Text("Adaptive Lighting: ${if (adaptiveLightingEnabled) "On" else "Off"}") }, onClick = { adaptiveLightingEnabled = !adaptiveLightingEnabled })
                    DropdownMenuItem(text = { Text("Download model") }, onClick = { onRequestModelDownload(); showMenu = false })
                    DropdownMenuItem(text = { Text("Flash: ${if (flashlightEnabled) "On" else "Off"}") }, onClick = {
                        if (cameraController.hasFlashUnit()) {
                            cameraController.setTorch(!flashlightEnabled)
                                .onSuccess {
                                    flashlightEnabled = !flashlightEnabled
                                    lastMessage = "Flash ${if (flashlightEnabled) "enabled" else "disabled"}"
                                    onMessage(lastMessage, false)
                                }
                                .onFailure {
                                    lastMessage = "Failed to set flash: ${it.message ?: "unknown"}"
                                    onMessage(lastMessage, true)
                                }
                        } else {
                            lastMessage = "No flash available on device"
                            onMessage(lastMessage, true)
                        }
                        showMenu = false
                    })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { /* open settings */ showMenu = false })
                    DropdownMenuItem(text = { Text("Quit") }, onClick = { /* exit app */ showMenu = false })
                }
            }

            // Minimal floating capture/record button
            FloatingActionButton(
                onClick = {
                    cameraController.takePhoto { result ->
                        lastMessage = result.fold(
                            onSuccess = { uri -> if (uri.isNotBlank()) "Saved photo: $uri" else "Photo saved" },
                            onFailure = { "Photo failed: ${it.message}" }
                        )
                        onMessage(lastMessage, lastMessage.contains("failed", ignoreCase = true))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp)
            ) {
                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Capture photo")
            }

            // Query bar (minimalist) at bottom
            var queryText by remember { mutableStateOf("") }
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                placeholder = { Text("Ask Lumina... (e.g., brighten skin tones)") },
                trailingIcon = {
                    IconButton(onClick = {
                        if (queryText.isNotBlank()) {
                            onMessage(queryText, false)
                            queryText = ""
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth(0.9f)
            )

            if (lastMessage.isNotBlank()) {
                Text(
                    text = lastMessage,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}
