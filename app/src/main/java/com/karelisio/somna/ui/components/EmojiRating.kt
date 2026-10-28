package com.karelisio.somna.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val RatingEmojis = listOf("😴", "😪", "😐", "🙂", "😄")
val RatingLabels = listOf("Épuisé", "Fatigué", "Moyen", "Bien", "En pleine forme")

fun ratingEmoji(rating: Int): String = RatingEmojis.getOrElse(rating - 1) { "·" }

/** Wake-up mood picker from 😴 to 😄, with a springy selection morphing into a cookie shape. */
@Composable
fun EmojiRating(
    rating: Int,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemSize: Dp = 56.dp,
) {
    val haptics = rememberHaptics()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RatingEmojis.forEachIndexed { index, emoji ->
            val value = index + 1
            val selected = rating == value
            val scale by animateFloatAsState(
                if (selected) 1.18f else if (rating == 0) 1f else 0.9f,
                MaterialTheme.motionScheme.defaultSpatialSpec(), label = "emojiScale",
            )
            val container by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                MaterialTheme.motionScheme.defaultEffectsSpec(), label = "emojiBg",
            )
            val shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, selected, spin = true)
            Box(
                Modifier
                    .size(itemSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(if (selected) shape else CircleShape)
                    .background(container)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        haptics.confirm()
                        onRate(value)
                    }
                    .semantics { contentDescription = RatingLabels[index] },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = (itemSize.value * 0.5f).sp)
            }
        }
    }
}
