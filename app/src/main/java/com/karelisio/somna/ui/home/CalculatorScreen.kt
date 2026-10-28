package com.karelisio.somna.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karelisio.somna.container
import com.karelisio.somna.core.SleepOption
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.permissions.AppPermission
import com.karelisio.somna.permissions.AppPermissions
import com.karelisio.somna.ui.LocalSomna
import com.karelisio.somna.ui.components.ConnectedChoice
import com.karelisio.somna.ui.components.CycleWave
import com.karelisio.somna.ui.components.RollingText
import com.karelisio.somna.ui.components.ScreenHeader
import com.karelisio.somna.ui.components.ShapeContainer
import com.karelisio.somna.ui.components.SomnaTimePickerDialog
import com.karelisio.somna.ui.components.SpringCard
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.ui.components.rememberStaggerState
import com.karelisio.somna.ui.components.staggerEnter
import com.karelisio.somna.ui.theme.ClockStyle
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.launch

@Composable
fun CalculatorScreen(
    contentPadding: PaddingValues,
    sleepNowRequest: Int,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onShowAlarms: () -> Unit,
    napRequest: Int = 0,
) {
    val container = LocalContext.current.container
    val vm = viewModel { CalculatorViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(sleepNowRequest) {
        if (sleepNowRequest > 0) vm.mode = CalcMode.SLEEP_NOW
    }

    fun nap(minutes: Int) {
        haptics.confirm()
        scope.launch {
            val created = vm.createNap(minutes)
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(created.message, actionLabel = "Annuler")
            if (result == SnackbarResult.ActionPerformed) vm.undo(created)
        }
    }
    LaunchedEffect(napRequest) {
        if (napRequest > 0) nap(20)
    }

    val options = remember(vm.mode, now, vm.wakeTarget, settings.cycleMinutes, settings.fallAsleepMinutes) {
        vm.options(now, settings)
    }
    val stagger = rememberStaggerState(vm.mode, vm.wakeTarget)
    val heroEnter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val heroScale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val heroExit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val focused = options.firstOrNull { it.cycles == vm.focusedCycles } ?: options.last()

    LazyColumn(
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Somna",
                overline = greeting(now),
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Réglages") }
                },
            )
        }
        item(key = "permissions") { PermissionReminder(onOpenPermissions) }
        item(key = "mode") {
            ConnectedChoice(
                options = CalcMode.entries,
                selected = vm.mode,
                onSelect = { vm.mode = it },
                label = { if (it == CalcMode.WAKE_AT) "Je me lève à…" else "Je me couche" },
                icon = { if (it == CalcMode.WAKE_AT) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "hero") {
            AnimatedContent(
                targetState = vm.mode,
                transitionSpec = {
                    (fadeIn(heroEnter) + androidx.compose.animation.scaleIn(heroScale, 0.92f)).togetherWith(fadeOut(heroExit))
                },
                label = "hero",
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { mode ->
                when (mode) {
                    CalcMode.SLEEP_NOW -> NowHero(now, settings.fallAsleepMinutes)
                    CalcMode.WAKE_AT -> WakeHero(
                        target = vm.wakeDateTime(now),
                        onEdit = { showPicker = true },
                        onShift = {
                            haptics.tick()
                            vm.shiftWakeTarget(it)
                        },
                    )
                }
            }
        }
        item(key = "title-${vm.mode}") {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
                Text(
                    if (vm.mode == CalcMode.WAKE_AT) "Couche-toi à…" else "Réveille-toi à…",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "Touchez un horaire pour créer le réveil",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(options, key = { "${vm.mode}-${it.cycles}" }) { option ->
            val past = vm.mode == CalcMode.WAKE_AT && !option.time.isAfter(now)
            SleepOptionCard(
                option = option,
                past = past,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .staggerEnter(stagger, "${vm.mode}-${option.cycles}", option.cycles)
                    .animateItem(),
                onClick = {
                    haptics.confirm()
                    scope.launch {
                        val created = vm.create(option)
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(created.message, actionLabel = "Annuler", withDismissAction = false)
                        if (result == SnackbarResult.ActionPerformed) vm.undo(created)
                    }
                },
            )
        }
        item(key = "night") {
            NightPreview(focused, vm.mode, now, settings.fallAsleepMinutes, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        item(key = "nap") {
            NapCard(now, settings.fallAsleepMinutes, settings.cycleMinutes, onNap = ::nap, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item(key = "footer") {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cycles de ${settings.cycleMinutes} min · endormissement ${settings.fallAsleepMinutes} min", maxLines = 1)
                }
            }
        }
    }

    if (showPicker) {
        SomnaTimePickerDialog(
            title = "Je veux me lever à",
            initialHour = vm.wakeTarget.hour,
            initialMinute = vm.wakeTarget.minute,
            onDismiss = { showPicker = false },
            onConfirm = { h, m ->
                vm.wakeTarget = java.time.LocalTime.of(h, m)
                showPicker = false
            },
        )
    }
}

private fun greeting(now: LocalDateTime): String = when (now.hour) {
    in 5..11 -> "Bonjour"
    in 12..17 -> "Bon après-midi"
    in 18..21 -> "Bonsoir"
    else -> "Bonne nuit"
}

@Composable
private fun NowHero(now: LocalDateTime, fallAsleep: Int) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            ShapeContainer(
                MaterialShapes.Cookie12Sided,
                size = 180.dp,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                rotationPeriodMs = 40_000,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 50.dp, y = (-40).dp),
            )
            Column(Modifier.padding(28.dp)) {
                Text("Il est", style = MaterialTheme.typography.titleMedium)
                RollingText(TimeFormat.clock(now), style = ClockStyle.copy(fontSize = 72.sp, lineHeight = 76.sp))
                Text(
                    "Endormissement estimé vers ${TimeFormat.clock(now.plusMinutes(fallAsleep.toLong()))} (+$fallAsleep min)",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun WakeHero(target: LocalDateTime, onEdit: () -> Unit, onShift: (Long) -> Unit) {
    SpringCard(
        onClick = onEdit,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        pressedScale = 0.98f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            ShapeContainer(
                MaterialShapes.Sunny,
                size = 170.dp,
                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                rotationPeriodMs = 30_000,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 45.dp, y = (-35).dp),
            )
            Column(Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Je veux me lever à", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Edit, "Modifier", Modifier.size(18.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RollingText(TimeFormat.clock(target), style = ClockStyle.copy(fontSize = 72.sp, lineHeight = 76.sp))
                    Spacer(Modifier.weight(1f))
                    val colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    FilledIconButton(onClick = { onShift(-15) }, colors = colors, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Remove, "15 minutes plus tôt")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { onShift(15) }, colors = colors, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Add, "15 minutes plus tard")
                    }
                }
                Text(TimeFormat.relativeDay(target.toLocalDate()), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SleepOptionCard(option: SleepOption, past: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        option.ideal -> scheme.primaryContainer to scheme.onPrimaryContainer
        option.recommended -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    val containerColor by animateColorAsState(container, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "optionColor")
    val tag = when {
        option.ideal -> "Idéal"
        option.recommended -> "Recommandé"
        else -> "Nuit courte"
    }
    val polygon = when {
        option.ideal -> MaterialShapes.Sunny
        option.recommended -> MaterialShapes.Cookie9Sided
        else -> MaterialShapes.Circle
    }
    SpringCard(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = content,
        haptic = false,
        modifier = modifier.fillMaxWidth().alpha(if (past) 0.55f else 1f),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = if (option.recommended) 22.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeContainer(
                polygon,
                size = if (option.recommended) 60.dp else 48.dp,
                containerColor = content.copy(alpha = 0.12f),
                rotate = option.recommended,
                rotationPeriodMs = 16_000,
            ) {
                Text("${option.cycles}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    TimeFormat.clock(option.time),
                    style = if (option.recommended) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "${option.cycles} cycles · ${TimeFormat.duration(option.sleepDuration)}" + if (past) " · passé" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(shape = CircleShape, color = content.copy(alpha = 0.12f)) {
                    Text(tag, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Icon(Icons.Rounded.AlarmAdd, "Créer le réveil")
            }
        }
    }
}

@Composable
private fun NightPreview(option: SleepOption, mode: CalcMode, now: LocalDateTime, fallAsleep: Int, modifier: Modifier = Modifier) {
    val start: LocalDateTime
    val end: LocalDateTime
    if (mode == CalcMode.SLEEP_NOW) {
        start = now.plusMinutes(fallAsleep.toLong())
        end = option.time
    } else {
        start = option.time.plusMinutes(fallAsleep.toLong())
        end = option.time.plus(option.sleepDuration).plusMinutes(fallAsleep.toLong())
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("Ta nuit", style = MaterialTheme.typography.titleLarge)
            AnimatedContent(option.cycles, label = "nightTitle") { cycles ->
                Text(
                    "$cycles cycles · ${TimeFormat.duration(Duration.ofMinutes(option.sleepDuration.toMinutes()))} — réveil en fin de cycle, en sommeil léger",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            CycleWave(option.cycles, Modifier.fillMaxWidth().height(96.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(TimeFormat.clock(start), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(TimeFormat.clock(end), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.WbSunny, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/** Discreet reminder when critical permissions are still missing (e.g. skipped during onboarding). */
@Composable
fun PermissionReminder(onOpen: () -> Unit) {
    val context = LocalContext.current
    val somna = LocalSomna.current
    val settings by somna.settings.collectAsStateWithLifecycle()
    var missing by remember { mutableStateOf<List<AppPermission>>(emptyList()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        missing = AppPermissions.all().filter { it.critical && !it.isGranted(context) }
    }
    val dismissedAt = settings?.permissionReminderDismissedAt ?: 0L
    val visible = missing.isNotEmpty() && System.currentTimeMillis() - dismissedAt > 2 * 24 * 3_600_000L
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
        exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Réveil pas encore 100 % fiable", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (missing.size == 1) "1 autorisation à vérifier" else "${missing.size} autorisations à vérifier",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onOpen, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) {
                    Text("Vérifier")
                }
                IconButton(onClick = { somna.edit { setPermissionReminderDismissedAt(System.currentTimeMillis()) } }) {
                    Icon(Icons.Rounded.Close, "Me le rappeler plus tard")
                }
            }
        }
    }
}

@Composable
private fun NapCard(now: LocalDateTime, fallAsleep: Int, cycle: Int, onNap: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Hotel, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Sieste", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Réveil avant le sommeil profond, ou après un cycle complet (endormissement inclus)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20 to "Flash", 30 to "Récup", cycle to "1 cycle").forEach { (minutes, label) ->
                    val wake = now.plusMinutes((fallAsleep + minutes).toLong() + 1)
                    SpringCard(
                        onClick = { onNap(minutes) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.large,
                        haptic = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                            Text("$minutes min", style = MaterialTheme.typography.titleMedium)
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(6.dp))
                            Text("→ ${TimeFormat.clock(wake)}", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}
