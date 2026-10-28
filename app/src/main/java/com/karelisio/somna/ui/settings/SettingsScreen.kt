package com.karelisio.somna.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.somna.BuildConfig
import com.karelisio.somna.alarm.AlarmService
import com.karelisio.somna.alarm.AlarmSpec
import com.karelisio.somna.audio.SoundType
import com.karelisio.somna.core.SleepCalculator
import com.karelisio.somna.data.Mission
import com.karelisio.somna.data.SYSTEM_SOUND
import com.karelisio.somna.data.ThemeMode
import com.karelisio.somna.data.soundLabel
import com.karelisio.somna.permissions.AppPermissions
import com.karelisio.somna.ui.LocalSomna
import com.karelisio.somna.ui.components.ConnectedChoice
import com.karelisio.somna.ui.components.SectionTitle
import com.karelisio.somna.ui.components.SettingsGroup
import com.karelisio.somna.ui.components.TopInsets
import com.karelisio.somna.ui.components.SettingsNav
import com.karelisio.somna.ui.components.SettingsRow
import com.karelisio.somna.ui.components.SettingsSlider
import com.karelisio.somna.ui.components.SettingsSwitch
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.update.CheckResult
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPermissions: () -> Unit, onOpenBattery: () -> Unit) {
    val somna = LocalSomna.current
    val context = LocalContext.current
    val settings by somna.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scrollBehavior,
                windowInsets = TopInsets,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Sleep
            SectionTitle("Sommeil")
            var cycle by remember(s.cycleMinutes) { mutableFloatStateOf(s.cycleMinutes.toFloat()) }
            var fallAsleep by remember(s.fallAsleepMinutes) { mutableFloatStateOf(s.fallAsleepMinutes.toFloat()) }
            SettingsGroup(
                items = listOf(
                    {
                        SettingsSlider(
                            title = "Durée d'un cycle",
                            subtitle = "90 min en moyenne, entre 80 et 110 min selon les personnes",
                            icon = Icons.Rounded.Loop,
                            value = cycle,
                            onValueChange = { cycle = it },
                            onValueChangeFinished = { somna.edit { setCycleMinutes(cycle.roundToInt()) } },
                            valueRange = SleepCalculator.MIN_CYCLE_MINUTES.toFloat()..SleepCalculator.MAX_CYCLE_MINUTES.toFloat(),
                            steps = 0,
                            label = { "${it.roundToInt()} min" },
                        )
                    },
                    {
                        SettingsSlider(
                            title = "Temps d'endormissement",
                            subtitle = "Ajouté avant le premier cycle",
                            icon = Icons.Rounded.Bedtime,
                            value = fallAsleep,
                            onValueChange = { fallAsleep = it },
                            onValueChangeFinished = { somna.edit { setFallAsleepMinutes(fallAsleep.roundToInt()) } },
                            valueRange = 0f..SleepCalculator.MAX_FALL_ASLEEP_MINUTES.toFloat(),
                            steps = 0,
                            label = { "${it.roundToInt()} min" },
                        )
                    },
                    {
                        SettingsRow(
                            title = "Nuit visée",
                            subtitle = "Pour le rappel du coucher et l'objectif du journal",
                            icon = Icons.Rounded.Hotel,
                            below = {
                                ConnectedChoice(
                                    options = listOf(4, 5, 6),
                                    selected = s.preferredCycles,
                                    onSelect = { somna.edit { setPreferredCycles(it) } },
                                    label = { "$it cycles" },
                                )
                            },
                        )
                    },
                ),
            )

            // Alarm
            SectionTitle("Réveil")
            SettingsGroup(
                items = listOf(
                    { SoundChoice(s.defaultSound) { v -> somna.edit { setDefaultSound(v) } } },
                    {
                        SettingsSwitch(
                            title = "Lever de soleil",
                            subtitle = "L'écran passe de 0 à 100 % de luminosité avant l'heure",
                            icon = Icons.Rounded.WbTwilight,
                            checked = s.sunriseEnabled,
                            onCheckedChange = { v -> somna.edit { setSunriseEnabled(v) } },
                        )
                    },
                    {
                        var v by remember(s.sunriseMinutes) { mutableFloatStateOf(s.sunriseMinutes.toFloat()) }
                        SettingsSlider(
                            title = "Durée du lever de soleil",
                            icon = Icons.Rounded.AccessTime,
                            value = v,
                            onValueChange = { v = it },
                            onValueChangeFinished = { somna.edit { setSunriseMinutes(v.roundToInt()) } },
                            valueRange = 5f..30f,
                            steps = 4,
                            label = { "${it.roundToInt()} min" },
                        )
                    },
                    {
                        var v by remember(s.crescendoSeconds) { mutableFloatStateOf(s.crescendoSeconds.toFloat()) }
                        SettingsSlider(
                            title = "Crescendo du son",
                            subtitle = "Temps pour passer de très doux au volume maximal",
                            icon = Icons.Rounded.GraphicEq,
                            value = v,
                            onValueChange = { v = it },
                            onValueChangeFinished = { somna.edit { setCrescendoSeconds(v.roundToInt()) } },
                            valueRange = 30f..300f,
                            steps = 8,
                            label = { if (it >= 60) "${(it / 60).roundToInt()} min" + if (it.roundToInt() % 60 != 0) " ${it.roundToInt() % 60} s" else "" else "${it.roundToInt()} s" },
                        )
                    },
                    {
                        var v by remember(s.alarmVolume) { mutableFloatStateOf(s.alarmVolume) }
                        SettingsSlider(
                            title = "Volume du réveil",
                            subtitle = "Même en mode silencieux (flux Alarme)",
                            icon = Icons.Rounded.VolumeUp,
                            value = v,
                            onValueChange = { v = it },
                            onValueChangeFinished = { somna.edit { setAlarmVolume(v) } },
                            valueRange = 0.1f..1f,
                            steps = 0,
                            label = { "${(it * 100).roundToInt()} %" },
                        )
                    },
                    {
                        SettingsSwitch(
                            title = "Vibration progressive",
                            icon = Icons.Rounded.Vibration,
                            checked = s.vibration,
                            onCheckedChange = { v -> somna.edit { setVibration(v) } },
                        )
                    },
                    {
                        var v by remember(s.snoozeMinutes) { mutableFloatStateOf(s.snoozeMinutes.toFloat()) }
                        SettingsSlider(
                            title = "Snooze",
                            icon = Icons.Rounded.Snooze,
                            value = v,
                            onValueChange = { v = it },
                            onValueChangeFinished = { somna.edit { setSnoozeMinutes(v.roundToInt()) } },
                            valueRange = 1f..30f,
                            steps = 0,
                            label = { "${it.roundToInt()} min" },
                        )
                    },
                    {
                        SettingsRow(
                            title = "Mission anti-snooze",
                            subtitle = "Par défaut pour les nouveaux réveils",
                            icon = Icons.Rounded.Extension,
                            below = {
                                ConnectedChoice(
                                    options = Mission.entries,
                                    selected = s.mission,
                                    onSelect = { somna.edit { setMission(it) } },
                                    label = { it.label },
                                    icon = {
                                        when (it) {
                                            Mission.NONE -> Icons.Rounded.Block
                                            Mission.MATH -> Icons.Rounded.Calculate
                                            Mission.PUZZLE -> Icons.Rounded.Extension
                                        }
                                    },
                                )
                            },
                        )
                    },
                    {
                        SettingsRow(
                            title = "Réveil intelligent",
                            subtitle = "Fenêtre par défaut des nouveaux réveils (accéléromètre, téléphone sur le matelas)",
                            icon = Icons.Rounded.Sensors,
                            below = {
                                ConnectedChoice(
                                    options = listOf(0, 20, 25, 30),
                                    selected = s.smartWakeMinutes,
                                    onSelect = { somna.edit { setSmartWakeMinutes(it) } },
                                    label = { if (it == 0) "Off" else "$it min" },
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Sensibilité aux mouvements", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(6.dp))
                                ConnectedChoice(
                                    options = listOf(0, 1, 2),
                                    selected = s.smartSensitivity,
                                    onSelect = { somna.edit { setSmartSensitivity(it) } },
                                    label = { listOf("Faible", "Normale", "Élevée")[it] },
                                )
                            },
                        )
                    },
                    {
                        SettingsNav(
                            title = "Tester le réveil",
                            subtitle = "Sonne tout de suite avec les réglages par défaut (crescendo raccourci)",
                            icon = Icons.Rounded.NotificationsActive,
                            onClick = {
                                val spec = AlarmSpec(
                                    alarmId = 0, triggerAt = System.currentTimeMillis(), hour = 0, minute = 0,
                                    label = "Test", sound = s.defaultSound, vibrate = s.vibration,
                                    mission = s.mission.name, snoozeMinutes = s.snoozeMinutes,
                                    crescendoSeconds = 20, volume = s.alarmVolume, test = true,
                                )
                                AlarmService.start(context, AlarmService.ACTION_RING, spec)
                            },
                        )
                    },
                ),
            )

            // Routine
            SectionTitle("Routine")
            SettingsGroup(
                items = listOf(
                    {
                        SettingsSwitch(
                            title = "Rappel « va te coucher »",
                            subtitle = "À l'heure idéale calculée d'après le réveil du lendemain",
                            icon = Icons.Rounded.Bedtime,
                            checked = s.bedtimeReminder,
                            onCheckedChange = { v -> somna.edit { setBedtimeReminder(v) } },
                        )
                    },
                ),
            )

            // Appearance
            SectionTitle("Apparence")
            SettingsGroup(
                items = listOf(
                    {
                        SettingsRow(
                            title = "Thème",
                            icon = Icons.Rounded.Palette,
                            below = {
                                ConnectedChoice(
                                    options = ThemeMode.entries,
                                    selected = s.themeMode,
                                    onSelect = { somna.edit { setThemeMode(it) } },
                                    label = { if (it == ThemeMode.AUTO_NIGHT) "Auto" else it.label },
                                )
                                Text(
                                    if (s.themeMode == ThemeMode.AUTO_NIGHT) "Sombre de 20 h à 7 h, ou quand le système l'est" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            },
                        )
                    },
                    {
                        SettingsSwitch(
                            title = "Couleurs dynamiques",
                            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Material You : couleurs tirées du fond d'écran" else "Disponible à partir d'Android 12",
                            icon = Icons.Rounded.Palette,
                            checked = s.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onCheckedChange = { v -> somna.edit { setDynamicColor(v) } },
                        )
                    },
                    {
                        SettingsSwitch(
                            title = "Retours haptiques",
                            icon = Icons.Rounded.TouchApp,
                            checked = s.haptics,
                            onCheckedChange = { v -> somna.edit { setHaptics(v) } },
                        )
                    },
                ),
            )

            // Reliability
            SectionTitle("Fiabilité")
            val missing = remember { AppPermissions.missing(context).size }
            SettingsGroup(
                items = listOf(
                    {
                        SettingsNav(
                            title = "Autorisations",
                            subtitle = if (missing == 0) "Tout est accordé" else "$missing à vérifier",
                            icon = Icons.Rounded.Shield,
                            onClick = onOpenPermissions,
                        )
                    },
                    {
                        SettingsNav(
                            title = "Optimisation de la batterie",
                            subtitle = "Guide pour Honor / MagicOS et autres surcouches",
                            icon = Icons.Rounded.BatteryChargingFull,
                            onClick = onOpenBattery,
                        )
                    },
                ),
            )

            // Updates
            SectionTitle("Mises à jour")
            var checking by remember { mutableStateOf(false) }
            var checkMessage by remember { mutableStateOf<String?>(null) }
            val pendingUpdate by somna.pendingUpdate.collectAsStateWithLifecycle()
            val openUpdate = {
                haptics.confirm()
                somna.updateSheetDismissed = false
                somna.forceShowUpdate = true
            }
            SettingsGroup(
                items = listOf(
                    {
                        SettingsRow(
                            title = "Rechercher une mise à jour",
                            subtitle = checkMessage
                                ?: pendingUpdate?.let { "Version ${it.version} disponible (installée : ${BuildConfig.VERSION_NAME})" }
                                ?: "Version installée : ${BuildConfig.VERSION_NAME}",
                            icon = Icons.Rounded.SystemUpdate,
                            onClick = {
                                if (checking) return@SettingsRow
                                checking = true
                                checkMessage = null
                                scope.launch {
                                    val result = somna.checkUpdateNow()
                                    checking = false
                                    checkMessage = when (result) {
                                        is CheckResult.Available -> {
                                            // A manual check always offers it, even if this version was skipped.
                                            somna.edit { setSkippedVersion("") }
                                            openUpdate()
                                            "Version ${result.info.version} disponible"
                                        }
                                        CheckResult.UpToDate, CheckResult.Throttled -> "Somna est à jour ✓"
                                        is CheckResult.Error -> "Impossible de vérifier : ${result.message}"
                                    }
                                }
                            },
                            trailing = {
                                AnimatedContent(checking to (pendingUpdate != null), label = "checking") { (busy, available) ->
                                    when {
                                        busy -> LoadingIndicator(Modifier.size(36.dp))
                                        available -> Button(onClick = openUpdate, shapes = ButtonDefaults.shapes()) { Text("Mettre à jour") }
                                        else -> Spacer(Modifier.size(36.dp))
                                    }
                                }
                            },
                        )
                    },
                    {
                        SettingsSwitch(
                            title = "Vérifier automatiquement",
                            subtitle = "Au lancement et toutes les 6 h au maximum",
                            icon = Icons.Rounded.Update,
                            checked = s.autoUpdateCheck,
                            onCheckedChange = { v -> somna.edit { setAutoUpdateCheck(v) } },
                        )
                    },
                    {
                        SettingsRow(
                            title = "À propos",
                            subtitle = "Somna ${BuildConfig.VERSION_NAME} · 100 % local, aucune donnée ne quitte ton téléphone",
                            icon = Icons.Rounded.Info,
                        )
                    },
                ),
            )
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SoundChoice(current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = SoundType.alarmSounds.map { it.name } + SYSTEM_SOUND
    SettingsRow(
        title = "Son par défaut",
        subtitle = soundLabel(current),
        icon = Icons.Rounded.MusicNote,
        onClick = { open = true },
        trailing = {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
                options.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(soundLabel(id)) },
                        onClick = {
                            onSelect(id)
                            open = false
                        },
                    )
                }
            }
        },
    )
}

