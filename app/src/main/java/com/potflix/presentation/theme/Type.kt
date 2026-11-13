package com.potflix.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

// The SAMFLIX type scale. Screens pick one of these styles instead of setting sizes themselves.
val Typography = Typography(
    headlineLarge = style(32, 40, FontWeight.Bold),
    headlineMedium = style(28, 34, FontWeight.Bold),
    headlineSmall = style(24, 30, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold, 0.1),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.2),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.2),
    bodySmall = style(12, 16, FontWeight.Normal, 0.3),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.3),
    labelSmall = style(11, 16, FontWeight.Medium, 0.4)
)
