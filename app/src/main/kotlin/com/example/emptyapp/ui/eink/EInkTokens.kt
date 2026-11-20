package com.example.emptyapp.ui.eink

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object EInkShapes {
  val Cover = RoundedCornerShape(6.dp)
  val Badge = RoundedCornerShape(6.dp)
  val Control = RoundedCornerShape(10.dp)
  val Card = RoundedCornerShape(12.dp)
  val Hero = RoundedCornerShape(14.dp)
  val SheetTop = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
  val Pill = RoundedCornerShape(50)
}

object EInkBorder {
  val Hairline = 0.75.dp
  val Card = 1.dp
  val Active = 1.5.dp
}
