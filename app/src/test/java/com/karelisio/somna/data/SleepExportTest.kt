package com.karelisio.somna.data

import com.karelisio.somna.core.toEpochMillis
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepExportTest {
    @Test
    fun `csv has a header and one sorted line per night`() {
        val csv = SleepExport.csv(
            listOf(
                SleepEntry(bedtime = LocalDateTime.of(2026, 10, 27, 23, 10).toEpochMillis(), wakeTime = LocalDateTime.of(2026, 10, 28, 6, 50).toEpochMillis(), rating = 5),
                SleepEntry(bedtime = LocalDateTime.of(2026, 10, 25, 22, 45).toEpochMillis(), wakeTime = LocalDateTime.of(2026, 10, 26, 7, 0).toEpochMillis()),
            ),
        )
        val lines = csv.removePrefix("\uFEFF").trimEnd().lines()
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals("date;coucher;lever;durée (min);durée;note (1-5);humeur", lines[0])
        assertEquals("2026-10-26;22:45;07:00;495;8h15;;", lines[1])
        assertEquals("2026-10-28;23:10;06:50;460;7h40;5;En pleine forme", lines[2])
    }
}
