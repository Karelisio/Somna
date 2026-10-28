package com.karelisio.somna.ui.alarms

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karelisio.somna.container
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.data.AlarmEntity
import com.karelisio.somna.data.Mission
import com.karelisio.somna.ui.components.RollingText
import com.karelisio.somna.ui.components.ScreenHeader
import com.karelisio.somna.ui.components.ShapeBadge
import com.karelisio.somna.ui.components.ShapeContainer
import com.karelisio.somna.ui.components.SomnaTimePickerDialog
import com.karelisio.somna.ui.components.SpringCard
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.ui.components.rememberStaggerState
import com.karelisio.somna.ui.components.staggerEnter
import com.karelisio.somna.ui.home.LocalSnackbar
import com.karelisio.somna.ui.navigation.sharedContainer
import com.karelisio.somna.ui.navigation.sharedItem
import com.karelisio.somna.ui.theme.ClockStyle
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

@Composable
fun AlarmsScreen(
    contentPadding: PaddingValues,
    onOpenAlarm: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onGoToCalculator: () -> Unit,
) {
    val container = LocalContext.current.container
    val vm = viewModel { AlarmsViewModel(container) }
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var showPicker by remember { mutableStateOf(false) }
    val stagger = rememberStaggerState()

    val list = alarms ?: return
    val next = list.filter { it.enabled }.map { it to nextTrigger(it, now) }.minByOrNull { it.second }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = "Réveils",
                    actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Réglages") } },
                )
            }
            item(key = "next") { NextAlarmHero(next?.first, next?.second, now, Modifier.padding(horizontal = 16.dp)) }
            if (list.isEmpty()) {
                item(key = "empty") { EmptyAlarms(onGoToCalculator) }
            }
            items(list, key = { it.id }) { alarm ->
                val dismissState = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismissState) {
                    snapshotFlow { dismissState.targetValue }.collect {
                        if (it == SwipeToDismissBoxValue.EndToStart) haptics.threshold()
                    }
                }
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .staggerEnter(stagger, alarm.id, list.indexOf(alarm))
                        .animateItem(),
                    onDismiss = {
                        haptics.confirm()
                        vm.delete(alarm)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar("Réveil de ${"%02d:%02d".format(alarm.hour, alarm.minute)} supprimé", actionLabel = "Annuler")
                            if (result == SnackbarResult.ActionPerformed) vm.restore(alarm)
                        }
                    },
                    backgroundContent = {
                        val progress = dismissState.progress
                        val scale by animateFloatAsState(
                            if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1.2f else 0.8f,
                            MaterialTheme.motionScheme.fastSpatialSpec(), label = "deleteScale",
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = progress.coerceIn(0.3f, 1f)))
                                .padding(horizontal = 28.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                Icons.Rounded.Delete, "Supprimer",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                            )
                        }
                    },
                ) {
                    AlarmCard(
                        alarm = alarm,
                        now = now,
                        onClick = { onOpenAlarm(alarm.id) },
                        onToggle = { vm.setEnabled(alarm, it) },
                    )
                }
            }
        }
        MediumExtendedFloatingActionButton(
            text = { Text("Nouveau réveil") },
            icon = { Icon(Icons.Rounded.Add, null) },
            onClick = {
                haptics.click()
                showPicker = true
            },
            expanded = fabExpanded,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        )
    }

    if (showPicker) {
        val suggestion = next?.first
        SomnaTimePickerDialog(
            title = "Nouveau réveil",
            initialHour = suggestion?.hour ?: 7,
            initialMinute = suggestion?.minute ?: 0,
            confirmLabel = "Créer",
            onDismiss = { showPicker = false },
            onConfirm = { h, m ->
                showPicker = false
                scope.launch {
                    val alarm = vm.create(h, m)
                    onOpenAlarm(alarm.id)
                }
            },
        )
    }
}

@Composable
private fun NextAlarmHero(alarm: AlarmEntity?, at: ZonedDateTime?, now: ZonedDateTime, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            ShapeContainer(
                MaterialShapes.Flower,
                size = 160.dp,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                rotationPeriodMs = 36_000,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-36).dp),
            )
            AnimatedContent(alarm != null && at != null, label = "nextHero") { has ->
                Column(Modifier.padding(26.dp)) {
                    if (has && at != null) {
                        Text("Prochain réveil", style = MaterialTheme.typography.titleMedium)
                        val minutes = Duration.between(now, at).toMinutes() + 1
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("dans ", style = MaterialTheme.typography.headlineSmall)
                            RollingText(TimeFormat.durationMinutes(minutes), style = MaterialTheme.typography.displaySmall)
                        }
                        Text(
                            "${TimeFormat.relativeDay(at.toLocalDate())} à ${TimeFormat.clock(at.toLocalTime())}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        Text("Aucun réveil actif", style = MaterialTheme.typography.headlineSmall)
                        Text("Profite de ta grasse matinée ☁️", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(alarm: AlarmEntity, now: ZonedDateTime, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val container by animateColorAsState(
        if (alarm.enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "alarmContainer",
    )
    val contentAlpha by animateFloatAsState(if (alarm.enabled) 1f else 0.55f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "alarmAlpha")
    val shape = MaterialTheme.shapes.large
    SpringCard(
        onClick = onClick,
        shape = shape,
        containerColor = container,
        modifier = Modifier.fillMaxWidth().sharedContainer("alarm-${alarm.id}", shape),
    ) {
        Row(Modifier.padding(start = 22.dp, end = 18.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(contentAlpha)) {
                Text(
                    "%02d:%02d".format(alarm.hour, alarm.minute),
                    style = ClockStyle.copy(fontSize = 52.sp, lineHeight = 56.sp),
                    modifier = Modifier.sharedItem("alarm-time-${alarm.id}"),
                )
                val whenText = if (alarm.repeating) daysSummary(alarm.days)
                else TimeFormat.relativeDay(nextTrigger(alarm, now).toLocalDate())
                Text(
                    listOf(alarm.label.takeIf { it.isNotBlank() }, whenText).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (alarm.sunrise) FeatureIcon(Icons.Rounded.WbTwilight, "Lever de soleil")
                    if (alarm.smartWakeMinutes > 0) FeatureIcon(Icons.Rounded.Sensors, "Réveil intelligent")
                    when (alarm.missionType) {
                        Mission.MATH -> FeatureIcon(Icons.Rounded.Calculate, "Mission calcul")
                        Mission.PUZZLE -> FeatureIcon(Icons.Rounded.Extension, "Mission puzzle")
                        Mission.NONE -> Unit
                    }
                }
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = {
                    haptics.toggle(it)
                    onToggle(it)
                },
                thumbContent = if (alarm.enabled) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

@Composable
private fun FeatureIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Icon(
            icon, description,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(6.dp).size(16.dp),
        )
    }
}

@Composable
private fun EmptyAlarms(onGoToCalculator: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeBadge(
            MaterialShapes.Cookie9Sided, Icons.Rounded.WbTwilight, size = 112.dp,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(Modifier.height(20.dp))
        Text("Pas encore de réveil", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Calcule l'heure idéale selon tes cycles de sommeil, puis touche un horaire pour créer ton réveil.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGoToCalculator, shapes = ButtonDefaults.shapes()) {
            Text("Calculer mes cycles")
        }
        Spacer(Modifier.width(1.dp))
    }
}
