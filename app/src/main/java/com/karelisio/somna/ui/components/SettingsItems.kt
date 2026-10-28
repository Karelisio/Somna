package com.karelisio.somna.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Position of an item inside a [SettingsGroup], for the segmented "connected card" look. */
private val LocalGroupShape = compositionLocalOf<Shape> { RoundedCornerShape(24.dp) }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Group of rows rendered as connected rounded cards (large outer corners, small inner ones). */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, items: List<@Composable () -> Unit>) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { index, item ->
            val big = 26.dp
            val small = 6.dp
            val shape = RoundedCornerShape(
                topStart = if (index == 0) big else small,
                topEnd = if (index == 0) big else small,
                bottomStart = if (index == items.lastIndex) big else small,
                bottomEnd = if (index == items.lastIndex) big else small,
            )
            CompositionLocalProvider(LocalGroupShape provides shape) { item() }
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    below: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Surface(
        shape = LocalGroupShape.current,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction, 0.98f) else Modifier),
    ) {
        Column(
            Modifier
                .then(
                    if (onClick != null) Modifier.clickable(interaction, indication = androidx.compose.material3.ripple()) {
                        haptics.click()
                        onClick()
                    } else Modifier,
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    trailing()
                }
            }
            if (below != null) {
                Spacer(Modifier.size(8.dp))
                below()
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = if (enabled) ({ haptics.toggle(!checked); onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                },
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        },
    )
}

@Composable
fun SettingsNav(title: String, subtitle: String? = null, icon: ImageVector? = null, badge: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    SettingsRow(
        title = title, subtitle = subtitle, icon = icon, onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badge?.invoke()
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
fun SettingsSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: (Float) -> String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        trailing = {
            RollingText(label(value), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        },
        below = {
            Slider(
                value = value,
                onValueChange = {
                    if (steps > 0 && it.roundToInt() != value.roundToInt()) haptics.tick()
                    onValueChange(it)
                },
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
            )
        },
    )
}

/** Connected toggle buttons (M3 Expressive button group) for a small set of options. */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
) {
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = {
                    haptics.tick()
                    onSelect(option)
                },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = FilledTonalToggleButtonDefaults.colors(),
            ) {
                val i = icon?.invoke(option)
                if (i != null) {
                    Icon(i, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
