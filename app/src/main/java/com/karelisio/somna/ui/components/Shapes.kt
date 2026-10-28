package com.karelisio.somna.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** Shape morphing between two Material shapes, driven by [progress] (0 = start, 1 = end). */
class MorphShape(
    private val morph: Morph,
    private val progress: () -> Float,
    private val rotation: () -> Float = { 0f },
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        var first = true
        morph.asCubics(progress()).forEach { c ->
            if (first) {
                path.moveTo(c.anchor0X, c.anchor0Y)
                first = false
            }
            path.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
        }
        path.close()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        val bounds = path.getBounds()
        path.translate(Offset(size.width / 2f - bounds.center.x, size.height / 2f - bounds.center.y))
        val angle = rotation()
        if (angle != 0f) {
            val rotate = Matrix()
            rotate.translate(size.width / 2f, size.height / 2f)
            rotate.rotateZ(angle)
            rotate.translate(-size.width / 2f, -size.height / 2f)
            path.transform(rotate)
        }
        return Outline.Generic(path)
    }
}

/** Animated morph between [from] and [to] following [selected], with the expressive spatial spring. */
@Composable
fun rememberMorphShape(from: RoundedPolygon, to: RoundedPolygon, selected: Boolean, spin: Boolean = false): Shape {
    val morph = remember(from, to) { Morph(from, to) }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "morph",
    )
    val rotation by animateFloatAsState(
        targetValue = if (selected && spin) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "morphRotation",
    )
    return remember(morph) { MorphShape(morph, { progress }, { rotation }) }
}

/** Content sitting on a (slowly turning) Material shape: cookie, sunny, flower… */
@Composable
fun ShapeContainer(
    polygon: RoundedPolygon,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    rotate: Boolean = true,
    rotationPeriodMs: Int = 12_000,
    content: @Composable () -> Unit = {},
) {
    val angle = if (rotate) {
        val transition = rememberInfiniteTransition(label = "shape")
        transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(rotationPeriodMs, easing = LinearEasing), RepeatMode.Restart),
            label = "shapeAngle",
        ).value
    } else {
        0f
    }
    val shape = remember(polygon) { MorphShape(Morph(polygon, polygon), { 0f }) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = angle }
                .clip(shape)
                .background(containerColor),
        )
        content()
    }
}

/** An icon on a slowly turning Material shape. */
@Composable
fun ShapeBadge(
    polygon: RoundedPolygon,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    rotate: Boolean = true,
    rotationPeriodMs: Int = 12_000,
    iconSize: Dp = size * 0.46f,
) {
    ShapeContainer(polygon, modifier, size, containerColor, rotate, rotationPeriodMs) {
        if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}
