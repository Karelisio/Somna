package com.karelisio.somna.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.karelisio.somna.ui.theme.SunriseColors
import kotlin.math.sin
import kotlin.random.Random

private fun lerpPalette(a: List<Color>, b: List<Color>, t: Float) = a.indices.map { lerp(a[it], b[it], t) }

/** Sky colours for a sunrise progress in 0..1 (night → dawn → sunrise → day). */
fun sunrisePalette(progress: Float): List<Color> {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p < 0.35f -> lerpPalette(SunriseColors.night, SunriseColors.dawn, p / 0.35f)
        p < 0.7f -> lerpPalette(SunriseColors.dawn, SunriseColors.sunrise, (p - 0.35f) / 0.35f)
        else -> lerpPalette(SunriseColors.sunrise, SunriseColors.day, (p - 0.7f) / 0.3f)
    }
}

/** Full-screen sky with a rising sun and fading stars. [progress] 0 = night, 1 = full daylight. */
@Composable
fun SunriseSky(progress: Float, modifier: Modifier = Modifier, pulse: Boolean = false) {
    val stars = remember { List(70) { Triple(Random.nextFloat(), Random.nextFloat() * 0.75f, Random.nextFloat()) } }
    val transition = rememberInfiniteTransition(label = "sky")
    val twinkle = transition.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(5_000, easing = LinearEasing), RepeatMode.Restart), label = "twinkle",
    ).value
    val breathe = transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_400), RepeatMode.Reverse), label = "breathe",
    ).value
    Canvas(modifier) {
        val p = progress.coerceIn(0f, 1f)
        val palette = sunrisePalette(p)
        drawRect(Brush.verticalGradient(listOf(palette[0], palette[1], palette[2])))

        val starAlpha = (1f - p * 2.2f).coerceIn(0f, 1f)
        if (starAlpha > 0f) {
            stars.forEach { (x, y, phase) ->
                val a = starAlpha * (0.45f + 0.55f * (0.5f + 0.5f * sin(twinkle + phase * 6.28f)))
                drawCircle(Color.White.copy(alpha = a), radius = 1.2f + phase * 1.6f, center = Offset(x * size.width, y * size.height))
            }
        }

        // The sun rises from below the horizon to two thirds of the screen.
        val sunY = size.height * (1.18f - 0.52f * p)
        val radius = size.minDimension * (0.16f + 0.04f * p) * (if (pulse) 1f + 0.04f * breathe else 1f)
        val center = Offset(size.width / 2f, sunY)
        val glow = (0.25f + 0.75f * p)
        drawCircle(
            Brush.radialGradient(
                listOf(SunriseColors.sun.copy(alpha = 0.55f * glow), SunriseColors.sun.copy(alpha = 0f)),
                center = center, radius = radius * 4.2f,
            ),
            radius = radius * 4.2f, center = center,
        )
        drawCircle(
            Brush.radialGradient(listOf(SunriseColors.sunCore, SunriseColors.sun), center = center, radius = radius),
            radius = radius, center = center, alpha = (0.3f + 0.7f * p).coerceIn(0f, 1f),
        )
    }
}
