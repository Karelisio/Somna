package com.karelisio.somna.ui.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.somna.BuildConfig
import com.karelisio.somna.permissions.AppPermissions
import com.karelisio.somna.permissions.rememberPermissionRequester
import com.karelisio.somna.ui.LocalSomna
import com.karelisio.somna.ui.components.AnimatedCheck
import com.karelisio.somna.ui.components.ShapeBadge
import com.karelisio.somna.ui.components.rememberHaptics
import com.karelisio.somna.update.InstallState
import com.karelisio.somna.update.ReleaseInfo
import com.karelisio.somna.update.UpdateInstaller
import java.util.Locale

/** Shows the update bottom sheet when a newer release is available (and not skipped / dismissed). */
@Composable
fun UpdateSheetHost() {
    val somna = LocalSomna.current
    val pending by somna.pendingUpdate.collectAsStateWithLifecycle()
    val info = pending ?: return
    if (somna.updateSheetDismissed && !somna.forceShowUpdate) return
    UpdateSheet(
        info = info,
        onClose = {
            somna.updateSheetDismissed = true
            somna.forceShowUpdate = false
            val state = UpdateInstaller.state.value
            if (state is InstallState.Failed || state is InstallState.NeedsPermission) UpdateInstaller.reset()
        },
        onSkip = {
            somna.skipVersion(info.version)
            somna.updateSheetDismissed = true
            somna.forceShowUpdate = false
        },
    )
}

@Composable
private fun UpdateSheet(info: ReleaseInfo, onClose: () -> Unit, onSkip: () -> Unit) {
    val somna = LocalSomna.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val state by UpdateInstaller.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var refresh by remember { mutableIntStateOf(0) }
    val requester = rememberPermissionRequester { refresh++ }
    val installPermission = remember { AppPermissions.byId(AppPermissions.INSTALL) }

    // Permission granted from the explanation screen: continue right away.
    LaunchedEffect(requester.version, refresh, state) {
        if (state is InstallState.NeedsPermission && UpdateInstaller.canInstall(context)) somna.startUpdate(context, info)
    }

    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(MaterialShapes.Cookie9Sided, Icons.Rounded.SystemUpdate, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Somna ${info.version}", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Nouvelle version · tu as la ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Nouveautés", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Changelog(info.changelog.ifBlank { "Améliorations et corrections." })
                }
            }
            Spacer(Modifier.height(20.dp))
            AnimatedContent(
                targetState = state.phase(),
                transitionSpec = { fadeIn(enter).togetherWith(fadeOut(exit)) },
                label = "updateState",
            ) { phase ->
                when (phase) {
                    Phase.Ready -> Column {
                        val failed = state as? InstallState.Failed
                        if (failed != null) {
                            Text(failed.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                        }
                        Button(
                            onClick = {
                                haptics.confirm()
                                somna.startUpdate(context, info)
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(10.dp))
                            Text(if (failed != null) "Réessayer" else "Mettre à jour", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onSkip) { Text("Ignorer cette version") }
                            TextButton(onClick = onClose) { Text("Plus tard") }
                        }
                    }
                    Phase.Permission -> Column {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.InstallMobile, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Pour installer la mise à jour, Android doit autoriser Somna à installer des applis. Tu reviendras ici automatiquement.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { installPermission?.let(requester::request) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("Autoriser", style = MaterialTheme.typography.titleMedium) }
                    }
                    Phase.Downloading -> {
                        val downloading = state as? InstallState.Downloading
                        val progress by animateFloatAsState(
                            downloading?.progress ?: 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "download",
                        )
                        Column {
                            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(14.dp))
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Text("Téléchargement…", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(
                                    "${(progress * 100).toInt()} %" + (downloading?.takeIf { it.total > 0 }?.let { " · ${mb(it.bytes)} / ${mb(it.total)} Mo" } ?: ""),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Phase.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when (state) {
                                InstallState.Verifying -> "Vérification de la signature…"
                                InstallState.WaitingForUser -> "Confirme l'installation dans la fenêtre d'Android"
                                else -> "Installation…"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Phase.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedCheck(size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Mise à jour installée. Somna va redémarrer.", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

private enum class Phase { Ready, Permission, Downloading, Working, Done }

private fun InstallState.phase(): Phase = when (this) {
    InstallState.Idle, is InstallState.Failed -> Phase.Ready
    InstallState.NeedsPermission -> Phase.Permission
    is InstallState.Downloading -> Phase.Downloading
    InstallState.Verifying, InstallState.Installing, InstallState.WaitingForUser -> Phase.Working
    InstallState.Success -> Phase.Done
}

private fun mb(bytes: Long) = String.format(Locale.FRENCH, "%.1f", bytes / 1_048_576f)

/** Minimal Markdown rendering for GitHub release notes: headings, bullets, **bold**. */
@Composable
private fun Changelog(markdown: String) {
    markdown.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.forEach { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> Text(
                bold(trimmed.trimStart('#').trim()),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(bold(trimmed.drop(2)), style = MaterialTheme.typography.bodyMedium)
            }
            else -> Text(bold(trimmed), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun bold(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part.replace("`", ""))
    }
}
