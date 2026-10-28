package com.karelisio.somna.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** Text whose characters roll vertically when they change (clock digits, counters). */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spatial) { if (up) it else -it } + fadeIn(effects))
                        .togetherWith(slideOutVertically(spatial) { if (up) -it else it } + fadeOut(effects))
                        .using(SizeTransform(clip = false))
                },
                label = "roll$index",
            ) { c ->
                Text(c.toString(), style = style, color = color)
            }
        }
    }
}

/** Integer that counts up/down with a spring, formatted by [format]. */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    format: (Int) -> String = { it.toString() },
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "counter",
    )
    Text(format(animated), modifier = modifier, style = style, color = color)
}
