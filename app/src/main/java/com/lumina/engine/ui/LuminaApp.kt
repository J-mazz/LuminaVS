package com.lumina.engine.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import com.lumina.engine.GlassmorphicParams
import com.lumina.engine.NativeBridge
import com.lumina.engine.NativeEngine
import com.lumina.engine.PythonOrchestrator
import com.lumina.engine.RenderMode
import com.lumina.engine.ProcessingState
import com.lumina.engine.VideoEditor
import com.lumina.engine.CameraController
import com.lumina.engine.LuminaViewModel
import com.lumina.engine.ui.components.CameraPreviewArea
import com.lumina.engine.ui.components.GlassyContainer
import com.lumina.engine.ui.components.GlassyInputBar
import com.lumina.engine.ui.components.GlassyRenderModeSelector
import com.lumina.engine.ui.components.GlassyStatusIndicator
import com.lumina.engine.ui.components.IntentSummaryCard
import com.lumina.engine.ui.components.QuickActionButton
import com.lumina.engine.ui.components.VideoEditorCard
import com.lumina.engine.ui.components.ErrorBanner
import com.lumina.engine.ModelDownloader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuminaApp(
    nativeBridge: NativeBridge,
    pythonOrchestrator: PythonOrchestrator,
    cameraController: CameraController,
    luminaViewModel: LuminaViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        luminaViewModel.nativeBridge = nativeBridge
        luminaViewModel.pythonOrchestrator = pythonOrchestrator
    }

    val luminaState by luminaViewModel.luminaState.collectAsState()
    val userInput by luminaViewModel.userInput.collectAsState()
    val isProcessing by luminaViewModel.isProcessing.collectAsState()
    val statusMessage by luminaViewModel.statusMessage.collectAsState()
    val dynamicTheme by luminaViewModel.dynamicTheme.collectAsState()
    val context = LocalContext.current

    val logoBitmap = remember {
        runCatching {
            context.assets.open("LuminaVS_logo.png").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var lastVideoUri by rememberSaveable { mutableStateOf("") }
    var trimStatus by rememberSaveable { mutableStateOf("") }
    var isTrimming by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(statusMessage, luminaState.processingState) {
        if (statusMessage.isNotBlank()) {
            val isError = luminaState.processingState == ProcessingState.ERROR || statusMessage.contains("error", ignoreCase = true)
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = statusMessage,
                    withDismissAction = true,
                    duration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
                )
            }
        }
    }

    val modelDownloader = remember { ModelDownloader(context) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .systemBarsPadding()
        ) {
            // Full-screen camera preview with minimalist UI overlays
            CameraPreviewArea(
                cameraController = cameraController,
                nativeEngine = nativeBridge as? NativeEngine,
                onMessage = { msg, isError ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = msg,
                            withDismissAction = true,
                            duration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
                        )
                    }
                },
                onVideoSaved = { uriString ->
                    lastVideoUri = uriString
                    trimStatus = "Video ready to edit"
                }
                ,
                onRequestModelDownload = {
                    scope.launch { modelDownloader.ensureModelAvailable() }
                }
            )

            // The ModelDownloader sits above the preview so we can start it
            val modelState by modelDownloader.state.collectAsState(initial = com.lumina.engine.ModelDownloader.DownloadState.Idle)

            // Start the model download on first compose
            LaunchedEffect(modelDownloader) {
                modelDownloader.ensureModelAvailable()
            }

            // Show a subtle model download indicator at top
            when (modelState) {
                is com.lumina.engine.ModelDownloader.DownloadState.Checking -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        color = Color(0xFF66D9FF)
                    )
                }
                is com.lumina.engine.ModelDownloader.DownloadState.Downloading -> {
                    val progress = (modelState as com.lumina.engine.ModelDownloader.DownloadState.Downloading).progress
                    Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                        Text(
                            text = "Downloading model... ${(progress * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(0.6f).height(6.dp)
                        )
                    }
                }
                is com.lumina.engine.ModelDownloader.DownloadState.Error -> {
                    val message = (modelState as com.lumina.engine.ModelDownloader.DownloadState.Error).message
                    Text(
                        text = "Model download failed: $message",
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                    )
                }
                is com.lumina.engine.ModelDownloader.DownloadState.Completed -> {
                    Text(
                        text = "AI model ready",
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                    )
                }
                else -> Unit
            }
        }
    }
}
