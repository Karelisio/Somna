package com.karelisio.somna.data

import com.karelisio.somna.core.toLocalDateTime
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SleepStats(
    val nights: Int,
    val averageMinutes: Long,
    /** 0–100: how constant bedtimes and wake-up times are. */
    val regularity: Int,
    val averageRating: Float,
    /** Minutes after midnight (may be negative = before midnight). */
    val averageBedtimeMinutes: Int?,
    val averageWakeMinutes: Int?,
    val perDay: List<DayPoint>,
) {
    data class DayPoint(val date: LocalDate, val minutes: Long, val rating: Int)
}

class SleepRepository(private val dao: SleepDao, private val settings: SettingsRepository) {
    val entries: Flow<List<SleepEntry>> = dao.observeAll()

    fun stats(days: Int): Flow<SleepStats> {
        val since = System.currentTimeMillis() - days * DAY_MS
        return dao.observeSince(since).map { computeStats(it, days) }
    }

    suspend fun add(entry: SleepEntry): Long = dao.insert(entry)
    suspend fun update(entry: SleepEntry) = dao.update(entry)
    suspend fun delete(entry: SleepEntry) = dao.delete(entry)

    /** Marks "I'm going to bed now" so the morning entry has an accurate bedtime. */
    suspend fun markBedtime(at: Long = System.currentTimeMillis()) = settings.setPendingBedtime(at)

    /**
     * Logs the night when an alarm is dismissed. The bedtime is the one the user declared (if within
     * the last 16 h) or an estimate from the preferred cycles.
     */
    suspend fun recordWake(alarmTime: Long, wakeAt: Long = System.currentTimeMillis()): Long {
        val s = settings.current()
        val declared = s.pendingBedtime.takeIf { it in (wakeAt - 16 * HOUR_MS)..<wakeAt }
        val bedtime = declared ?: (alarmTime - (s.preferredCycles * s.cycleMinutes + s.fallAsleepMinutes) * 60_000L)
        settings.setPendingBedtime(0)
        settings.setBedtimeOverride(0)
        return dao.insert(SleepEntry(bedtime = bedtime, wakeTime = wakeAt))
    }

    /** Rates the most recent wake-up (from the alarm screen). */
    suspend fun rateLatest(rating: Int, maxAgeMs: Long = 2 * HOUR_MS) {
        val latest = dao.latest() ?: return
        if (System.currentTimeMillis() - latest.wakeTime <= maxAgeMs) dao.update(latest.copy(rating = rating))
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 24 * HOUR_MS

        fun computeStats(entries: List<SleepEntry>, days: Int): SleepStats {
            if (entries.isEmpty()) return SleepStats(0, 0, 0, 0f, null, null, emptyList())
            val avg = entries.sumOf { it.durationMinutes } / entries.size
            val bedMinutes = entries.map { minutesOfDay(it.bedtime) }
            val wakeMinutes = entries.map { minutesOfDay(it.wakeTime) }
            val bedSd = circularSd(bedMinutes)
            val wakeSd = circularSd(wakeMinutes)
            // 0 min deviation → 100 %, 90 min average deviation → 0 %.
            val regularity = if (entries.size < 2) 100 else (100 - (bedSd + wakeSd) / 2 / 0.9).roundToInt().coerceIn(0, 100)
            val rated = entries.filter { it.rating > 0 }
            val perDay = entries.groupBy { it.wakeTime.toLocalDateTime().toLocalDate() }
                .map { (date, list) -> SleepStats.DayPoint(date, list.sumOf { it.durationMinutes }, list.maxOf { it.rating }) }
                .sortedBy { it.date }
                .takeLast(days)
            return SleepStats(
                nights = entries.size,
                averageMinutes = avg,
                regularity = regularity,
                averageRating = if (rated.isEmpty()) 0f else rated.map { it.rating }.average().toFloat(),
                averageBedtimeMinutes = circularMean(bedMinutes).let { if (it > 12 * 60) it - 24 * 60 else it },
                averageWakeMinutes = circularMean(wakeMinutes),
                perDay = perDay,
            )
        }

        private fun minutesOfDay(epoch: Long): Int = epoch.toLocalDateTime().toLocalTime().let { it.hour * 60 + it.minute }

        private fun angle(minutes: Int) = minutes / 1440.0 * 2 * Math.PI

        private fun circularMean(values: List<Int>): Int {
            val s = values.sumOf { sin(angle(it)) }
            val c = values.sumOf { cos(angle(it)) }
            val a = atan2(s, c).let { if (it < 0) it + 2 * Math.PI else it }
            return (a / (2 * Math.PI) * 1440).roundToInt() % 1440
        }

        /** Standard deviation in minutes on the 24 h circle (23:50 and 00:10 are 20 min apart). */
        private fun circularSd(values: List<Int>): Double {
            if (values.size < 2) return 0.0
            val mean = circularMean(values)
            val diffs = values.map { v ->
                val d = abs(v - mean) % 1440
                minOf(d, 1440 - d).toDouble()
            }
            return sqrt(diffs.sumOf { it * it } / diffs.size)
        }
    }
}
