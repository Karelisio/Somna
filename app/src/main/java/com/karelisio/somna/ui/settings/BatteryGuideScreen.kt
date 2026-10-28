package com.karelisio.somna.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.somna.permissions.AppPermissions
import com.karelisio.somna.permissions.OemBattery
import com.karelisio.somna.permissions.rememberPermissionRequester
import com.karelisio.somna.ui.components.AnimatedCheck
import com.karelisio.somna.ui.components.ShapeContainer
import com.karelisio.somna.ui.components.rememberHaptics

/** Step-by-step guide so the OS never kills Somna at night (Honor / MagicOS first). */
@Composable
fun BatteryGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var refresh by remember { mutableIntStateOf(0) }
    val requester = rememberPermissionRequester { refresh++ }
    val battery = remember { AppPermissions.byId(AppPermissions.BATTERY) }
    val batteryOk = remember(requester.version, refresh) { battery?.isGranted(context) == true }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val honor = OemBattery.isHonorOrHuawei

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Batterie") },
                subtitle = { Text(if (honor) "Guide ${OemBattery.brand} / MagicOS" else "Guide ${OemBattery.brand}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Pour économiser la batterie, certains fabricants ferment les applis en arrière-plan, ce qui peut empêcher un réveil de sonner. Trois réglages suffisent à l'éviter.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Step(
                number = 1,
                title = "Ne pas optimiser Somna",
                body = "Autorise Somna à ignorer l'optimisation de la batterie d'Android.",
                done = batteryOk,
            ) {
                Button(
                    onClick = {
                        haptics.click()
                        battery?.let(requester::request)
                    },
                    enabled = !batteryOk,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.BatteryChargingFull, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (batteryOk) "C'est fait" else "Autoriser")
                }
            }

            Step(
                number = 2,
                title = if (honor) "Gestion manuelle du lancement" else "Lancement automatique",
                body = if (honor) {
                    "Paramètres › Batterie › Lancement des applications › Somna : désactive « Gérer automatiquement », puis active « Lancement automatique », « Lancement secondaire » et « Exécution en arrière-plan »."
                } else {
                    "Dans les réglages du fabricant (démarrage automatique, applis protégées…), autorise Somna à démarrer et à rester active en arrière-plan."
                },
                done = null,
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.click()
                        OemBattery.openLaunchManager(context)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.RocketLaunch, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ouvrir les réglages")
                }
            }

            Step(
                number = 3,
                title = "Verrouiller Somna dans les applis récentes",
                body = if (honor) {
                    "Ouvre l'écran des applis récentes, fais glisser la carte Somna vers le bas (ou appuie longuement) pour afficher le cadenas 🔒. Elle ne sera plus fermée par « Tout effacer »."
                } else {
                    "Dans les applis récentes, appuie longuement sur Somna et choisis « Verrouiller » si ton téléphone le propose."
                },
                done = null,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("À faire depuis l'écran des applis récentes", style = MaterialTheme.typography.labelLarge)
                }
            }

            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(
                    "Astuce : utilise « Tester le réveil » dans les réglages, éteins l'écran et attends. Si tout sonne, c'est bon pour la nuit.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(18.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String, done: Boolean?, action: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(done == true, label = "step$number") { ok ->
                        if (ok) {
                            AnimatedCheck(size = 44.dp)
                        } else {
                            ShapeContainer(MaterialShapes.Cookie7Sided, size = 44.dp, rotate = false) {
                                Text("$number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}
