package com.karelisio.somna.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object TimeFormat {
    private val french = Locale.FRENCH
    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", french)

    /** "06:50" — big clock digits. */
    fun clock(time: LocalTime): String = time.format(clockFormatter)
    fun clock(time: LocalDateTime): String = clock(time.toLocalTime())
    fun clock(epochMillis: Long): String = clock(epochMillis.toLocalDateTime())

    /** "6h50" — French short style used in sentences and the widget. */
    fun short(time: LocalTime): String = "${time.hour}h%02d".format(time.minute)
    fun short(time: LocalDateTime): String = short(time.toLocalTime())

    /** "7 h 30", "45 min". */
    fun duration(duration: Duration): String {
        val total = duration.toMinutes().coerceAtLeast(0)
        val h = total / 60
        val m = total % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h %02d".format(m)
        }
    }

    fun durationMinutes(minutes: Long): String = duration(Duration.ofMinutes(minutes))

    /** "dans 7 h 12" / "dans 4 min". */
    fun countdown(from: LocalDateTime, to: LocalDateTime): String {
        val minutes = Duration.between(from, to).toMinutes().coerceAtLeast(0) + 1
        return "dans " + durationMinutes(minutes)
    }

    fun day(date: LocalDate): String = date.format(dayFormatter).replaceFirstChar { it.titlecase(french) }

    fun dayName(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, french).trimEnd('.').replaceFirstChar { it.titlecase(french) }

    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Aujourd'hui"
        today.plusDays(1) -> "Demain"
        today.minusDays(1) -> "Hier"
        else -> day(date)
    }
}

fun Long.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

fun LocalDateTime.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atZone(zone).toInstant().toEpochMilli()
