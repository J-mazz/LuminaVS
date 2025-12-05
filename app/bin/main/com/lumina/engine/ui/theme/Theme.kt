package com.lumina.engine.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Lumina color palette
private val LuminaPrimary = Color(0xFF66D9FF)
private val LuminaSecondary = Color(0xFF7C4DFF)
private val LuminaTertiary = Color(0xFFFF6B9D)
private val LuminaBackground = Color(0xFF0D1B2A)
private val LuminaSurface = Color(0xFF1B263B)
private val LuminaSurfaceVariant = Color(0xFF415A77)

private val DarkColorScheme = darkColorScheme(
    primary = LuminaPrimary,
    secondary = LuminaSecondary,
    tertiary = LuminaTertiary,
    background = LuminaBackground,
    surface = LuminaSurface,
    surfaceVariant = LuminaSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.8f)
)

@Composable
fun LuminaVSTheme(
    darkTheme: Boolean = true, // Always dark for glassmorphic effect
    dynamicColor: Boolean = false, // Optional dynamic accents
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
