package com.karelisio.somna.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * A stylised hypnogram: one dip per sleep cycle (deep sleep shallower as the night goes on), drawn
 * progressively when [cycles] changes, with a glowing dot travelling along it.
 */
@Composable
fun CycleWave(
    cycles: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    accent: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    animate: Boolean = true,
) {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(cycles) {
        draw.snapTo(0f)
        draw.animateTo(1f, tween(durationMillis = 900 + cycles * 120))
    }
    val travel = if (animate) {
        rememberInfiniteTransition(label = "wave").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
            label = "travel",
        ).value
    } else {
        1f
    }
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier) {
        val n = cycles.coerceAtLeast(1)
        val stroke = 4.dp.toPx()
        val top = stroke * 2
        val bottom = size.height - stroke * 2
        val w = size.width
        path.reset()
        val samples = 40
        for (k in 0 until n) {
            val depth = 1f - 0.13f * k
            for (i in 0..samples) {
                val t = i / samples.toFloat()
                // Fast descent into deep sleep, slower climb towards REM.
                val shaped = sin(PI * t.toDouble().pow(0.8)).toFloat().coerceAtLeast(0f)
                val y = top + (bottom - top) * depth * shaped.pow(1.3f)
                val x = (k + t) / n * w
                if (k == 0 && i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        // Track
        drawPath(path, trackColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
        // Animated reveal
        measure.setPath(path, false)
        val length = measure.length
        segment.reset()
        measure.getSegment(0f, length * draw.value, segment, true)
        drawPath(
            segment,
            brush = Brush.horizontalGradient(listOf(color, accent)),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Cycle boundaries: light-sleep moments, the good times to wake up.
        for (k in 1..n) {
            val x = k / n.toFloat() * w
            if (k / n.toFloat() <= draw.value + 0.001f) {
                drawCircle(if (k == n) accent else color, radius = stroke * (if (k == n) 1.6f else 1.1f), center = Offset(x.coerceAtMost(w - stroke * 1.6f), top))
            }
        }
        // Travelling glow
        if (draw.value >= 1f && animate) {
            val pos = measure.getPosition(length * travel)
            drawCircle(accent.copy(alpha = 0.25f), radius = stroke * 3.2f, center = pos)
            drawCircle(accent, radius = stroke * 1.2f, center = pos)
        }
    }
}
