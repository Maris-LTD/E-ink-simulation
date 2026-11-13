package com.example.emptyapp.ui.eink

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.emptyapp.R

private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF000000)
private val Mid = Color(0xFF111111)
private val Panel = Color(0xFFF2F2F2)
private val DividerLight = Color(0xFFD0D0D0)

@OptIn(ExperimentalTextApi::class)
private val EInkSansFamily =
  FontFamily(
    Font(
      resId = R.font.rubik,
      weight = FontWeight.Normal,
      variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
    Font(
      resId = R.font.rubik,
      weight = FontWeight.Medium,
      variationSettings = FontVariation.Settings(FontVariation.weight(850)),
    ),
    Font(
      resId = R.font.rubik,
      weight = FontWeight.SemiBold,
      variationSettings = FontVariation.Settings(FontVariation.weight(900)),
    ),
    Font(
      resId = R.font.rubik,
      weight = FontWeight.Bold,
      variationSettings = FontVariation.Settings(FontVariation.weight(900)),
    ),
  )

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
    outlineVariant = DividerLight,
  )

private fun TextStyle.sans() = copy(fontFamily = EInkSansFamily)

private fun eInkTypography(): Typography {
  val base = Typography()
  return base.copy(
    headlineMedium =
      base.headlineMedium.sans().copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
      ),
    titleLarge =
      base.titleLarge.sans().copy(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
      ),
    titleMedium =
      base.titleMedium.sans().copy(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
      ),
    bodyLarge = base.bodyLarge.sans().copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.sans().copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.sans().copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge =
      base.labelLarge.sans().copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
      ),
    labelMedium =
      base.labelMedium.sans().copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
      ),
    labelSmall =
      base.labelSmall.sans().copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
      ),
  )
}

@Composable
fun EInkTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = EInkColorScheme, typography = eInkTypography(), content = content)
}
