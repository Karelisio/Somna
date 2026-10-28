package com.karelisio.somna.alarm

import com.karelisio.somna.data.AlarmEntity
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NextTriggerTest {
    private val zone = ZoneId.of("Europe/Paris")
    // Wednesday 28 October 2026, 22:00
    private val now = ZonedDateTime.of(2026, 10, 28, 22, 0, 0, 0, zone)

    @Test
    fun `one-shot alarm rings tomorrow when the time has passed today`() {
        val next = AlarmScheduler.nextTrigger(6, 50, 0, now)
        assertEquals(ZonedDateTime.of(2026, 10, 29, 6, 50, 0, 0, zone), next)
    }

    @Test
    fun `one-shot alarm rings today when still ahead`() {
        val next = AlarmScheduler.nextTrigger(23, 15, 0, now)
        assertEquals(ZonedDateTime.of(2026, 10, 28, 23, 15, 0, 0, zone), next)
    }

    @Test
    fun `weekday alarm skips the weekend`() {
        val friday = ZonedDateTime.of(2026, 10, 30, 8, 0, 0, 0, zone)
        val next = AlarmScheduler.nextTrigger(7, 0, AlarmEntity.WEEKDAYS, friday)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(ZonedDateTime.of(2026, 11, 2, 7, 0, 0, 0, zone), next)
    }

    @Test
    fun `alarm on the same weekday a week later`() {
        val wednesdayOnly = 1 shl (DayOfWeek.WEDNESDAY.value - 1)
        val next = AlarmScheduler.nextTrigger(7, 0, wednesdayOnly, now)
        assertEquals(ZonedDateTime.of(2026, 11, 4, 7, 0, 0, 0, zone), next)
    }

    @Test
    fun `next occurrence is strictly after the given instant`() {
        val at = ZonedDateTime.of(2026, 10, 29, 6, 50, 0, 0, zone)
        assertEquals(at.plusDays(1), AlarmScheduler.nextTrigger(6, 50, AlarmEntity.EVERY_DAY, at))
    }

    @Test
    fun `daylight saving gap does not shift the following days`() {
        // Europe/Paris springs forward on 2027-03-28 (02:00 → 03:00).
        val saturday = ZonedDateTime.of(2027, 3, 27, 12, 0, 0, 0, zone)
        val weekdays = AlarmScheduler.nextTrigger(2, 30, AlarmEntity.WEEKDAYS, saturday)
        assertEquals(ZonedDateTime.of(2027, 3, 29, 2, 30, 0, 0, zone), weekdays)
        val afterGap = ZonedDateTime.of(2027, 3, 28, 3, 30, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2027, 3, 29, 2, 30, 0, 0, zone), AlarmScheduler.nextTrigger(2, 30, AlarmEntity.EVERY_DAY, afterGap))
    }

    @Test
    fun `spec survives json round trip`() {
        val spec = AlarmSpec(alarmId = 3, triggerAt = 123L, hour = 6, minute = 50, days = 31, sound = "BIRDS", mission = "MATH")
        assertEquals(spec, AlarmSpec.fromJson(spec.toJson()))
    }
}
