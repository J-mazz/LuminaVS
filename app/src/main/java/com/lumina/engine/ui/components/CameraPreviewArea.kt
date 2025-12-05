package com.lumina.engine.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumina.engine.CameraController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewArea(
    cameraController: CameraController,
    onMessage: (String, Boolean) -> Unit,
    onVideoSaved: (String) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf("") }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        cameraController.startCamera(lifecycleOwner, previewView)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = {
                cameraController.takePhoto { result ->
                    lastMessage = result.fold(
                        onSuccess = { uri -> if (uri.isNotBlank()) "Saved photo: $uri" else "Photo saved" },
                        onFailure = { "Photo failed: ${it.message}" }
                    )
                    onMessage(lastMessage, lastMessage.contains("failed", ignoreCase = true))
                }
            }) {
                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Capture photo")
                Spacer(Modifier.width(8.dp))
                Text("Photo")
            }

            FilledTonalButton(onClick = {
                if (isRecording) {
                    cameraController.stopRecording()
                    isRecording = false
                    lastMessage = "Recording stopped"
                    onMessage(lastMessage, false)
                } else {
                    cameraController.startRecording { event ->
                        when (event) {
                            is androidx.camera.video.VideoRecordEvent.Start -> {
                                isRecording = true
                                lastMessage = "Recording..."
                                onMessage(lastMessage, false)
                            }
                            is androidx.camera.video.VideoRecordEvent.Finalize -> {
                                isRecording = false
                                lastMessage = if (event.error == androidx.camera.video.VideoRecordEvent.Finalize.ERROR_NONE) {
                                    val uriString = event.outputResults.outputUri.toString()
                                    onVideoSaved(uriString)
                                    "Saved video: $uriString"
                                } else {
                                    "Recording error: ${event.error}"
                                }
                                onMessage(lastMessage, event.error != androidx.camera.video.VideoRecordEvent.Finalize.ERROR_NONE)
                            }
                        }
                    }
                }
            }) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = "Record"
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isRecording) "Stop" else "Record")
            }

            FilledTonalButton(onClick = {
                cameraController.switchCamera(lifecycleOwner, previewView)
                lastMessage = "Switched camera"
                onMessage(lastMessage, false)
            }) {
                Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "Switch camera")
                Spacer(Modifier.width(8.dp))
                Text("Switch")
            }
        }

        if (lastMessage.isNotBlank()) {
            Text(
                text = lastMessage,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
