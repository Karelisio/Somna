package com.karelisio.somna.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.somna.container
import com.karelisio.somna.permissions.AppPermission
import com.karelisio.somna.permissions.AppPermissions
import com.karelisio.somna.permissions.OemBattery
import com.karelisio.somna.permissions.rememberPermissionRequester
import com.karelisio.somna.ui.components.AnimatedCheck
import com.karelisio.somna.ui.components.TopInsets
import com.karelisio.somna.ui.components.ShapeBadge
import com.karelisio.somna.ui.components.SpringCard
import com.karelisio.somna.ui.components.rememberStaggerState
import com.karelisio.somna.ui.components.staggerEnter
import kotlinx.coroutines.launch

/** Review the state of every permission and grant the missing ones. */
@Composable
fun PermissionsScreen(onBack: () -> Unit, onOpenBattery: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val requester = rememberPermissionRequester { refresh++ }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val stagger = rememberStaggerState()
    // Read on every resume / change.
    val version = requester.version + refresh
    val permissions = remember(version) { AppPermissions.all().map { it to it.isGranted(context) } }
    val granted = permissions.count { it.second }
    LaunchedEffect(version) {
        // Exact alarms may have just been granted: re-arm everything.
        context.container.appScope.launch { runCatching { context.container.alarms.rescheduleAll() } }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Autorisations") },
                subtitle = { Text("$granted sur ${permissions.size} accordées") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scrollBehavior,
                windowInsets = TopInsets,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            permissions.forEachIndexed { index, (permission, isGranted) ->
                PermissionCard(
                    permission, isGranted,
                    onRequest = { requester.request(permission) },
                    modifier = Modifier.staggerEnter(stagger, permission.id, index),
                )
            }
            SpringCard(
                onClick = onOpenBattery,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.staggerEnter(stagger, "oem", permissions.size),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.RocketLaunch, null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (OemBattery.isHonorOrHuawei) "Lancement des applis (${OemBattery.brand})" else "Restrictions du fabricant",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Étapes guidées pour que rien ne coupe Somna la nuit", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun PermissionCard(permission: AppPermission, granted: Boolean, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(
                if (permission.critical) MaterialShapes.Cookie9Sided else MaterialShapes.Pill,
                permission.icon,
                size = 48.dp,
                rotate = !granted,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(permission.title, style = MaterialTheme.typography.titleMedium)
                Text(permission.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            AnimatedContent(
                granted,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)).togetherWith(fadeOut()) },
                label = "granted",
            ) { ok ->
                if (ok) {
                    AnimatedCheck(size = 40.dp)
                } else {
                    Button(onClick = onRequest, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) {
                        Text("Autoriser")
                    }
                }
            }
        }
    }
}

