package com.karelisio.somna.core

import java.time.Duration
import java.time.LocalDateTime

/** One proposed bedtime or wake-up time, aligned on the end of a sleep cycle. */
data class SleepOption(
    val time: LocalDateTime,
    val cycles: Int,
    val sleepDuration: Duration,
) {
    val recommended: Boolean get() = cycles >= SleepCalculator.RECOMMENDED_MIN_CYCLES
    val ideal: Boolean get() = cycles == SleepCalculator.IDEAL_CYCLES
}

object SleepCalculator {
    const val DEFAULT_CYCLE_MINUTES = 90
    const val MIN_CYCLE_MINUTES = 80
    const val MAX_CYCLE_MINUTES = 110
    const val DEFAULT_FALL_ASLEEP_MINUTES = 14
    const val MAX_FALL_ASLEEP_MINUTES = 45
    const val RECOMMENDED_MIN_CYCLES = 5
    const val IDEAL_CYCLES = 6
    val PROPOSED_CYCLES = 4..6

    /** "Je me couche maintenant": wake-up times, chronological (4 → 6 cycles). */
    fun wakeTimesFor(
        bedtime: LocalDateTime,
        cycleMinutes: Int = DEFAULT_CYCLE_MINUTES,
        fallAsleepMinutes: Int = DEFAULT_FALL_ASLEEP_MINUTES,
        cycles: IntRange = PROPOSED_CYCLES,
    ): List<SleepOption> {
        val asleepAt = bedtime.plusMinutes(fallAsleepMinutes.toLong())
        return cycles.map { n ->
            val sleep = Duration.ofMinutes(n.toLong() * cycleMinutes.coerceIn(MIN_CYCLE_MINUTES, MAX_CYCLE_MINUTES))
            SleepOption(asleepAt.plus(sleep), n, sleep)
        }
    }

    /** "Je veux me lever à…": bedtimes, chronological (6 → 4 cycles). */
    fun bedtimesFor(
        wake: LocalDateTime,
        cycleMinutes: Int = DEFAULT_CYCLE_MINUTES,
        fallAsleepMinutes: Int = DEFAULT_FALL_ASLEEP_MINUTES,
        cycles: IntRange = PROPOSED_CYCLES,
    ): List<SleepOption> = cycles.reversed().map { n ->
        val sleep = Duration.ofMinutes(n.toLong() * cycleMinutes.coerceIn(MIN_CYCLE_MINUTES, MAX_CYCLE_MINUTES))
        SleepOption(wake.minus(sleep).minusMinutes(fallAsleepMinutes.toLong()), n, sleep)
    }

    /** Ideal bedtime before [wake] for the preferred number of cycles. */
    fun idealBedtime(wake: LocalDateTime, cycles: Int, cycleMinutes: Int, fallAsleepMinutes: Int): LocalDateTime =
        wake.minusMinutes(cycles.toLong() * cycleMinutes + fallAsleepMinutes)
}
