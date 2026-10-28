package com.karelisio.somna.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Card with a springy press effect and a subtle haptic tap. */
@Composable
fun SpringCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    haptic: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Card(
        onClick = {
            if (haptic) haptics.click()
            onClick()
        },
        modifier = modifier.pressScale(interaction, pressedScale),
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        interactionSource = interaction,
        content = content,
    )
}
