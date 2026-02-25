package com.tazztone.losslesscut.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color.White,
    secondary = SurfaceVariant,
    onSecondary = Color.White,
    surface = DarkGray,
    onSurface = TextColor,
    onSurfaceVariant = OnSurfaceVariant,
    background = DeepDark,
    onBackground = TextColor
)

@Composable
fun LosslessCutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
