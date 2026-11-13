package com.potflix.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// SAMFLIX is dark only. Every role is set so Material components need no per-screen colours.
private val SamColorScheme = darkColorScheme(
    primary = SamAccent,
    onPrimary = SamOnAccent,
    primaryContainer = SamAccentPressed,
    onPrimaryContainer = SamOnAccent,
    secondary = SamAccent,
    onSecondary = SamOnAccent,
    secondaryContainer = SamSurfaceRaised,
    onSecondaryContainer = SamText,
    tertiary = SamAccent,
    onTertiary = SamOnAccent,
    background = SamBackground,
    onBackground = SamText,
    surface = SamSurface,
    onSurface = SamText,
    surfaceVariant = SamSurfaceRaised,
    onSurfaceVariant = SamTextMuted,
    surfaceContainerLowest = SamBackground,
    surfaceContainerLow = SamSurface,
    surfaceContainer = SamSurface,
    surfaceContainerHigh = SamSurfaceRaised,
    surfaceContainerHighest = SamSurfaceRaised,
    outline = SamOutline,
    outlineVariant = SamOutline,
    error = SamError,
    onError = SamOnAccent
)

@Composable
fun PotFlixTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = SamColorScheme,
        typography = Typography,
        content = content
    )
}
