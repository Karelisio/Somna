package com.karelisio.somna.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.somna.data.ThemeMode
import java.time.LocalTime
import kotlinx.coroutines.delay

val SomnaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/** Night = 20:00 → 07:00: Somna defaults to dark at night. */
fun isNightTime(time: LocalTime = LocalTime.now()): Boolean = time.hour >= 20 || time.hour < 7

@Composable
fun rememberIsNight(): Boolean {
    var night by remember { mutableStateOf(isNightTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            night = isNightTime()
        }
    }
    return night
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean {
    val system = isSystemInDarkTheme()
    val night = rememberIsNight()
    return when (mode) {
        ThemeMode.AUTO_NIGHT -> system || night
        ThemeMode.SYSTEM -> system
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

@Composable
fun somnaColorScheme(dark: Boolean, dynamic: Boolean): ColorScheme {
    val context = LocalContext.current
    return when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> NightDarkScheme
        else -> NightLightScheme
    }
}

@Composable
fun SomnaTheme(
    themeMode: ThemeMode = ThemeMode.AUTO_NIGHT,
    dynamicColor: Boolean = true,
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = darkOverride ?: isDarkTheme(themeMode)
    MaterialExpressiveTheme(
        colorScheme = somnaColorScheme(dark, dynamicColor),
        motionScheme = MotionScheme.expressive(),
        shapes = SomnaShapes,
        typography = SomnaTypography,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark, content = content)
    }
}

val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }
