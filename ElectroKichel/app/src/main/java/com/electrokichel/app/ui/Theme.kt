package com.electrokichel.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EkBg = Color(0xFF070B14)
val EkBg2 = Color(0xFF0C1220)
val EkCard = Color(0xFF0F1830)
val EkCard2 = Color(0xFF0B1425)
val EkBorder = Color(0xFF1C2C4D)
val EkText = Color(0xFFE7ECF7)
val EkDim = Color(0xFF8896B3)
val EkBlue = Color(0xFF3AA6FF)
val EkBrandBlue = Color(0xFF1A56C4)
val EkRed = Color(0xFFE53935)
val EkGreen = Color(0xFF22E08A)
val EkOrange = Color(0xFFFFB347)
val EkPurple = Color(0xFFA97BFF)
val EkTeal = Color(0xFF2FE5C7)

private val scheme = darkColorScheme(
    primary = EkBlue, onPrimary = Color(0xFF04101F),
    secondary = EkGreen, onSecondary = Color(0xFF04150E),
    background = EkBg, onBackground = EkText,
    surface = EkCard, onSurface = EkText,
    surfaceVariant = EkCard2, onSurfaceVariant = EkDim,
    error = EkRed, outline = EkBorder,
)

@Composable
fun ElectroTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
