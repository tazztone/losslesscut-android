package com.tazztone.losslesscut.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

fun getAccentColor(name: String?): Color {
    return when (name?.lowercase()) {
        "purple" -> PurpleAccent
        "green" -> GreenAccent
        "yellow" -> YellowAccent
        "red" -> RedAccent
        "orange" -> OrangeAccent
        else -> CyanAccent
    }
}

private val BaseDarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color.Black,
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
    accentColorName: String? = null,
    content: @Composable () -> Unit
) {
    val accentColor = remember(accentColorName) { getAccentColor(accentColorName) }
    val colorScheme = remember(accentColor) {
        BaseDarkColorScheme.copy(
            primary = accentColor,
            onPrimary = if (accentColor == YellowAccent || accentColor == CyanAccent || accentColor == GreenAccent) {
                Color.Black
            } else {
                Color.White
            }
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

