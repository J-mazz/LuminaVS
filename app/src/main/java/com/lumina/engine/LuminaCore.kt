package com.lumina.engine

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lumina Virtual Studio - Data Contract & UI Components
 * 
 * This file contains:
 * 1. Data classes matching C++ engine_structs.h
 * 2. LuminaViewModel for state management
 * 3. Glassmorphic UI components (Material 3)
 */

// ============================================================================
// Data Classes - Mirror C++ structs for JNI interop
// ============================================================================

/**
 * RGBA Color with HDR support
 */
data class ColorRGBA(
    val r: Float = 0f,
    val g: Float = 0f,
    val b: Float = 0f,
    val a: Float = 1f
) {
    companion object {
        fun fromHex(hex: Long): ColorRGBA {
            return ColorRGBA(
                r = ((hex shr 24) and 0xFF) / 255f,
                g = ((hex shr 16) and 0xFF) / 255f,
                b = ((hex shr 8) and 0xFF) / 255f,
                a = (hex and 0xFF) / 255f
            )
        }

        val Transparent = ColorRGBA(0f, 0f, 0f, 0f)
        val White = ColorRGBA(1f, 1f, 1f, 1f)
        val Black = ColorRGBA(0f, 0f, 0f, 1f)
    }

    fun toComposeColor(): Color = Color(r, g, b, a)
}

/**
 * 2D Vector for positions and dimensions
 */
data class Vec2(
    val x: Float = 0f,
    val y: Float = 0f
)

/**
 * 3D Vector for spatial coordinates
 */
data class Vec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

/**
 * Render mode enumeration
 */
enum class RenderMode(val value: Int) {
    PASSTHROUGH(0),
    STYLIZED(1),
    SEGMENTED(2),
    DEPTH_MAP(3),
    NORMAL_MAP(4)
}

/**
 * Effect type enumeration
 */
enum class EffectType(val value: Int) {
    NONE(0),
    BLUR(1),
    BLOOM(2),
    COLOR_GRADE(3),
    VIGNETTE(4),
    CHROMATIC_ABERRATION(5),
    NOISE(6),
    SHARPEN(7)
}

/**
 * Processing state enumeration
 */
enum class ProcessingState(val value: Int) {
    IDLE(0),
    PROCESSING(1),
    RENDERING(2),
    ERROR(3)
}

/**
 * Effect parameters
 */
data class EffectParams(
    val type: EffectType = EffectType.NONE,
    val intensity: Float = 1f,
    val param1: Float = 0f,
    val param2: Float = 0f,
    val tintColor: ColorRGBA = ColorRGBA(),
    val center: Vec2 = Vec2(0.5f, 0.5f),
    val scale: Vec2 = Vec2(1f, 1f)
)

/**
 * Camera state for viewport configuration
 */
data class CameraState(
    val position: Vec3 = Vec3(0f, 0f, 5f),
    val lookAt: Vec3 = Vec3(0f, 0f, 0f),
    val fov: Float = 60f,
    val nearPlane: Float = 0.1f,
    val farPlane: Float = 1000f
)

/**
 * Glassmorphic UI parameters
 */
data class GlassmorphicParams(
    val backgroundColor: ColorRGBA = ColorRGBA(1f, 1f, 1f, 0.1f),
    val borderColor: ColorRGBA = ColorRGBA(1f, 1f, 1f, 0.2f),
    val blurRadius: Float = 20f,
    val transparency: Float = 0.7f,
    val borderWidth: Float = 1f,
    val cornerRadius: Float = 16f,
    val saturation: Float = 1.2f,
    val brightness: Float = 1.1f
)

/**
 * AI Intent result from Python orchestrator
 */
data class AIIntent(
    val action: String = "",
    val target: String = "",
    val parameters: String = "",
    val confidence: Float = 0f,
    val timestamp: Long = 0L
)

/**
 * Frame timing information
 */
data class FrameTiming(
    val deltaTime: Float = 0f,
    val totalTime: Float = 0f,
    val frameCount: Long = 0L,
    val fps: Float = 0f,
    val gpuTime: Float = 0f,
    val cpuTime: Float = 0f
)

/**
 * Touch state enumeration
 */
enum class TouchState(val value: Int) {
    NONE(0),
    DOWN(1),
    MOVE(2),
    UP(3)
}

/**
 * Main Lumina State - Central data contract
 * Matches C++ LuminaState struct
 */
data class LuminaState(
    val version: Int = 1,
    val stateId: Int = 0,
    val processingState: ProcessingState = ProcessingState.IDLE,
    val flags: Int = 0,
    val renderMode: RenderMode = RenderMode.PASSTHROUGH,
    val width: Int = 1920,
    val height: Int = 1080,
    val aspectRatio: Float = 16f / 9f,
    val camera: CameraState = CameraState(),
    val effects: List<EffectParams> = listOf(
        EffectParams(), EffectParams(), EffectParams(), EffectParams()
    ),
    val activeEffectCount: Int = 0,
    val uiStyle: GlassmorphicParams = GlassmorphicParams(),
    val currentIntent: AIIntent = AIIntent(),
    val pendingIntent: AIIntent = AIIntent(),
    val timing: FrameTiming = FrameTiming(),
    val touchPosition: Vec2 = Vec2(),
    val touchDelta: Vec2 = Vec2(),
    val touchPressure: Float = 0f,
    val touchState: TouchState = TouchState.NONE
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): LuminaState = Gson().fromJson(json, LuminaState::class.java)
    }
}

// ============================================================================
// ViewModel - State Management
// ============================================================================

/**
 * LuminaViewModel manages the application state and bridges UI, AI, and Render layers
 */
class LuminaViewModel : ViewModel() {

    private val _luminaState = MutableStateFlow(LuminaState())
    val luminaState: StateFlow<LuminaState> = _luminaState.asStateFlow()

    private val _userInput = MutableStateFlow("")
    val userInput: StateFlow<String> = _userInput.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Native bridge reference (initialized from MainActivity)
    var nativeBridge: NativeBridge? = null

    // Python orchestrator reference
    var pythonOrchestrator: PythonOrchestrator? = null

    /**
     * Process user input through the AI pipeline
     */
    fun processUserInput(input: String) {
        if (input.isBlank()) return

        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Processing..."

            try {
                // Update state to processing
                updateState { copy(processingState = ProcessingState.PROCESSING) }

                // Send to Python orchestrator for intent parsing
                val intent = withContext(Dispatchers.Default) {
                    pythonOrchestrator?.parseIntent(input) ?: AIIntent(
                        action = "unknown",
                        target = input,
                        confidence = 0f
                    )
                }

                // Update state with parsed intent
                updateState {
                    copy(
                        currentIntent = intent,
                        processingState = ProcessingState.RENDERING
                    )
                }

                // Send to native renderer
                nativeBridge?.updateState(_luminaState.value.toJson())

                _statusMessage.value = "Applied: ${intent.action}"

            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                updateState { copy(processingState = ProcessingState.ERROR) }
            } finally {
                _isProcessing.value = false
                updateState { copy(processingState = ProcessingState.IDLE) }
            }
        }
    }

    /**
     * Update the Lumina state with a transformation
     */
    fun updateState(transform: LuminaState.() -> LuminaState) {
        _luminaState.value = _luminaState.value.transform().copy(
            stateId = _luminaState.value.stateId + 1
        )
    }

    /**
     * Set render mode
     */
    fun setRenderMode(mode: RenderMode) {
        updateState { copy(renderMode = mode) }
        nativeBridge?.setRenderMode(mode.value)
    }

    /**
     * Add effect to the pipeline
     */
    fun addEffect(effect: EffectParams) {
        updateState {
            val newEffects = effects.toMutableList()
            if (activeEffectCount < 4) {
                newEffects[activeEffectCount] = effect
                copy(effects = newEffects, activeEffectCount = activeEffectCount + 1)
            } else this
        }
    }

    /**
     * Clear all effects
     */
    fun clearEffects() {
        updateState {
            copy(
                effects = listOf(EffectParams(), EffectParams(), EffectParams(), EffectParams()),
                activeEffectCount = 0
            )
        }
    }

    /**
     * Update UI styling
     */
    fun updateUIStyle(params: GlassmorphicParams) {
        updateState { copy(uiStyle = params) }
    }

    /**
     * Update user input text
     */
    fun setUserInput(text: String) {
        _userInput.value = text
    }
}

// ============================================================================
// Bridge Interfaces
// ============================================================================

/**
 * Interface for native C++ bridge
 */
interface NativeBridge {
    fun initialize(): Boolean
    fun updateState(jsonState: String)
    fun setRenderMode(mode: Int)
    fun getFrameTiming(): FrameTiming
    fun shutdown()
}

/**
 * Interface for Python orchestrator
 */
interface PythonOrchestrator {
    fun initialize(assetsPath: String): Boolean
    fun parseIntent(userInput: String): AIIntent
    fun shutdown()
}

// ============================================================================
// Glassmorphic UI Components
// ============================================================================

/**
 * Glassmorphic container with blur and transparency effects
 */
@Composable
fun GlassyContainer(
    modifier: Modifier = Modifier,
    params: GlassmorphicParams = GlassmorphicParams(),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(params.cornerRadius.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        params.backgroundColor.toComposeColor().copy(alpha = params.transparency),
                        params.backgroundColor.toComposeColor().copy(alpha = params.transparency * 0.5f)
                    )
                )
            )
            .border(
                width = params.borderWidth.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        params.borderColor.toComposeColor(),
                        params.borderColor.toComposeColor().copy(alpha = 0.5f)
                    )
                ),
                shape = RoundedCornerShape(params.cornerRadius.dp)
            )
            .blur(radius = 0.5.dp) // Subtle blur on container itself
    ) {
        content()
    }
}

/**
 * Glassmorphic Input Bar - Main user interaction component
 */
@Composable
fun GlassyInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    placeholder: String = "Describe your vision...",
    params: GlassmorphicParams = GlassmorphicParams()
) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val processingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "processingAlpha"
    )

    GlassyContainer(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        params = if (isProcessing) {
            params.copy(
                borderColor = ColorRGBA(0.4f, 0.8f, 1f, processingAlpha)
            )
        } else params
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text input
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Send button
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (value.isNotBlank()) Color.White else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Status indicator with glassmorphic styling
 */
@Composable
fun GlassyStatusIndicator(
    status: String,
    processingState: ProcessingState,
    modifier: Modifier = Modifier
) {
    val stateColor = when (processingState) {
        ProcessingState.IDLE -> Color(0xFF4CAF50)
        ProcessingState.PROCESSING -> Color(0xFF2196F3)
        ProcessingState.RENDERING -> Color(0xFFFF9800)
        ProcessingState.ERROR -> Color(0xFFF44336)
    }

    GlassyContainer(
        modifier = modifier
            .wrapContentSize()
            .padding(8.dp),
        params = GlassmorphicParams(
            cornerRadius = 20f,
            transparency = 0.6f
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(stateColor, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = status,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Render mode selector with glassmorphic buttons
 */
@Composable
fun GlassyRenderModeSelector(
    currentMode: RenderMode,
    onModeSelected: (RenderMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        RenderMode.entries.forEach { mode ->
            GlassyModeButton(
                label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                isSelected = mode == currentMode,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

@Composable
private fun GlassyModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val params = if (isSelected) {
        GlassmorphicParams(
            backgroundColor = ColorRGBA(0.4f, 0.8f, 1f, 0.3f),
            borderColor = ColorRGBA(0.4f, 0.8f, 1f, 0.6f),
            transparency = 0.8f
        )
    } else {
        GlassmorphicParams()
    }

    GlassyContainer(
        params = params,
        modifier = Modifier.padding(4.dp)
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = label,
                color = if (isSelected) Color(0xFF66D9FF) else Color.White,
                fontSize = 12.sp
            )
        }
    }
}
