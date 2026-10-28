package com.karelisio.somna.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.somna.MainActivity
import com.karelisio.somna.ui.LocalSomna
import com.karelisio.somna.ui.alarms.AlarmsScreen
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.ui.journal.JournalScreen
import com.karelisio.somna.ui.sounds.SoundsScreen
import kotlinx.coroutines.flow.MutableStateFlow

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    CALCULATOR("Calculer", Icons.Outlined.Bedtime, Icons.Rounded.Bedtime),
    ALARMS("Réveils", Icons.Outlined.Alarm, Icons.Rounded.Alarm),
    SOUNDS("Sons", Icons.Outlined.GraphicEq, Icons.Rounded.GraphicEq),
    JOURNAL("Journal", Icons.Outlined.Insights, Icons.Rounded.Insights),
}

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun MainScreen(
    destination: MutableStateFlow<String?>,
    onOpenAlarm: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val somna = LocalSomna.current
    val haptics = rememberHaptics()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sleepNowRequest by remember { mutableIntStateOf(0) }
    var napRequest by remember { mutableIntStateOf(0) }
    var newAlarmRequest by remember { mutableIntStateOf(0) }
    val pendingDestination by destination.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDestination) {
        when (pendingDestination) {
            MainActivity.DEST_ALARMS -> tab = Tab.ALARMS.ordinal
            MainActivity.DEST_SOUNDS -> tab = Tab.SOUNDS.ordinal
            MainActivity.DEST_JOURNAL -> tab = Tab.JOURNAL.ordinal
            MainActivity.DEST_SLEEP_NOW -> {
                tab = Tab.CALCULATOR.ordinal
                sleepNowRequest++
            }
            MainActivity.DEST_NAP -> {
                tab = Tab.CALCULATOR.ordinal
                napRequest++
            }
            MainActivity.DEST_NEW_ALARM -> {
                tab = Tab.ALARMS.ordinal
                newAlarmRequest++
            }
            MainActivity.DEST_UPDATE -> {
                somna.updateSheetDismissed = false
                somna.forceShowUpdate = true
            }
        }
        destination.value = null
    }
    val snackbar = remember { SnackbarHostState() }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { item ->
                    val selected = tab == item.ordinal
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.tick()
                            tab = item.ordinal
                        },
                        icon = { BouncyIcon(if (selected) item.selectedIcon else item.icon, selected) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // M3 fade-through between top-level destinations.
                    (fadeIn(effects) + scaleIn(spatial, initialScale = 0.96f)).togetherWith(fadeOut(fastEffects))
                },
                label = "tabs",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                val contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())
                Box(Modifier.fillMaxSize()) {
                    when (Tab.entries[current]) {
                        Tab.CALCULATOR -> CalculatorScreen(
                            contentPadding = contentPadding,
                            sleepNowRequest = sleepNowRequest,
                            napRequest = napRequest,
                            onOpenSettings = onOpenSettings,
                            onOpenPermissions = onOpenPermissions,
                            onShowAlarms = { tab = Tab.ALARMS.ordinal },
                        )
                        Tab.ALARMS -> AlarmsScreen(
                            contentPadding, onOpenAlarm, onOpenSettings,
                            onGoToCalculator = { tab = Tab.CALCULATOR.ordinal },
                            newAlarmRequest = newAlarmRequest,
                        )
                        Tab.SOUNDS -> SoundsScreen(contentPadding, onOpenSettings)
                        Tab.JOURNAL -> JournalScreen(contentPadding, onOpenSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun BouncyIcon(icon: ImageVector, selected: Boolean) {
    val scale = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(0.8f)
            scale.animateTo(1f, spec)
        }
    }
    Crossfade(icon, label = "navIcon") {
        Icon(it, contentDescription = null, modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        })
    }
}
