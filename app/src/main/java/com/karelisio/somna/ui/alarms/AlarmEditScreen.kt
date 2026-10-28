package com.karelisio.somna.ui.alarms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karelisio.somna.audio.SoundType
import com.karelisio.somna.container
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.data.AlarmEntity
import com.karelisio.somna.data.Mission
import com.karelisio.somna.data.SYSTEM_SOUND
import com.karelisio.somna.data.soundLabel
import com.karelisio.somna.ui.components.ConnectedChoice
import com.karelisio.somna.ui.components.SectionTitle
import com.karelisio.somna.ui.components.SettingsGroup
import com.karelisio.somna.ui.components.SettingsRow
import com.karelisio.somna.ui.components.SettingsSwitch
import com.karelisio.somna.ui.components.SomnaTimePickerDialog
import com.karelisio.somna.ui.components.SpringCard
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.ui.components.rememberMorphShape
import com.karelisio.somna.ui.components.rememberSoundPreview
import com.karelisio.somna.ui.navigation.sharedContainer
import com.karelisio.somna.ui.navigation.sharedItem
import com.karelisio.somna.ui.theme.ClockStyle
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@Composable
fun AlarmEditScreen(alarmId: Int, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val context = LocalContext.current
    val vm = viewModel(key = "alarm-$alarmId") { AlarmEditViewModel(container, alarmId) }
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var showPicker by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.large

    Scaffold(
        modifier = Modifier.sharedContainer("alarm-$alarmId", shape),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Réveil") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                },
                actions = {
                    IconButton(onClick = {
                        haptics.reject()
                        vm.delete(onBack)
                    }) { Icon(Icons.Rounded.Delete, "Supprimer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        val a = alarm ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            TimeHeader(a, onEdit = { showPicker = true })
            DaySelector(a.days, onChange = { days -> vm.update { it.copy(days = days, enabled = true) } })
            var label by remember(a.id) { mutableStateOf(a.label) }
            OutlinedTextField(
                value = label,
                onValueChange = { text ->
                    label = text.take(40)
                    vm.update(debounce = true) { it.copy(label = label) }
                },
                label = { Text("Nom du réveil") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            SectionTitle("Son")
            SoundPicker(a.sound, onSelect = { sound -> vm.update { it.copy(sound = sound) } })

            SectionTitle("Réveil en douceur")
            SettingsGroup(
                items = listOf(
                    {
                        SettingsSwitch(
                            title = "Lever de soleil",
                            subtitle = "L'écran s'illumine progressivement avant l'heure",
                            icon = Icons.Rounded.WbTwilight,
                            checked = a.sunrise,
                            onCheckedChange = { v -> vm.update { it.copy(sunrise = v) } },
                        )
                    },
                    {
                        SettingsSwitch(
                            title = "Vibration progressive",
                            icon = Icons.Rounded.Vibration,
                            checked = a.vibrate,
                            onCheckedChange = { v -> vm.update { it.copy(vibrate = v) } },
                        )
                    },
                ),
            )

            SectionTitle("Réveil intelligent")
            SmartWakeSection(a, onChange = { minutes -> vm.update { it.copy(smartWakeMinutes = minutes) } })

            SectionTitle("Mission anti-snooze")
            Column(Modifier.padding(horizontal = 16.dp)) {
                ConnectedChoice(
                    options = Mission.entries,
                    selected = a.missionType,
                    onSelect = { m -> vm.update { it.copy(mission = m.name) } },
                    label = { it.label },
                    icon = {
                        when (it) {
                            Mission.NONE -> Icons.Rounded.Block
                            Mission.MATH -> Icons.Rounded.Calculate
                            Mission.PUZZLE -> Icons.Rounded.Extension
                        }
                    },
                )
                Text(
                    when (a.missionType) {
                        Mission.NONE -> "Le réveil s'arrête d'un simple geste."
                        Mission.MATH -> "Résous deux petits calculs pour arrêter le réveil."
                        Mission.PUZZLE -> "Touche les nombres de 1 à 9 dans l'ordre pour arrêter le réveil."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            FilledTonalButton(
                onClick = {
                    haptics.click()
                    vm.test(context)
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
            ) {
                Icon(Icons.Rounded.NotificationsActive, null)
                Spacer(Modifier.width(10.dp))
                Text("Tester ce réveil maintenant")
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showPicker) {
        val a = alarm
        if (a != null) {
            SomnaTimePickerDialog(
                title = "Heure du réveil",
                initialHour = a.hour,
                initialMinute = a.minute,
                onDismiss = { showPicker = false },
                onConfirm = { h, m ->
                    showPicker = false
                    vm.update { it.copy(hour = h, minute = m, enabled = true) }
                },
            )
        }
    }
}

@Composable
private fun TimeHeader(alarm: AlarmEntity, onEdit: () -> Unit) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(alarm) { now = ZonedDateTime.now() }
    SpringCard(
        onClick = onEdit,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        pressedScale = 0.98f,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%02d:%02d".format(alarm.hour, alarm.minute),
                style = ClockStyle.copy(fontSize = 96.sp, lineHeight = 100.sp),
                modifier = Modifier.sharedItem("alarm-time-${alarm.id}"),
            )
            val next = nextTrigger(alarm, now)
            val minutes = Duration.between(now, next).toMinutes() + 1
            Text(
                if (alarm.enabled) "Sonne ${TimeFormat.relativeDay(next.toLocalDate()).lowercase()} · dans ${TimeFormat.durationMinutes(minutes)}"
                else "Désactivé",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun DaySelector(days: Int, onChange: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { day ->
                val bit = 1 shl (day.value - 1)
                val selected = days and bit != 0
                val shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie6Sided, selected, spin = true)
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.motionScheme.defaultEffectsSpec(), label = "dayColor",
                )
                val textColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.motionScheme.defaultEffectsSpec(), label = "dayText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .padding(3.dp)
                        .aspectRatio(1f)
                        .clip(shape)
                        .background(color)
                        .clickable(remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple()) {
                            haptics.toggle(!selected)
                            onChange(days xor bit)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(dayLetter(day), style = MaterialTheme.typography.titleMedium, color = textColor)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Une fois", AlarmEntity.WEEKDAYS to "En semaine", AlarmEntity.EVERY_DAY to "Tous les jours").forEach { (value, label) ->
                androidx.compose.material3.FilterChip(
                    selected = days == value,
                    onClick = {
                        haptics.tick()
                        onChange(value)
                    },
                    label = { Text(label) },
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}

private fun soundIcon(id: String): ImageVector = when (id) {
    SoundType.BIRDS.name -> Icons.Rounded.Forest
    SoundType.CHIMES.name -> Icons.Rounded.MusicNote
    SoundType.WAVES.name -> Icons.Rounded.Waves
    else -> Icons.Rounded.NotificationsActive
}

@Composable
private fun SoundPicker(selected: String, onSelect: (String) -> Unit) {
    val preview = rememberSoundPreview()
    val haptics = rememberHaptics()
    val sounds = SoundType.alarmSounds.map { it.name } + SYSTEM_SOUND
    Row(
        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sounds.forEach { id ->
            val isSelected = id == selected
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.motionScheme.defaultEffectsSpec(), label = "soundColor",
            )
            val iconShape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Clover4Leaf, isSelected, spin = true)
            SpringCard(
                onClick = {
                    onSelect(id)
                    preview.play(id)
                },
                containerColor = container,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.width(132.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(iconShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                soundIcon(id), null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            haptics.click()
                            preview.toggle(id)
                        }) {
                            Icon(if (preview.playing == id) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, "Écouter")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(soundLabel(id), style = MaterialTheme.typography.titleSmall, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun SmartWakeSection(alarm: AlarmEntity, onChange: (Int) -> Unit) {
    val items = mutableListOf<@Composable () -> Unit>(
        {
            SettingsSwitch(
                title = "Sonner en sommeil léger",
                subtitle = "Pose le téléphone sur le matelas : Somna détecte tes mouvements et sonne au meilleur moment de la fenêtre.",
                icon = Icons.Rounded.Sensors,
                checked = alarm.smartWakeMinutes > 0,
                onCheckedChange = { onChange(if (it) 30 else 0) },
            )
        },
    )
    if (alarm.smartWakeMinutes > 0) {
        items += {
            var value by remember(alarm.id) { mutableStateOf(alarm.smartWakeMinutes.toFloat()) }
            val start = (alarm.hour * 60 + alarm.minute - value.roundToInt()).mod(1440)
            val haptics = rememberHaptics()
            SettingsRow(
                title = "Fenêtre de réveil",
                subtitle = "Entre %02d:%02d et %02d:%02d".format(start / 60, start % 60, alarm.hour, alarm.minute),
                trailing = {
                    Text("${value.roundToInt()} min", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                },
                below = {
                    androidx.compose.material3.Slider(
                        value = value,
                        onValueChange = {
                            if (it.roundToInt() != value.roundToInt()) haptics.tick()
                            value = it
                        },
                        onValueChangeFinished = { onChange(value.roundToInt()) },
                        valueRange = 20f..30f,
                        steps = 1,
                    )
                },
            )
        }
    }
    SettingsGroup(items = items)
}
