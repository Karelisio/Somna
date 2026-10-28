package com.karelisio.somna.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.karelisio.somna.ui.components.CycleWave
import com.karelisio.somna.ui.components.ShapeContainer
import com.karelisio.somna.ui.components.SunriseSky
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Somna's emblem, alive: a crescent moon floating above flowing sleep-cycle waves. */
@Composable
fun MoonWavesIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "moon")
    val phase by transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "phase")
    val float by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(3_000), RepeatMode.Reverse), label = "float")
    val twinkle by transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "twinkle")
    val stars = remember { List(14) { Triple(Random.nextFloat(), Random.nextFloat() * 0.55f, Random.nextFloat()) } }
    val moon = MaterialTheme.colorScheme.tertiary
    val waves = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    Box(modifier.size(280.dp), contentAlignment = Alignment.Center) {
        ShapeContainer(
            MaterialShapes.Cookie12Sided,
            size = 260.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            rotationPeriodMs = 50_000,
        )
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val w = size.width
            stars.forEach { (x, y, p) ->
                val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(twinkle + p * 6.28f))
                drawCircle(moon.copy(alpha = a), radius = 1.5.dp.toPx() + p * 2.dp.toPx(), center = Offset(x * w, y * w))
            }
            // Crescent: a disc minus an offset disc.
            val c = Offset(w * 0.5f, w * 0.36f + float * 6.dp.toPx())
            val r = w * 0.17f
            drawCircle(moon, radius = r, center = c)
            drawCircle(Color.Black, radius = r * 0.86f, center = Offset(c.x + r * 0.5f, c.y - r * 0.32f), blendMode = BlendMode.Clear)
            // Waves
            waves.forEachIndexed { i, color ->
                val path = Path()
                val y0 = w * (0.66f + i * 0.085f)
                val amp = w * (0.03f - i * 0.006f)
                val left = w * (0.2f + i * 0.06f)
                val right = w * (0.8f - i * 0.06f)
                var x = left
                path.moveTo(x, y0 + amp * sin(phase + i))
                while (x <= right) {
                    path.lineTo(x, y0 + amp * sin(phase + i + (x - left) / w * 4f * PI.toFloat()))
                    x += 2f
                }
                drawPath(path, color, style = Stroke(width = (7 - i * 1.5f).dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun CyclesIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        ShapeContainer(
            MaterialShapes.SoftBurst,
            size = 300.dp,
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            rotationPeriodMs = 60_000,
        )
        CycleWave(5, Modifier.fillMaxWidth(0.82f).height(120.dp))
    }
}

@Composable
fun SunriseIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sunriseDemo")
    val progress by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse), label = "sunriseProgress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        SunriseSky(progress, Modifier.fillMaxSize())
    }
}
