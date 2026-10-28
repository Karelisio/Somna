package com.karelisio.somna.core

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepCalculatorTest {
    private val evening = LocalDateTime.of(2026, 10, 28, 23, 0)

    @Test
    fun `sleeping now proposes 4 to 6 cycles after falling asleep`() {
        val options = SleepCalculator.wakeTimesFor(evening, cycleMinutes = 90, fallAsleepMinutes = 14)
        assertEquals(listOf(4, 5, 6), options.map { it.cycles })
        assertEquals(LocalDateTime.of(2026, 10, 29, 5, 14), options[0].time)
        assertEquals(LocalDateTime.of(2026, 10, 29, 6, 44), options[1].time)
        assertEquals(LocalDateTime.of(2026, 10, 29, 8, 14), options[2].time)
    }

    @Test
    fun `bedtimes for a wake-up time are chronological and account for falling asleep`() {
        val wake = LocalDateTime.of(2026, 10, 29, 7, 0)
        val options = SleepCalculator.bedtimesFor(wake, cycleMinutes = 90, fallAsleepMinutes = 14)
        assertEquals(listOf(6, 5, 4), options.map { it.cycles })
        assertEquals(LocalDateTime.of(2026, 10, 28, 21, 46), options[0].time)
        assertEquals(LocalDateTime.of(2026, 10, 28, 23, 16), options[1].time)
        assertEquals(LocalDateTime.of(2026, 10, 29, 0, 46), options[2].time)
    }

    @Test
    fun `cycle length is clamped to 80-110 minutes`() {
        val short = SleepCalculator.wakeTimesFor(evening, cycleMinutes = 30, fallAsleepMinutes = 0, cycles = 1..1)
        assertEquals(80, short.single().sleepDuration.toMinutes())
        val long = SleepCalculator.wakeTimesFor(evening, cycleMinutes = 200, fallAsleepMinutes = 0, cycles = 1..1)
        assertEquals(110, long.single().sleepDuration.toMinutes())
    }

    @Test
    fun `five and six cycles are highlighted`() {
        val options = SleepCalculator.wakeTimesFor(evening)
        assertFalse(options.first { it.cycles == 4 }.recommended)
        assertTrue(options.first { it.cycles == 5 }.recommended)
        assertTrue(options.first { it.cycles == 6 }.ideal)
    }

    @Test
    fun `french short time format`() {
        assertEquals("6h50", TimeFormat.short(LocalDateTime.of(2026, 1, 1, 6, 50)))
        assertEquals("7 h 30", TimeFormat.durationMinutes(450))
        assertEquals("45 min", TimeFormat.durationMinutes(45))
    }
}
