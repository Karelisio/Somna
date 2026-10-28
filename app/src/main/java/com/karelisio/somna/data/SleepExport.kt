package com.karelisio.somna.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.karelisio.somna.core.toLocalDateTime
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Journal export as CSV (";" separator + BOM, opens cleanly in a French spreadsheet). */
object SleepExport {
    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val moods = listOf("", "Épuisé", "Fatigué", "Moyen", "Bien", "En pleine forme")

    fun csv(entries: List<SleepEntry>): String = buildString {
        append('\uFEFF')
        append("date;coucher;lever;durée (min);durée;note (1-5);humeur\n")
        entries.sortedBy { it.wakeTime }.forEach { e ->
            val bed = e.bedtime.toLocalDateTime()
            val wake = e.wakeTime.toLocalDateTime()
            val minutes = e.durationMinutes
            append(wake.format(date)).append(';')
            append(bed.format(time)).append(';')
            append(wake.format(time)).append(';')
            append(minutes).append(';')
            append("%dh%02d".format(minutes / 60, minutes % 60)).append(';')
            append(if (e.rating > 0) e.rating.toString() else "").append(';')
            append(moods.getOrElse(e.rating) { "" }).append('\n')
        }
    }

    /** Writes the CSV to the cache and returns a share sheet intent for it. */
    fun shareIntent(context: Context, entries: List<SleepEntry>): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "somna-journal-${LocalDate.now().format(date)}.csv")
        file.writeText(csv(entries))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Journal de sommeil Somna")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Exporter le journal")
    }
}
