package com.karelisio.somna.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.somna.permissions.AppPermission
import com.karelisio.somna.permissions.AppPermissions
import com.karelisio.somna.permissions.rememberPermissionRequester
import com.karelisio.somna.ui.LocalSomna
import com.karelisio.somna.ui.components.AnimatedCheck
import com.karelisio.somna.ui.components.ShapeContainer
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.ui.components.rememberMorphShape
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Page {
    data object Welcome : Page
    data object Cycles : Page
    data object Gentle : Page
    data class Permission(val permission: AppPermission) : Page
    data object Done : Page
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val somna = LocalSomna.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pages = remember {
        listOf(Page.Welcome, Page.Cycles, Page.Gentle) + AppPermissions.all().map { Page.Permission(it) } + Page.Done
    }
    val pager = rememberPagerState { pages.size }
    var refresh by remember { mutableIntStateOf(0) }
    val requester = rememberPermissionRequester { refresh++ }
    val version = requester.version + refresh
    val granted = remember(version) { pages.filterIsInstance<Page.Permission>().associate { it.permission.id to it.permission.isGranted(context) } }

    fun next() = scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pages.lastIndex)) }

    // Auto-advance shortly after a permission gets granted on its page (time for the check animation).
    val current = pages[pager.currentPage]
    val currentGranted = (current as? Page.Permission)?.let { granted[it.permission.id] } == true
    var requestedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current, currentGranted) {
        if (current is Page.Permission && currentGranted && requestedId == current.permission.id) {
            requestedId = null
            delay(1_100)
            if (pages[pager.currentPage] == current) next()
        }
    }

    fun finish() {
        val skippedNow = pages.filterIsInstance<Page.Permission>().filter { granted[it.permission.id] != true }.map { it.permission.id }.toSet()
        somna.edit {
            setSkippedPermissions(skippedNow)
            setOnboardingDone(true)
        }
        haptics.confirm()
        onFinished()
    }

    val background = Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface),
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).background(background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
            ) { index ->
                val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - offset * 0.6f
                        val s = 1f - offset * 0.12f
                        scaleX = s
                        scaleY = s
                    },
                ) {
                    when (val page = pages[index]) {
                        Page.Welcome -> IntroPage(
                            title = "Somna",
                            body = "Réveille-toi entre deux cycles de sommeil, pas au milieu d'un rêve.",
                        ) { MoonWavesIllustration() }
                        Page.Cycles -> IntroPage(
                            title = "Dors en cycles",
                            body = "Un cycle dure environ 90 minutes. Somna calcule tes heures de coucher et de réveil pour que tu te lèves en sommeil léger, sans sensation de lourdeur.",
                        ) { CyclesIllustration() }
                        Page.Gentle -> IntroPage(
                            title = "Un réveil tout en douceur",
                            body = "L'écran s'illumine comme un lever de soleil, des sons doux montent en crescendo, la vibration aussi. En option, le réveil intelligent guette ton sommeil léger.",
                        ) { SunriseIllustration() }
                        is Page.Permission -> PermissionPage(page.permission, granted[page.permission.id] == true)
                        Page.Done -> DonePage(missing = granted.count { !it.value })
                    }
                }
            }

            PageIndicator(pager, pages.size, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (current) {
                    Page.Welcome, Page.Cycles, Page.Gentle -> {
                        TextButton(onClick = {
                            scope.launch { pager.animateScrollToPage(pages.indexOfFirst { it is Page.Permission }.coerceAtLeast(0)) }
                        }) { Text("Passer l'intro") }
                        Spacer(Modifier.weight(1f))
                        PrimaryButton("Suivant") {
                            haptics.click()
                            next()
                        }
                    }
                    is Page.Permission -> {
                        val ok = granted[current.permission.id] == true
                        TextButton(
                            onClick = { next() },
                            enabled = !ok,
                        ) { Text(if (ok) "" else "Plus tard") }
                        Spacer(Modifier.weight(1f))
                        PrimaryButton(if (ok) "Continuer" else "Autoriser") {
                            haptics.click()
                            if (ok) {
                                next()
                            } else {
                                requestedId = current.permission.id
                                requester.request(current.permission)
                            }
                        }
                    }
                    Page.Done -> {
                        Spacer(Modifier.weight(1f))
                        PrimaryButton("Commencer") { finish() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.height(56.dp),
        contentPadding = ButtonDefaults.contentPaddingFor(56.dp),
    ) {
        AnimatedContent(text, label = "primaryText") { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun PageIndicator(pager: PagerState, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = pager.currentPage == i
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dotWidth")
            val color by animateColorAsState(
                when {
                    selected -> MaterialTheme.colorScheme.primary
                    i < pager.currentPage -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                MaterialTheme.motionScheme.fastEffectsSpec(), label = "dotColor",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun IntroPage(title: String, body: String, illustration: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        illustration()
        Spacer(Modifier.height(36.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionPage(permission: AppPermission, granted: Boolean) {
    val transition = rememberInfiniteTransition(label = "permIcon")
    val pulse by transition.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(1_400), RepeatMode.Reverse), label = "pulse")
    val shape = rememberMorphShape(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, granted, spin = true)
    val container by animateColorAsState(
        if (granted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.motionScheme.slowEffectsSpec(), label = "permColor",
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            ShapeContainer(MaterialShapes.SoftBurst, size = 220.dp, containerColor = container.copy(alpha = 0.35f), rotationPeriodMs = 30_000)
            Box(
                Modifier
                    .size(148.dp)
                    .graphicsLayer {
                        val s = if (granted) 1f else pulse
                        scaleX = s
                        scaleY = s
                    }
                    .clip(shape)
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    granted,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.4f)).togetherWith(fadeOut()) },
                    label = "permState",
                ) { ok ->
                    if (ok) {
                        AnimatedCheck(size = 76.dp)
                    } else {
                        Icon(permission.icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(permission.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            permission.rationale,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        AnimatedContent(granted, label = "permLabel") { ok ->
            Text(
                when {
                    ok -> "Autorisation accordée"
                    permission.runtimePermission == null -> "S'ouvre dans les réglages système · retour automatique"
                    permission.critical -> "Indispensable au réveil"
                    else -> "Recommandé"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DonePage(missing: Int) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ShapeContainer(MaterialShapes.Sunny, size = 220.dp, containerColor = MaterialTheme.colorScheme.tertiaryContainer, rotationPeriodMs = 20_000)
            AnimatedCheck(size = 96.dp)
        }
        Spacer(Modifier.height(32.dp))
        Text("Tout est prêt", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            if (missing == 0) "Somna peut te réveiller en douceur, à coup sûr. Bonne nuit 🌙"
            else "Il reste $missing autorisation${if (missing > 1) "s" else ""} à accorder. Somna te le rappellera discrètement ; tu les retrouves dans Réglages › Autorisations.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
