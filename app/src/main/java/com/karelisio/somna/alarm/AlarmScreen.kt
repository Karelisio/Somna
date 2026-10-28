package com.karelisio.somna.alarm

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.UserManagerCompat
import com.karelisio.somna.container
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.data.Mission
import com.karelisio.somna.ui.components.AnimatedCheck
import com.karelisio.somna.ui.components.EmojiRating
import com.karelisio.somna.ui.components.RollingText
import com.karelisio.somna.ui.components.SunriseSky
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.ui.components.rememberMorphShape
import com.karelisio.somna.ui.theme.ClockStyle
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Stage { Calm, Sunrise, Ringing, Mission, Rating, Snoozed, Closing }

@Composable
fun AlarmScreen(
    session: AlarmSession?,
    outcome: AlarmOutcome?,
    onBrightness: (Float?) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    var missionOpen by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val stage = when {
        session?.ringing == true && missionOpen -> Stage.Mission
        session?.ringing == true -> Stage.Ringing
        session?.sunriseStart != null -> Stage.Sunrise
        session != null -> Stage.Calm
        outcome is AlarmOutcome.Dismissed && !outcome.spec.test -> Stage.Rating
        outcome is AlarmOutcome.Snoozed -> Stage.Snoozed
        else -> Stage.Closing
    }

    // Sunrise progress: 0 → 1 over the sunrise window, then full daylight while ringing.
    val sunriseProgress = session?.sunriseStart?.let { start ->
        ((now - start).toFloat() / (session.sunriseEnd - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    } ?: 0f
    val skyTarget = when (stage) {
        Stage.Ringing, Stage.Mission, Stage.Rating, Stage.Snoozed -> 1f
        Stage.Sunrise -> sunriseProgress
        else -> 0f
    }
    val sky by animateFloatAsState(skyTarget, tween(1_200), label = "sky")

    LaunchedEffect(stage, sunriseProgress) {
        onBrightness(
            when (stage) {
                Stage.Sunrise -> 0.01f + 0.99f * sunriseProgress
                Stage.Ringing, Stage.Mission -> 1f
                Stage.Calm -> 0.01f
                else -> null
            },
        )
    }
    LaunchedEffect(stage) {
        when (stage) {
            Stage.Closing -> onFinish()
            Stage.Snoozed -> {
                delay(2_500)
                onFinish()
            }
            else -> Unit
        }
    }
    LaunchedEffect(missionOpen) {
        if (AlarmRuntime.session.value?.ringing == true) {
            AlarmService.command(context, AlarmService.ACTION_MISSION) { putExtra(AlarmService.EXTRA_DUCK, missionOpen) }
        }
    }

    val enterEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val enterSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val exitEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val textColor = lerp(Color.White, Color(0xFF14183A), ((sky - 0.8f) / 0.15f).coerceIn(0f, 1f))

    Box(Modifier.fillMaxSize()) {
        SunriseSky(sky, Modifier.fillMaxSize(), pulse = stage == Stage.Ringing)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            RollingText(TimeFormat.clock(now), style = ClockStyle.copy(fontSize = 96.sp, lineHeight = 100.sp), color = textColor)
            Text(
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.FRENCH)),
                style = MaterialTheme.typography.titleMedium,
                color = textColor.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(12.dp))
            val spec = session?.spec ?: outcome?.spec
            Text(
                when (stage) {
                    Stage.Calm -> "Réveil intelligent actif · au plus tard ${spec?.let { TimeFormat.clock(it.triggerAt) }}"
                    Stage.Sunrise -> "Lever de soleil · réveil à ${spec?.let { TimeFormat.clock(it.triggerAt) }}"
                    Stage.Ringing, Stage.Mission ->
                        if (session?.smartTriggered == true) "Sommeil léger détecté : c'est le bon moment ☀️"
                        else spec?.label?.ifBlank { null } ?: "Bonjour ☀️"
                    Stage.Snoozed -> "Snooze jusqu'à ${(outcome as? AlarmOutcome.Snoozed)?.let { TimeFormat.clock(it.until) }} 💤"
                    else -> "Bonjour ☀️"
                },
                style = MaterialTheme.typography.titleLarge,
                color = textColor,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(enterEffects) + scaleIn(enterSpatial, 0.9f)).togetherWith(fadeOut(exitEffects))
                },
                label = "alarmStage",
            ) { current ->
                when (current) {
                    Stage.Calm, Stage.Sunrise -> Button(
                        onClick = { AlarmService.command(context, AlarmService.ACTION_DISMISS) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.28f), contentColor = Color.White),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Icon(Icons.Rounded.WbSunny, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Je suis réveillé", style = MaterialTheme.typography.titleMedium)
                    }
                    Stage.Ringing -> RingingControls(
                        spec = session?.spec,
                        contentColor = textColor,
                        onSnooze = { AlarmService.command(context, AlarmService.ACTION_SNOOZE) },
                        onDismiss = {
                            val mission = session?.spec?.mission?.let { runCatching { Mission.valueOf(it) }.getOrNull() } ?: Mission.NONE
                            if (mission == Mission.NONE) AlarmService.command(context, AlarmService.ACTION_DISMISS) else missionOpen = true
                        },
                    )
                    Stage.Mission -> {
                        val mission = session?.spec?.mission?.let { runCatching { Mission.valueOf(it) }.getOrNull() } ?: Mission.MATH
                        MissionCard(
                            mission = mission,
                            onSolved = {
                                missionOpen = false
                                AlarmService.command(context, AlarmService.ACTION_DISMISS)
                            },
                            onIdle = { missionOpen = false },
                        )
                    }
                    Stage.Rating -> RatingCard(onDone = onFinish)
                    Stage.Snoozed, Stage.Closing -> Spacer(Modifier.height(1.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RingingControls(spec: AlarmSpec?, contentColor: Color, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FilledTonalButton(
            onClick = {
                haptics.click()
                onSnooze()
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.height(72.dp).fillMaxWidth(0.8f),
        ) {
            Icon(Icons.Rounded.Snooze, null)
            Spacer(Modifier.width(10.dp))
            Text("Snooze · ${spec?.snoozeMinutes ?: 9} min", style = MaterialTheme.typography.titleLarge)
        }
        SlideToStop(contentColor, onDismiss)
    }
}

/** Slide the sun to the right to stop the alarm (no accidental dismiss). */
@Composable
private fun SlideToStop(contentColor: Color, onStop: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val thumbShape = rememberMorphShape(MaterialShapes.Sunny, MaterialShapes.Circle, armed, spin = true)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(CircleShape)
            .background(contentColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val thumb = 72.dp
        val max = with(density) { (maxWidth - thumb - 8.dp).toPx() }
        Text(
            "Glisse pour arrêter",
            style = MaterialTheme.typography.titleMedium,
            color = contentColor.copy(alpha = 0.85f * (1f - (offset.value / max).coerceIn(0f, 1f))),
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            Modifier
                .padding(4.dp)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumb)
                .clip(thumbShape)
                .background(MaterialTheme.colorScheme.tertiary)
                .pointerInput(max) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value > max * 0.85f) {
                                    haptics.confirm()
                                    offset.animateTo(max, spring())
                                    onStop()
                                } else {
                                    armed = false
                                    offset.animateTo(0f, spring(dampingRatio = 0.55f))
                                }
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch {
                            val value = (offset.value + drag).coerceIn(0f, max)
                            offset.snapTo(value)
                            val nowArmed = value > max * 0.85f
                            if (nowArmed != armed) {
                                armed = nowArmed
                                if (nowArmed) haptics.threshold()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (armed) Icons.Rounded.Check else Icons.Rounded.WbSunny, "Arrêter",
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun MissionCard(mission: Mission, onSolved: () -> Unit, onIdle: () -> Unit) {
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastInteraction) {
        delay(30_000)
        onIdle() // back to full volume if the user fell asleep again
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (mission) {
                Mission.PUZZLE -> PuzzleMission(onSolved) { lastInteraction = System.currentTimeMillis() }
                else -> MathMission(onSolved) { lastInteraction = System.currentTimeMillis() }
            }
        }
    }
}

@Composable
private fun MathMission(onSolved: () -> Unit, onInteract: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var solved by remember { mutableIntStateOf(0) }
    val problem = remember(solved) { newProblem() }
    var input by remember(solved) { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    Text("Mission ${solved + 1}/2", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    Text(problem.first, style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .graphicsLayer { translationX = shake.value }
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        RollingText(input.ifEmpty { "?" }, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(12.dp))
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
    keys.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
            row.forEach { key ->
                Button(
                    onClick = {
                        onInteract()
                        when (key) {
                            "⌫" -> {
                                haptics.tick()
                                input = input.dropLast(1)
                            }
                            "✓" -> {
                                if (input.toIntOrNull() == problem.second) {
                                    haptics.confirm()
                                    if (solved == 1) onSolved() else solved++
                                } else {
                                    haptics.reject()
                                    input = ""
                                    scope.launch {
                                        for (x in listOf(24f, -20f, 14f, -8f, 0f)) shake.animateTo(x, tween(45))
                                    }
                                }
                            }
                            else -> {
                                haptics.tick()
                                if (input.length < 4) input += key
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = if (key == "✓") ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.size(width = 84.dp, height = 60.dp),
                ) {
                    if (key == "⌫") Icon(Icons.AutoMirrored.Rounded.Backspace, "Effacer")
                    else Text(key, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

private fun newProblem(): Pair<String, Int> = if (Random.nextBoolean()) {
    val a = Random.nextInt(14, 69)
    val b = Random.nextInt(12, 39)
    "$a + $b" to a + b
} else {
    val a = Random.nextInt(3, 10)
    val b = Random.nextInt(6, 19)
    "$a × $b" to a * b
}

@Composable
private fun PuzzleMission(onSolved: () -> Unit, onInteract: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var round by remember { mutableIntStateOf(0) }
    val numbers = remember(round) { (1..9).shuffled() }
    var next by remember(round) { mutableIntStateOf(1) }
    val shake = remember { Animatable(0f) }
    Text("Touche les nombres de 1 à 9 dans l'ordre", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Column(Modifier.graphicsLayer { translationX = shake.value }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        numbers.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { n ->
                    val done = n < next
                    val shape = rememberMorphShape(MaterialShapes.Square, MaterialShapes.Cookie4Sided, done, spin = true)
                    val scale by animateFloatAsState(if (done) 0.88f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "tile")
                    Box(
                        Modifier
                            .size(80.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(shape)
                            .background(if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primaryContainer)
                            .clickable(remember { MutableInteractionSource() }, indication = null) {
                                    onInteract()
                                    if (n == next) {
                                        haptics.tick()
                                        if (n == 9) {
                                            haptics.confirm()
                                            onSolved()
                                        } else next++
                                    } else if (n > next) {
                                        haptics.reject()
                                        scope.launch {
                                            for (x in listOf(24f, -20f, 14f, -8f, 0f)) shake.animateTo(x, tween(45))
                                            round++
                                        }
                                    }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onTertiary)
                        else Text("$n", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingCard(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rated by remember { mutableIntStateOf(0) }
    var slept by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        delay(60_000) // went back to sleep without answering: don't keep the screen on
        onDone()
    }
    LaunchedEffect(Unit) {
        if (!UserManagerCompat.isUserUnlocked(context)) return@LaunchedEffect
        delay(400)
        val latest = runCatching { context.container.database.sleepDao().latest() }.getOrNull()
        if (latest != null && System.currentTimeMillis() - latest.wakeTime < 5 * 60_000L) {
            slept = TimeFormat.durationMinutes(latest.durationMinutes)
        }
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(rated > 0, label = "rated") { done ->
                if (done) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedCheck(size = 64.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Belle journée ☀️", style = MaterialTheme.typography.headlineSmall)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bonjour !", style = MaterialTheme.typography.headlineMedium)
                        slept?.let {
                            Text("Tu as dormi $it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Comment te sens-tu ce matin ?", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        EmojiRating(rated, { value ->
                            rated = value
                            scope.launch {
                                if (UserManagerCompat.isUserUnlocked(context)) {
                                    runCatching { context.container.sleep.rateLatest(value) }
                                }
                                delay(1_400)
                                onDone()
                            }
                        })
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onDone) { Text("Passer") }
                    }
                }
            }
        }
    }
}
