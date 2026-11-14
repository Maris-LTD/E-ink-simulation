package com.example.emptyapp.ui.eink

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.emptyapp.R
import com.example.emptyapp.reader.EInkTone

private fun eInkColorScheme(tone: EInkTone): ColorScheme {
  val bg = Color(tone.background)
  val fg = Color(tone.text)
  if (tone == EInkTone.DARK) {
    return darkColorScheme(
      primary = fg,
      onPrimary = bg,
      secondary = lerp(fg, bg, 0.15f),
      onSecondary = bg,
      background = bg,
      onBackground = fg,
      surface = bg,
      onSurface = fg,
      surfaceVariant = lerp(bg, fg, 0.08f),
      onSurfaceVariant = fg,
      outline = lerp(bg, fg, 0.5f),
      outlineVariant = lerp(bg, fg, 0.22f),
    )
  }
  return lightColorScheme(
    primary = fg,
    onPrimary = bg,
    secondary = lerp(fg, bg, 0.12f),
    onSecondary = bg,
    background = bg,
    onBackground = fg,
    surface = bg,
    onSurface = fg,
    surfaceVariant = lerp(bg, fg, 0.06f),
    onSurfaceVariant = fg,
    outline = fg,
    outlineVariant = lerp(bg, fg, 0.18f),
  )
}

@OptIn(ExperimentalTextApi::class)
private val EInkSansFamily =
  FontFamily(
    Font(
      resId = R.font.rubik,
      weight = FontWeight.Light,
      variationSettings = FontVariation.Settings(FontVariation.weight(300)),
    ),
    Font(
      resId = R.font.rubik,
      weight = FontWeight.Normal,
      variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
      resId = R.font.rubik,
      weight = FontWeight.Medium,
      variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
      resId = R.font.rubik,
      weight = FontWeight.SemiBold,
      variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
      resId = R.font.rubik,
      weight = FontWeight.Bold,
      variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
      resId = R.font.rubik,
      weight = FontWeight.ExtraBold,
      variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
  )

@Composable
fun EInkTheme(
  tone: EInkTone = EInkTone.HIGH_CONTRAST,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = eInkColorScheme(tone), typography = eInkTypography(), content = content)
}

private fun TextStyle.sans() = copy(fontFamily = EInkSansFamily)

private fun eInkTypography(): Typography {
  val base = Typography()
  return base.copy(
    displayLarge = base.displayLarge.sans(),
    displayMedium = base.displayMedium.sans(),
    displaySmall = base.displaySmall.sans(),
    headlineLarge =
      base.headlineLarge.sans().copy(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
      ),
    headlineMedium =
      base.headlineMedium.sans().copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
      ),
    headlineSmall = base.headlineSmall.sans(),
    titleLarge =
      base.titleLarge.sans().copy(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
      ),
    titleMedium =
      base.titleMedium.sans().copy(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
      ),
    titleSmall =
      base.titleSmall.sans().copy(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
      ),
    bodyLarge = base.bodyLarge.sans().copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.sans().copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.sans().copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    labelLarge =
      base.labelLarge.sans().copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
      ),
    labelMedium =
      base.labelMedium.sans().copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
      ),
    labelSmall =
      base.labelSmall.sans().copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
      ),
  )
}


