package com.example.emptyapp.ui.eink

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
      secondary = lerp(fg, bg, 0.38f),
      onSecondary = bg,
      background = bg,
      onBackground = fg,
      surface = bg,
      onSurface = fg,
      surfaceVariant = lerp(bg, fg, 0.09f),
      onSurfaceVariant = fg,
      outline = lerp(fg, bg, 0.58f),
      outlineVariant = lerp(fg, bg, 0.80f),
    )
  }
  return lightColorScheme(
    primary = fg,
    onPrimary = bg,
    secondary = lerp(fg, bg, 0.36f),
    onSecondary = bg,
    background = bg,
    onBackground = fg,
    surface = bg,
    onSurface = fg,
    surfaceVariant = lerp(bg, fg, 0.055f),
    onSurfaceVariant = fg,
    outline = lerp(fg, bg, 0.65f),
    outlineVariant = lerp(fg, bg, 0.84f),
  )
}

@OptIn(ExperimentalTextApi::class)
private val EInkSansFamily =
  FontFamily(
    Font(
      resId = R.font.jakarta,
      weight = FontWeight.Light,
      variationSettings = FontVariation.Settings(FontVariation.weight(300)),
    ),
    Font(
      resId = R.font.jakarta,
      weight = FontWeight.Normal,
      variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
      resId = R.font.jakarta,
      weight = FontWeight.Medium,
      variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
      resId = R.font.jakarta,
      weight = FontWeight.SemiBold,
      variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
      resId = R.font.jakarta,
      weight = FontWeight.Bold,
      variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
      resId = R.font.jakarta,
      weight = FontWeight.ExtraBold,
      variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
  )

@OptIn(ExperimentalTextApi::class)
val EInkSerifFamily =
  FontFamily(
    Font(
      resId = R.font.bitter,
      weight = FontWeight.Normal,
      variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
      resId = R.font.bitter,
      weight = FontWeight.Medium,
      variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
      resId = R.font.bitter,
      weight = FontWeight.SemiBold,
      variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
      resId = R.font.bitter,
      weight = FontWeight.Bold,
      variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
      resId = R.font.bitter,
      weight = FontWeight.ExtraBold,
      variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
  )

private val EInkMaterialShapes =
  Shapes(
    extraSmall = EInkShapes.Badge,
    small = EInkShapes.Control,
    medium = EInkShapes.Card,
    large = EInkShapes.Hero,
  )

@Composable
fun EInkTheme(
  tone: EInkTone = EInkTone.HIGH_CONTRAST,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = eInkColorScheme(tone),
    typography = eInkTypography(),
    shapes = EInkMaterialShapes,
    content = content,
  )
}

private fun TextStyle.sans() = copy(fontFamily = EInkSansFamily)
private fun TextStyle.serif() = copy(fontFamily = EInkSerifFamily)

private fun eInkTypography(): Typography {
  val base = Typography()
  return base.copy(
    displayLarge = base.displayLarge.serif(),
    displayMedium = base.displayMedium.serif(),
    displaySmall = base.displaySmall.serif(),
    headlineLarge =
      base.headlineLarge.serif().copy(
        fontSize = 27.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
      ),
    headlineMedium =
      base.headlineMedium.serif().copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
      ),
    headlineSmall = base.headlineSmall.serif(),
    titleLarge =
      base.titleLarge.serif().copy(
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


