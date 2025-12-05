package com.lumina.engine.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumina.engine.ColorRGBA
import com.lumina.engine.GlassmorphicParams

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorCard(
    lastVideoUri: String,
    isTrimming: Boolean,
    status: String,
    onTrim: (startMs: Long, endMs: Long) -> Unit,
    onClearStatus: () -> Unit,
    onStatus: (String) -> Unit,
    onPlay: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var durationMs by rememberSaveable(lastVideoUri) { mutableStateOf(0L) }
    var startMs by rememberSaveable(lastVideoUri) { mutableStateOf(0f) }
    var endMs by rememberSaveable(lastVideoUri) { mutableStateOf(0f) }
    var resolution by rememberSaveable(lastVideoUri) { mutableStateOf("") }
    var sizeLabel by rememberSaveable(lastVideoUri) { mutableStateOf("") }
    var thumbnail by rememberSaveable(lastVideoUri) { mutableStateOf<Bitmap?>(null) }
    val minGapMs = 750f
    val videoView = remember(lastVideoUri) { VideoView(context) }

    DisposableEffect(lastVideoUri) {
        onDispose {
            runCatching { videoView.stopPlayback() }
        }
    }

    LaunchedEffect(lastVideoUri) {
        if (lastVideoUri.isBlank()) {
            durationMs = 0
            startMs = 0f
            endMs = 0f
            resolution = ""
            sizeLabel = ""
            thumbnail = null
            return@LaunchedEffect
        }

        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(lastVideoUri))
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                resolution = if (width != null && height != null) "${width}x${height}" else ""

                thumbnail = retriever.getFrameAtTime(150_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                sizeLabel = humanFileSize(context, Uri.parse(lastVideoUri))
                durationMs = dur
                startMs = 0f
                endMs = dur.toFloat()
            } finally {
                retriever.release()
            }
        }.onFailure {
            durationMs = 0
        }
    }

    GlassyContainer(
        modifier = modifier.fillMaxWidth(),
        params = GlassmorphicParams(
            transparency = 0.35f,
            cornerRadius = 18f,
            borderColor = ColorRGBA(0.5f, 0.8f, 1f, 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Video editor",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            if (lastVideoUri.isBlank() || durationMs <= 0) {
                Text(
                    text = "Record a clip to trim and export.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail!!.asImageBitmap(),
                            contentDescription = "Clip preview",
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Last capture: ${formatTimeMs(durationMs)}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (resolution.isNotBlank()) {
                            Text(
                                text = "Res: $resolution",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (sizeLabel.isNotBlank()) {
                            Text(
                                text = "Size: $sizeLabel",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    AndroidView(
                        factory = {
                            videoView.apply {
                                setVideoURI(Uri.parse(lastVideoUri))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    mp.setVolume(0f, 0f)
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlay(lastVideoUri)
                    }, enabled = !isTrimming) {
                        Text("Play")
                    }
                    OutlinedButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShare(lastVideoUri)
                    }, enabled = !isTrimming) {
                        Text("Share")
                    }
                    OutlinedButton(onClick = onClearStatus, enabled = status.isNotBlank()) {
                        Text("Clear")
                    }
                }

                RangeSlider(
                    value = startMs..endMs,
                    onValueChange = { range ->
                        startMs = range.start.coerceIn(0f, durationMs.toFloat())
                        val minEnd = (startMs + minGapMs).coerceAtMost(durationMs.toFloat())
                        endMs = range.endInclusive.coerceIn(minEnd, durationMs.toFloat())
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    steps = 20
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Start ${formatTimeMs(startMs.toLong())}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "End ${formatTimeMs(endMs.toLong())}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            val end = minOf(durationMs.toFloat(), 15_000f)
                            onTrim(0, end.toLong())
                            onStatus("Trimming first 15s...")
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        label = { Text("Head 15s") }
                    )
                    AssistChip(
                        onClick = {
                            val start = (durationMs - 15_000).coerceAtLeast(0).toFloat()
                            onTrim(start.toLong(), durationMs)
                            onStatus("Trimming last 15s...")
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        label = { Text("Tail 15s") }
                    )
                    AssistChip(
                        onClick = {
                            val centerStart = (durationMs / 2 - 7_500).coerceAtLeast(0).toFloat()
                            val centerEnd = (centerStart + 15_000).coerceAtMost(durationMs.toFloat())
                            onTrim(centerStart.toLong(), centerEnd.toLong())
                            onStatus("Trimming middle 15s...")
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        label = { Text("Mid 15s") }
                    )
                }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTrim(startMs.toLong(), endMs.toLong())
                    },
                    enabled = !isTrimming && (endMs - startMs) > minGapMs && durationMs > 0
                ) {
                    if (isTrimming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isTrimming) "Trimming" else "Trim & Save")
                }
            }

            if (status.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = status,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onClearStatus) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

private fun Float.format(decimals: Int): String = String.format("%.${decimals}f", this)

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun humanFileSize(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val size = pfd.statSize
            if (size <= 0) return@use ""
            val kb = size / 1024.0
            val mb = kb / 1024.0
            when {
                mb >= 1 -> "${"%.2f".format(mb)} MB"
                kb >= 1 -> "${"%.0f".format(kb)} KB"
                else -> "$size B"
            }
        } ?: ""
    }.getOrDefault("")
}
