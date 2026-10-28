package com.karelisio.somna.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Fallback "bleu nuit" palette used when dynamic colors are unavailable or disabled. */
val NightDarkScheme = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF14237A),
    primaryContainer = Color(0xFF2E3C91),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC8BFFF),
    onSecondary = Color(0xFF2F2475),
    secondaryContainer = Color(0xFF463C8D),
    onSecondaryContainer = Color(0xFFE5DEFF),
    tertiary = Color(0xFFFFB59D),
    onTertiary = Color(0xFF561F0F),
    tertiaryContainer = Color(0xFF733523),
    onTertiaryContainer = Color(0xFFFFDBD0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B1022),
    onBackground = Color(0xFFE3E1EC),
    surface = Color(0xFF0B1022),
    onSurface = Color(0xFFE3E1EC),
    surfaceVariant = Color(0xFF45465A),
    onSurfaceVariant = Color(0xFFC6C5DA),
    outline = Color(0xFF8F8FA6),
    outlineVariant = Color(0xFF45465A),
    inverseSurface = Color(0xFFE3E1EC),
    inverseOnSurface = Color(0xFF2F3040),
    inversePrimary = Color(0xFF4656AC),
    surfaceDim = Color(0xFF0B1022),
    surfaceBright = Color(0xFF32374D),
    surfaceContainerLowest = Color(0xFF070B1A),
    surfaceContainerLow = Color(0xFF13182B),
    surfaceContainer = Color(0xFF171C30),
    surfaceContainerHigh = Color(0xFF21263B),
    surfaceContainerHighest = Color(0xFF2C3146),
)

val NightLightScheme = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5891),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3DFFF),
    onSecondaryContainer = Color(0xFF17134A),
    tertiary = Color(0xFF8F4C38),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBD0),
    onTertiaryContainer = Color(0xFF3A0B01),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF3EFF7),
    inversePrimary = Color(0xFFBAC3FF),
    surfaceDim = Color(0xFFDBD9E0),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
)

/** Sunrise palette for the progressive light (night → dawn → day). */
object SunriseColors {
    val night = listOf(Color(0xFF02030A), Color(0xFF070B1E), Color(0xFF0B1230))
    val dawn = listOf(Color(0xFF1B1446), Color(0xFF5B2A6E), Color(0xFFC2547A))
    val sunrise = listOf(Color(0xFF6A3D8F), Color(0xFFF07A5A), Color(0xFFFFB86B))
    val day = listOf(Color(0xFF8EC5FF), Color(0xFFFFD9A0), Color(0xFFFFF1D0))
    val sun = Color(0xFFFFE6A8)
    val sunCore = Color(0xFFFFF7E0)
}

/** Success green for granted permissions (Material You has no green role). */
object SuccessColors {
    val light = Color(0xFF1B873F)
    val lightContainer = Color(0xFFC4F3CF)
    val dark = Color(0xFF7DDC93)
    val darkContainer = Color(0xFF14512A)
}
