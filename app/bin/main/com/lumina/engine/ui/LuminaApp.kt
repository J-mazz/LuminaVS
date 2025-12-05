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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D1B2A),
                            Color(0xFF1B263B),
                            Color(0xFF415A77)
                        )
                    )
                )
                .padding(padding)
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        logoBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Lumina logo",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Lumina Virtual Studio",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Realtime camera + AI",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    GlassyStatusIndicator(
                        status = statusMessage,
                        processingState = luminaState.processingState,
                        modifier = Modifier
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Adaptive color",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = dynamicTheme,
                        onCheckedChange = { luminaViewModel.setDynamicTheme(it) }
                    )
                }

                if (luminaState.processingState == ProcessingState.ERROR) {
                    Spacer(Modifier.height(8.dp))
                    ErrorBanner(message = statusMessage.ifBlank { "Something went wrong" })
                }

                if (isProcessing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF66D9FF),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                IntentSummaryCard(intent = luminaState.currentIntent)

                Spacer(modifier = Modifier.height(16.dp))

                GlassyRenderModeSelector(
                    currentMode = luminaState.renderMode,
                    onModeSelected = { mode -> luminaViewModel.setRenderMode(mode) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                GlassyContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    params = GlassmorphicParams(
                        transparency = 0.3f,
                        cornerRadius = 24f
                    )
                ) {
                    CameraPreviewArea(
                        cameraController = cameraController,
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
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                VideoEditorCard(
                    lastVideoUri = lastVideoUri,
                    isTrimming = isTrimming,
                    status = trimStatus,
                    onTrim = { startMs, endMs ->
                        if (lastVideoUri.isBlank()) {
                            trimStatus = "No video available"
                            return@VideoEditorCard
                        }
                        isTrimming = true
                        trimStatus = "Trimming..."
                        scope.launch {
                            val result = VideoEditor(context = context).trimVideo(Uri.parse(lastVideoUri), startMs, endMs)
                            result.onSuccess { uri ->
                                lastVideoUri = uri.toString()
                                trimStatus = "Trimmed clip saved"
                            }.onFailure { e ->
                                trimStatus = "Trim failed: ${e.message}"
                            }
                            isTrimming = false
                        }
                    },
                    onClearStatus = { trimStatus = "" },
                    onStatus = { trimStatus = it },
                    onPlay = { uriString ->
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(uriString), "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }.onFailure { e -> trimStatus = "Open failed: ${e.message}" }
                    },
                    onShare = { uriString ->
                        runCatching {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share video"))
                        }.onFailure { e -> trimStatus = "Share failed: ${e.message}" }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                GlassyInputBar(
                    value = userInput,
                    onValueChange = { luminaViewModel.setUserInput(it) },
                    onSend = {
                        luminaViewModel.processUserInput(userInput)
                        luminaViewModel.setUserInput("")
                    },
                    isProcessing = isProcessing,
                    placeholder = "Describe your vision..."
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickActionButton(
                        label = "Dreamy",
                        onClick = { luminaViewModel.processUserInput("Make it look dreamy with bloom") }
                    )
                    QuickActionButton(
                        label = "Depth",
                        onClick = { luminaViewModel.setRenderMode(RenderMode.DEPTH_MAP) }
                    )
                    QuickActionButton(
                        label = "Blur",
                        onClick = { luminaViewModel.processUserInput("Add subtle blur effect") }
                    )
                    QuickActionButton(
                        label = "Reset",
                        onClick = {
                            luminaViewModel.clearEffects()
                            luminaViewModel.setRenderMode(RenderMode.PASSTHROUGH)
                        }
                    )
                }
            }
        }
    }
}
