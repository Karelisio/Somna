package com.karelisio.somna.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Springy press feedback: the element sinks slightly while pressed. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.95f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Remembers which list items already played their entrance, so scrolling back doesn't replay it. */
@Stable
class StaggerState {
    internal val seen = mutableSetOf<Any>()
}

@Composable
fun rememberStaggerState(vararg keys: Any?): StaggerState = remember(*keys) { StaggerState() }

/** Cascading entrance: fade + rise + slight scale, delayed by the item index. */
@Composable
fun Modifier.staggerEnter(state: StaggerState, key: Any, index: Int): Modifier {
    val alreadySeen = key in state.seen
    val progress = remember(key) { Animatable(if (alreadySeen) 1f else 0f) }
    LaunchedEffect(key) {
        if (!alreadySeen) {
            state.seen += key
            delay(index.coerceAtMost(10) * 45L)
            progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 40.dp.toPx()
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}
