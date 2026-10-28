package com.karelisio.somna.data

import com.karelisio.somna.core.toEpochMillis
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStatsTest {
    private fun night(day: Int, bedH: Int, bedM: Int, wakeH: Int, wakeM: Int, rating: Int = 4) = SleepEntry(
        bedtime = LocalDateTime.of(2026, 10, day, bedH, bedM).toEpochMillis(),
        wakeTime = LocalDateTime.of(2026, 10, day + 1, wakeH, wakeM).toEpochMillis(),
        rating = rating,
    )

    @Test
    fun `perfectly regular nights score 100`() {
        val entries = (1..5).map { night(it, 23, 0, 7, 0) }
        val stats = SleepRepository.computeStats(entries, 7)
        assertEquals(480, stats.averageMinutes)
        assertEquals(100, stats.regularity)
        assertEquals(-60, stats.averageBedtimeMinutes)
        assertEquals(7 * 60, stats.averageWakeMinutes)
    }

    @Test
    fun `bedtimes around midnight average correctly`() {
        val entries = listOf(night(1, 23, 50, 7, 0), night(2, 23, 50, 7, 0))
            .plus(SleepEntry(bedtime = LocalDateTime.of(2026, 10, 4, 0, 10).toEpochMillis(), wakeTime = LocalDateTime.of(2026, 10, 4, 7, 0).toEpochMillis()))
        val stats = SleepRepository.computeStats(entries, 7)
        val bed = stats.averageBedtimeMinutes!!
        assertTrue("average bedtime $bed should be close to midnight", bed in -15..15)
        assertTrue(stats.regularity > 80)
    }

    @Test
    fun `irregular nights lower the score`() {
        val entries = listOf(night(1, 21, 0, 5, 0), night(2, 2, 0, 11, 0), night(3, 23, 0, 7, 0))
        assertTrue(SleepRepository.computeStats(entries, 7).regularity < 40)
    }
}
