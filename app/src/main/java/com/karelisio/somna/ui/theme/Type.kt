package com.karelisio.somna.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.karelisio.somna.R

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

private val base = Typography()

val SomnaTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em),
    displayMedium = base.displayMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em),
    displaySmall = base.displaySmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em),
    headlineLarge = base.headlineLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = Figtree, letterSpacing = 0.1.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Figtree, letterSpacing = 0.1.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Figtree),
    labelLarge = base.labelLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
)

/** Hero clock digits. */
val ClockStyle = TextStyle(
    fontFamily = Outfit,
    fontWeight = FontWeight.Medium,
    fontSize = 88.sp,
    lineHeight = 92.sp,
    letterSpacing = (-0.03).em,
)
