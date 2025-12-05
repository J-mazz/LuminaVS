package com.lumina.engine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.lumina.engine.AIIntent
import com.lumina.engine.ColorRGBA
import com.lumina.engine.GlassmorphicParams

@Composable
fun IntentSummaryCard(
    intent: AIIntent,
    modifier: Modifier = Modifier
) {
    val hasIntent = intent.action.isNotBlank() || intent.target.isNotBlank()

    GlassyContainer(
        modifier = modifier.fillMaxWidth(),
        params = GlassmorphicParams(
            transparency = 0.45f,
            cornerRadius = 18f,
            borderColor = ColorRGBA(0.4f, 0.8f, 1f, 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Intent",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasIntent) intent.action.ifBlank { "Parsed intent" } else "Awaiting input",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            if (intent.target.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = intent.target,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (intent.confidence > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                ConfidenceChip(confidence = intent.confidence)
            }
        }
    }
}

@Composable
fun ConfidenceChip(confidence: Float, modifier: Modifier = Modifier) {
    GlassyContainer(
        modifier = modifier,
        params = GlassmorphicParams(
            transparency = 0.35f,
            borderColor = ColorRGBA(0.4f, 0.9f, 0.6f, 0.6f),
            cornerRadius = 14f
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF7FFFD4), RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Confidence ${(confidence * 100).coerceIn(0f, 100f).format(0)}%",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun Float.format(decimals: Int): String = String.format("%.${decimals}f", this)
