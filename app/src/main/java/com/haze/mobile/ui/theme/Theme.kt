package com.haze.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HazeScheme = darkColorScheme(
    primary = HazeColors.Accent,
    onPrimary = Color(0xFF000000),
    background = HazeColors.Bg,
    onBackground = HazeColors.Text,
    surface = HazeColors.Surface,
    onSurface = HazeColors.Text,
    surfaceVariant = HazeColors.Surface2,
    onSurfaceVariant = HazeColors.Text2,
    error = HazeColors.Red,
    outline = HazeColors.Border2,
)

@Composable
fun HazeTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Haze is always dark, regardless of the system setting.
    MaterialTheme(
        colorScheme = HazeScheme,
        typography = Typography(),
        content = content,
    )
}
