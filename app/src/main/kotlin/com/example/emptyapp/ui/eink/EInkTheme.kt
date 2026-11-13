package com.example.emptyapp.ui.eink

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

private val Paper = Color(0xFFE4E0D4)
private val Ink = Color(0xFF1A1A1A)
private val Mid = Color(0xFF6B6B66)
private val Panel = Color(0xFFD6D2C6)

private val EInkColorScheme =
  lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Mid,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = Ink,
    outline = Ink,
    outlineVariant = Mid,
  )

private fun TextStyle.serif() = copy(fontFamily = FontFamily.Serif)

private fun serifTypography(): Typography {
  val base = Typography()
  return base.copy(
    headlineMedium = base.headlineMedium.serif(),
    titleLarge = base.titleLarge.serif(),
    titleMedium = base.titleMedium.serif(),
    bodyLarge = base.bodyLarge.serif(),
    bodyMedium = base.bodyMedium.serif(),
    bodySmall = base.bodySmall.serif(),
    labelLarge = base.labelLarge.serif(),
    labelMedium = base.labelMedium.serif(),
  )
}

@Composable
fun EInkTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = EInkColorScheme, typography = serifTypography(), content = content)
}
