package com.manish.vahakvigil.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.R

private val Manrope = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.manrope_variable, FontWeight.Light),
    androidx.compose.ui.text.font.Font(R.font.manrope_variable, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.manrope_variable, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.manrope_variable, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.manrope_variable, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(R.font.manrope_variable, FontWeight.ExtraBold)
)

private val MaterialTypography = Typography()

/** Locally bundled Latin typeface; Android's script fallback supplies Devanagari glyphs. */
val Typography = MaterialTypography.copy(
    displayLarge = MaterialTypography.displayLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    displayMedium = MaterialTypography.displayMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    displaySmall = MaterialTypography.displaySmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    headlineLarge = MaterialTypography.headlineLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    headlineMedium = MaterialTypography.headlineMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    headlineSmall = MaterialTypography.headlineSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    titleLarge = MaterialTypography.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    titleMedium = MaterialTypography.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    titleSmall = MaterialTypography.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    bodyLarge = MaterialTypography.bodyLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = MaterialTypography.bodyMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    bodySmall = MaterialTypography.bodySmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = MaterialTypography.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelMedium = MaterialTypography.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    labelSmall = MaterialTypography.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium)
)
