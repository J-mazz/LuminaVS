package com.lumina.engine

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumina.engine.ui.theme.LuminaVSTheme

/**
 * MainActivity - Entry point for Lumina Virtual Studio
 * Initializes JNI bridge and Chaquopy runtime
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var nativeEngine: NativeEngine
    private lateinit var pythonBridge: PythonBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.i(TAG, "Lumina Virtual Studio starting...")

        // Initialize native engine
        nativeEngine = NativeEngine()
        val nativeInitialized = nativeEngine.initialize()
        Log.i(TAG, "Native engine initialized: $nativeInitialized")

        // Initialize Python orchestrator
        pythonBridge = PythonBridge()
        val pythonInitialized = pythonBridge.initialize(filesDir.absolutePath)
        Log.i(TAG, "Python orchestrator initialized: $pythonInitialized")

        setContent {
            LuminaVSTheme {
                LuminaApp(
                    nativeBridge = nativeEngine,
                    pythonOrchestrator = pythonBridge
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Shutting down Lumina Virtual Studio")
        
        pythonBridge.shutdown()
        nativeEngine.shutdown()
    }
}

/**
 * Main Lumina App Composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuminaApp(
    nativeBridge: NativeBridge,
    pythonOrchestrator: PythonOrchestrator,
    luminaViewModel: LuminaViewModel = viewModel()
) {
    // Inject bridges into ViewModel
    LaunchedEffect(Unit) {
        luminaViewModel.nativeBridge = nativeBridge
        luminaViewModel.pythonOrchestrator = pythonOrchestrator
    }

    // Collect state
    val luminaState by luminaViewModel.luminaState.collectAsState()
    val userInput by luminaViewModel.userInput.collectAsState()
    val isProcessing by luminaViewModel.isProcessing.collectAsState()
    val statusMessage by luminaViewModel.statusMessage.collectAsState()

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
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Status bar
            GlassyStatusIndicator(
                status = statusMessage,
                processingState = luminaState.processingState,
                modifier = Modifier.align(Alignment.End)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Render mode selector
            GlassyRenderModeSelector(
                currentMode = luminaState.renderMode,
                onModeSelected = { mode ->
                    luminaViewModel.setRenderMode(mode)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main content area (placeholder for render view)
            GlassyContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                params = GlassmorphicParams(
                    transparency = 0.3f,
                    cornerRadius = 24f
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Lumina Virtual Studio",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Mode: ${luminaState.renderMode.name}",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "FPS: ${luminaState.timing.fps.toInt()}",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        // Show current intent if any
                        if (luminaState.currentIntent.action.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Last Action: ${luminaState.currentIntent.action}",
                                color = Color(0xFF66D9FF).copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Target: ${luminaState.currentIntent.target}",
                                color = Color(0xFF66D9FF).copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Input bar
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

            // Quick action buttons
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

@Composable
private fun QuickActionButton(
    label: String,
    onClick: () -> Unit
) {
    GlassyContainer(
        params = GlassmorphicParams(
            cornerRadius = 12f,
            transparency = 0.5f
        )
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
