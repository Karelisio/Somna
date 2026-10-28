package com.karelisio.somna.ui.journal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karelisio.somna.container
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.core.toLocalDateTime
import com.karelisio.somna.data.SleepEntry
import com.karelisio.somna.data.SleepStats
import com.karelisio.somna.ui.components.AnimatedCounter
import com.karelisio.somna.ui.components.ConnectedChoice
import com.karelisio.somna.ui.components.RatingLabels
import com.karelisio.somna.ui.components.ScreenHeader
import com.karelisio.somna.ui.components.ShapeBadge
import com.karelisio.somna.ui.components.SpringCard
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.ui.components.rememberStaggerState
import com.karelisio.somna.ui.components.ratingEmoji
import com.karelisio.somna.ui.components.staggerEnter
import com.karelisio.somna.ui.home.LocalSnackbar
import kotlinx.coroutines.launch

@Composable
fun JournalScreen(contentPadding: PaddingValues, onOpenSettings: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { JournalViewModel(container) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SleepEntry?>(null) }
    val stagger = rememberStaggerState()
    val list = entries ?: return
    val target = settings.preferredCycles * settings.cycleMinutes

    LazyColumn(
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Journal",
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            haptics.click()
                            val now = System.currentTimeMillis()
                            editing = SleepEntry(bedtime = now - 8 * 3_600_000L, wakeTime = now)
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(Icons.Rounded.Add, "Ajouter une nuit") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Réglages") }
                },
            )
        }
        item(key = "period") {
            ConnectedChoice(
                options = listOf(7, 30),
                selected = vm.period,
                onSelect = { vm.period = it },
                label = { "$it jours" },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "stats") {
            StatsGrid(stats, Modifier.padding(horizontal = 16.dp))
        }
        item(key = "chart") {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Durée de sommeil", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Objectif : ${TimeFormat.durationMinutes(target.toLong())} (${settings.preferredCycles} cycles)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SleepChart(stats?.perDay.orEmpty(), vm.period, target)
                }
            }
        }
        item(key = "nights") {
            Text(
                "Nuits",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            )
        }
        if (list.isEmpty()) {
            item(key = "empty") { EmptyJournal() }
        }
        items(list, key = { it.id }) { entry ->
            EntryCard(
                entry = entry,
                targetMinutes = target,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .staggerEnter(stagger, entry.id, list.indexOf(entry))
                    .animateItem(),
                onClick = { editing = entry },
            )
        }
    }

    editing?.let { entry ->
        EntryEditorSheet(
            entry = entry,
            onDismiss = { editing = null },
            onSave = {
                haptics.confirm()
                vm.save(it)
                editing = null
            },
            onDelete = if (entry.id != 0L) {
                { e ->
                    vm.delete(e)
                    editing = null
                    scope.launch {
                        val result = snackbar.showSnackbar("Nuit supprimée", actionLabel = "Annuler")
                        if (result == SnackbarResult.ActionPerformed) vm.restore(e)
                    }
                }
            } else null,
        )
    }
}

@Composable
private fun StatsGrid(stats: SleepStats?, modifier: Modifier = Modifier) {
    val s = stats ?: SleepStats(0, 0, 0, 0f, null, null, emptyList())
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Durée moyenne", Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) {
                AnimatedCounter(
                    s.averageMinutes.toInt(),
                    style = MaterialTheme.typography.displaySmall,
                    format = { if (s.nights == 0) "—" else TimeFormat.durationMinutes(it.toLong()) },
                )
            }
            StatCard("Régularité", Modifier.weight(1f), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                val progress by animateFloatAsState(
                    s.regularity / 100f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "regularity",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(56.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    AnimatedCounter(
                        s.regularity,
                        style = MaterialTheme.typography.headlineMedium,
                        format = { if (s.nights == 0) "—" else "$it %" },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Réveil moyen", Modifier.weight(1f), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) {
                val rating = kotlin.math.round(s.averageRating).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (rating == 0) "·" else ratingEmoji(rating), fontSize = 34.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(RatingLabels.getOrElse(rating - 1) { "Pas encore noté" }, style = MaterialTheme.typography.titleSmall)
                }
            }
            StatCard("Horaires moyens", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface) {
                Column {
                    TimeLine(Icons.Rounded.Bedtime, s.averageBedtimeMinutes)
                    TimeLine(Icons.Rounded.WbSunny, s.averageWakeMinutes)
                }
            }
        }
        Text(
            "${s.nights} nuit${if (s.nights > 1) "s" else ""} enregistrée${if (s.nights > 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun TimeLine(icon: androidx.compose.ui.graphics.vector.ImageVector, minutes: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        val text = minutes?.let { val m = ((it % 1440) + 1440) % 1440; "%02d:%02d".format(m / 60, m % 60) } ?: "—"
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun StatCard(
    title: String,
    modifier: Modifier,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    body: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = container, contentColor = content, modifier = modifier.height(128.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            body()
        }
    }
}

@Composable
private fun EntryCard(entry: SleepEntry, targetMinutes: Int, modifier: Modifier, onClick: () -> Unit) {
    val bed = entry.bedtime.toLocalDateTime()
    val wake = entry.wakeTime.toLocalDateTime()
    SpringCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Text(if (entry.rating == 0) "🌙" else ratingEmoji(entry.rating), fontSize = 28.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(TimeFormat.relativeDay(wake.toLocalDate()), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${TimeFormat.clock(bed)} → ${TimeFormat.clock(wake)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val good = entry.durationMinutes >= targetMinutes - 15
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (good) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    TimeFormat.durationMinutes(entry.durationMinutes),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyJournal() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ShapeBadge(MaterialShapes.Puffy, Icons.Rounded.Bedtime, size = 104.dp)
        Spacer(Modifier.height(16.dp))
        Text("Ton journal est vide", style = MaterialTheme.typography.titleLarge)
        Text(
            "Chaque réveil arrêté ajoute une nuit, avec ta note du matin. Tu peux aussi ajouter une nuit à la main.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
