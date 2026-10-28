package com.karelisio.somna.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.core.toEpochMillis
import com.karelisio.somna.core.toLocalDateTime
import com.karelisio.somna.data.SleepEntry
import com.karelisio.somna.ui.components.EmojiRating
import com.karelisio.somna.ui.components.ImmersiveDialogWindow
import com.karelisio.somna.ui.components.SomnaTimePickerDialog
import com.karelisio.somna.ui.components.SpringCard
import java.time.Duration
import java.time.LocalDateTime

/** Bottom sheet to add or edit a night of the journal. */
@Composable
fun EntryEditorSheet(
    entry: SleepEntry,
    onDismiss: () -> Unit,
    onSave: (SleepEntry) -> Unit,
    onDelete: ((SleepEntry) -> Unit)?,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var bedtime by remember { mutableStateOf(entry.bedtime.toLocalDateTime()) }
    var wake by remember { mutableStateOf(entry.wakeTime.toLocalDateTime()) }
    var rating by remember { mutableStateOf(entry.rating) }
    var editing by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        ImmersiveDialogWindow()
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(if (entry.id == 0L) "Ajouter une nuit" else "Modifier la nuit", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeTile("Coucher", bedtime, Icons.Rounded.Bedtime, Modifier.weight(1f)) { editing = "bed" }
                TimeTile("Lever", wake, Icons.Rounded.WbSunny, Modifier.weight(1f)) { editing = "wake" }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Durée : ${TimeFormat.duration(Duration.between(bedtime, wake))}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text("Comment s'est passé le réveil ?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            EmojiRating(rating, { rating = it }, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = { onDelete(entry) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Text("Supprimer")
                    }
                }
                Button(
                    onClick = {
                        onSave(entry.copy(bedtime = bedtime.toEpochMillis(), wakeTime = wake.toEpochMillis(), rating = rating))
                    },
                    enabled = wake.isAfter(bedtime),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Enregistrer") }
            }
        }
    }

    editing?.let { which ->
        val current = if (which == "bed") bedtime else wake
        SomnaTimePickerDialog(
            title = if (which == "bed") "Heure de coucher" else "Heure de lever",
            initialHour = current.hour,
            initialMinute = current.minute,
            onDismiss = { editing = null },
            onConfirm = { h, m ->
                if (which == "bed") {
                    // Bedtime is the evening before the wake-up when it's later in the day.
                    var b = wake.toLocalDate().atTime(h, m)
                    if (!b.isBefore(wake)) b = b.minusDays(1)
                    bedtime = b
                } else {
                    var w = bedtime.toLocalDate().atTime(h, m)
                    if (!w.isAfter(bedtime)) w = w.plusDays(1)
                    wake = w
                }
                editing = null
            },
        )
    }
}

@Composable
private fun TimeTile(label: String, time: LocalDateTime, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    SpringCard(onClick = onClick, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            Text(TimeFormat.clock(time), style = MaterialTheme.typography.displaySmall)
            Text(TimeFormat.relativeDay(time.toLocalDate()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
