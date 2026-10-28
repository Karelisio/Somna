package com.karelisio.somna.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.somna.ui.theme.LocalDarkTheme
import com.karelisio.somna.ui.theme.SuccessColors
import kotlinx.coroutines.launch

/** Green check that pops in with an overshoot and draws its tick (synced with a confirm haptic). */
@Composable
fun AnimatedCheck(modifier: Modifier = Modifier, size: Dp = 48.dp, animate: Boolean = true) {
    val dark = LocalDarkTheme.current
    val container = if (dark) SuccessColors.darkContainer else SuccessColors.lightContainer
    val tick = if (dark) SuccessColors.dark else SuccessColors.light
    val scale = remember { Animatable(if (animate) 0f else 1f) }
    val draw = remember { Animatable(if (animate) 0f else 1f) }
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        kotlinx.coroutines.delay(120)
        haptics.confirm()
        draw.animateTo(1f, tween(320))
    }
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    ) {
        drawCircle(container)
        val w = this.size.width
        path.reset()
        path.moveTo(w * 0.28f, w * 0.52f)
        path.lineTo(w * 0.44f, w * 0.67f)
        path.lineTo(w * 0.73f, w * 0.36f)
        measure.setPath(path, false)
        segment.reset()
        measure.getSegment(0f, measure.length * draw.value, segment, true)
        drawPath(segment, tick, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
