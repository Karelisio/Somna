package com.karelisio.somna.ui.sounds

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.somna.audio.SoundType
import com.karelisio.somna.container
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.routine.SleepSoundService
import com.karelisio.somna.ui.LocalSomna
import com.karelisio.somna.ui.components.MorphShape
import com.karelisio.somna.ui.components.RollingText
import com.karelisio.somna.ui.components.ScreenHeader
import com.karelisio.somna.ui.components.SpringCard
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.ui.components.rememberMorphShape
import com.karelisio.somna.ui.home.LocalSnackbar
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val sleepSounds = listOf(SoundType.RAIN, SoundType.WHITE_NOISE, SoundType.FIRE, SoundType.WAVES)
private val timers = listOf(15, 30, 45, 60, 90, 0)

private fun icon(type: SoundType): ImageVector = when (type) {
    SoundType.RAIN -> Icons.Rounded.WaterDrop
    SoundType.WHITE_NOISE -> Icons.Rounded.Air
    SoundType.FIRE -> Icons.Rounded.LocalFireDepartment
    else -> Icons.Rounded.Waves
}

@Composable
fun SoundsScreen(contentPadding: PaddingValues, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val somna = LocalSomna.current
    val settings by somna.settings.collectAsStateWithLifecycle()
    val status by SleepSoundService.status.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val s = settings ?: return
    val selected = status.type ?: runCatching { SoundType.valueOf(s.sleepSound) }.getOrDefault(SoundType.RAIN)
    var volume by remember { mutableFloatStateOf(s.sleepVolume) }

    fun play(type: SoundType = selected, minutes: Int = s.sleepTimerMinutes) {
        SleepSoundService.play(context, type, minutes, volume)
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        ScreenHeader(
            title = "Endormissement",
            actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Réglages") } },
        )

        Visualizer(
            playing = status.playing,
            onToggle = {
                haptics.confirm()
                if (status.playing) SleepSoundService.stop(context) else play()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
        )

        RemainingTime(status.endsAt, status.playing, Modifier.align(Alignment.CenterHorizontally))

        Text(
            "Ambiance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sleepSounds.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { type ->
                        SoundCard(
                            type = type,
                            selected = type == selected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                somna.edit { setSleepSound(type.name) }
                                if (status.playing) play(type)
                            },
                        )
                    }
                }
            }
        }

        Text(
            "Minuterie",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp),
        )
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            timers.forEach { minutes ->
                FilterChip(
                    selected = s.sleepTimerMinutes == minutes,
                    onClick = {
                        haptics.tick()
                        somna.edit { setSleepTimerMinutes(minutes) }
                        if (status.playing) play(minutes = minutes)
                    },
                    label = { Text(if (minutes == 0) "∞" else "$minutes min") },
                    shape = MaterialTheme.shapes.small,
                )
            }
        }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VolumeDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    SleepSoundService.setVolume(context, it)
                },
                onValueChangeFinished = { somna.edit { setSleepVolume(volume) } },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Icon(Icons.Rounded.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = {
                haptics.confirm()
                play()
                scope.launch {
                    context.container.sleep.markBedtime()
                    snackbar.showSnackbar("Heure de coucher notée · bonne nuit 🌙")
                }
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(60.dp),
        ) {
            Icon(Icons.Rounded.Bedtime, null)
            Spacer(Modifier.width(10.dp))
            Text("Je vais dormir", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Le son s'arrête en fondu à la fin de la minuterie. « Je vais dormir » note ton heure de coucher pour le journal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun Visualizer(playing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    // Smoothed audio level read every frame while playing.
    val level = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (!playing) {
            level.animateTo(0f, spring())
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            val target = (SleepSoundService.level * 3.5f).coerceIn(0f, 1f)
            level.snapTo(level.value + (target - level.value) * 0.12f)
        }
    }
    val transition = rememberInfiniteTransition(label = "visualizer")
    val rotation by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "rotation",
    )
    val breathe by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(4_000), RepeatMode.Reverse), label = "breathe",
    )
    val outerMorph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst) }
    var morphProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        val anim = Animatable(morphProgress)
        anim.animateTo(if (playing) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 120f)) { morphProgress = value }
    }
    val outerShape = remember { MorphShape(outerMorph, { morphProgress }) }
    val innerShape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, playing, spin = true)
    val container by animateColorAsState(
        if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.motionScheme.slowEffectsSpec(), label = "vizColor",
    )
    val onContainer = if (playing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Box(modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(250.dp)
                .graphicsLayer {
                    rotationZ = rotation
                    val s = 0.82f + 0.06f * breathe + 0.14f * level.value
                    scaleX = s
                    scaleY = s
                }
                .clip(outerShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        )
        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer {
                    rotationZ = -rotation * 0.5f
                    val s = 1f + 0.08f * level.value
                    scaleX = s
                    scaleY = s
                }
                .clip(innerShape)
                .background(container)
                .clickable(remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple(), onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(playing, label = "playIcon") { p ->
                Icon(
                    if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (p) "Pause" else "Lecture",
                    tint = onContainer,
                    modifier = Modifier.size(56.dp).graphicsLayer { rotationZ = rotation * 0.5f },
                )
            }
        }
    }
}

@Composable
private fun RemainingTime(endsAt: Long, playing: Boolean, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playing, endsAt) {
        while (playing) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val text = when {
        !playing -> "Touchez pour lancer"
        endsAt == 0L -> "Sans minuterie"
        else -> {
            val left = ((endsAt - now) / 1000).coerceAtLeast(0)
            "%d:%02d".format(left / 60, left % 60)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (playing) {
            RollingText(text, style = MaterialTheme.typography.headlineMedium)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (playing && endsAt > 0) {
            Text("arrêt en douceur à ${TimeFormat.clock(endsAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SoundCard(type: SoundType, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "soundCard",
    )
    val shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Sunny, selected, spin = true)
    SpringCard(onClick = onClick, containerColor = container, modifier = modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(shape)
                    .background(if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon(type), null,
                    tint = if (selected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(type.label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
