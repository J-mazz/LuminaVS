package com.lumina.engine.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumina.engine.ColorRGBA
import com.lumina.engine.GlassmorphicParams
import com.lumina.engine.ProcessingState
import com.lumina.engine.RenderMode

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
            .blur(radius = 0.5.dp)
    ) {
        content()
    }
}

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

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulseValue"
    )

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
                    .scale(if (processingState == ProcessingState.PROCESSING || processingState == ProcessingState.RENDERING) pulse else 1f)
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
fun ErrorBanner(message: String) {
    GlassyContainer(
        params = GlassmorphicParams(
            backgroundColor = ColorRGBA(1f, 0.3f, 0.3f, 0.2f),
            borderColor = ColorRGBA(1f, 0.3f, 0.3f, 0.6f),
            transparency = 0.6f,
            cornerRadius = 14f
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = Color(0xFFFFB4A9)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun QuickActionButton(
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
