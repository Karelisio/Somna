package com.karelisio.somna.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.karelisio.somna.SomnaApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmFlowTest {
    private val app = ApplicationProvider.getApplicationContext<SomnaApp>()

    @After
    fun reset() {
        AlarmRuntime.session.value = null
        AlarmRuntime.outcome.value = null
    }

    private fun spec(test: Boolean = false) = AlarmSpec(
        alarmId = 42, triggerAt = System.currentTimeMillis(), hour = 6, minute = 50,
        sound = "BIRDS", snoozeMinutes = 9, test = test,
    )

    private fun intent(action: String, spec: AlarmSpec? = null) = Intent(app, AlarmService::class.java).setAction(action).apply {
        spec?.let { putExtra(AlarmScheduler.EXTRA_SPEC, it.toJson()) }
    }

    @Test
    fun `ringing posts a full-screen alarm notification, snooze re-arms an alarm clock`() {
        val controller = Robolectric.buildService(AlarmService::class.java, intent(AlarmService.ACTION_RING, spec(test = true)))
            .create().startCommand(0, 1)

        val session = AlarmRuntime.session.value
        assertNotNull(session)
        assertTrue(session!!.ringing)

        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        val ring = notifications.first { it.channelId == Notifications.CH_ALARM }
        assertNotNull("full-screen intent", ring.fullScreenIntent)
        assertEquals(Notification.CATEGORY_ALARM, ring.category)

        controller.withIntent(intent(AlarmService.ACTION_SNOOZE)).startCommand(0, 2)
        assertNull(AlarmRuntime.session.value)
        val outcome = AlarmRuntime.outcome.value as AlarmOutcome.Snoozed
        assertTrue(outcome.until - System.currentTimeMillis() in (8 * 60_000L)..(9 * 60_000L + 1_000))

        val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .firstOrNull { it.alarmClockInfo != null }
        assertNotNull("snooze uses setAlarmClock", alarm)
        assertEquals(outcome.until, alarm!!.triggerAtTime)
    }

    @Test
    fun `dismiss during sunrise ends the session`() {
        val controller = Robolectric.buildService(AlarmService::class.java, intent(AlarmService.ACTION_SUNRISE, spec(test = true)))
            .create().startCommand(0, 1)
        assertNotNull(AlarmRuntime.session.value?.sunriseStart)
        val sunrise = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
            .first { it.channelId == Notifications.CH_SUNRISE }
        assertNotNull(sunrise.fullScreenIntent)

        controller.withIntent(intent(AlarmService.ACTION_DISMISS)).startCommand(0, 2)
        assertNull(AlarmRuntime.session.value)
        assertTrue(AlarmRuntime.outcome.value is AlarmOutcome.Dismissed)
    }

    @Test
    fun `while ringing, a backup re-ring is armed and another alarm is requeued, not lost`() {
        val controller = Robolectric.buildService(AlarmService::class.java, intent(AlarmService.ACTION_RING, spec(test = true)))
            .create().startCommand(0, 1)
        val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
        assertTrue("backup re-ring", alarmManager.scheduledAlarms.any { it.alarmClockInfo != null && it.triggerAtTime > System.currentTimeMillis() + 60_000 })

        val other = spec().copy(alarmId = 7, test = false)
        controller.withIntent(intent(AlarmService.ACTION_RING, other)).startCommand(0, 2)
        assertEquals(42, AlarmRuntime.session.value!!.spec.alarmId)
        val requeued = alarmManager.scheduledAlarms.filter { it.alarmClockInfo != null }
            .any { it.triggerAtTime in (System.currentTimeMillis() + 50_000)..(System.currentTimeMillis() + 61_000) }
        assertTrue("second alarm requeued", requeued)
    }

    @Test
    fun `a deleted repeating alarm is not resurrected when its occurrence is consumed`() = runBlocking {
        val alarm = app.container.alarms.create(7, 0, days = com.karelisio.somna.data.AlarmEntity.EVERY_DAY)
        val spec = app.container.scheduler.snapshot.specs().first { it.alarmId == alarm.id }
        app.container.alarms.delete(alarm)
        app.container.alarms.onOccurrenceConsumed(spec)
        assertTrue(app.container.scheduler.snapshot.specs().none { it.alarmId == alarm.id })
    }

    @Test
    fun `an occurrence consumed early is not re-armed by a later reschedule`() = runBlocking {
        val alarm = app.container.alarms.create(7, 0, days = com.karelisio.somna.data.AlarmEntity.EVERY_DAY)
        val next = AlarmScheduler.nextTrigger(7, 0, alarm.days).toInstant().toEpochMilli()
        app.container.scheduler.snapshot.markConsumed(alarm.id, next)
        app.container.alarms.rescheduleAll()
        val armed = app.container.scheduler.snapshot.specs().first { it.alarmId == alarm.id }.triggerAt
        assertEquals(AlarmScheduler.nextTrigger(7, 0, alarm.days, AlarmScheduler.zoned(next)).toInstant().toEpochMilli(), armed)
    }

    @Test
    fun `skipping the next occurrence arms the following one, and can be undone`() = runBlocking {
        val alarm = app.container.alarms.create(7, 0, days = com.karelisio.somna.data.AlarmEntity.EVERY_DAY)
        val upcoming = AlarmScheduler.nextTrigger(7, 0, alarm.days)
        app.container.alarms.skipNext(alarm)
        assertTrue(app.container.scheduler.isSkipped(alarm))
        val following = AlarmScheduler.nextTrigger(7, 0, alarm.days, upcoming).toInstant().toEpochMilli()
        assertEquals(following, app.container.scheduler.snapshot.specs().first { it.alarmId == alarm.id }.triggerAt)
        assertEquals(following, app.container.scheduler.effectiveNext(alarm).toInstant().toEpochMilli())

        app.container.alarms.unskip(alarm)
        assertTrue(!app.container.scheduler.isSkipped(alarm))
        assertEquals(upcoming.toInstant().toEpochMilli(), app.container.scheduler.snapshot.specs().first { it.alarmId == alarm.id }.triggerAt)
    }

    @Test
    fun `a nap is a one-shot alarm without sunrise nor smart wake`() = runBlocking {
        val before = java.time.LocalDateTime.now()
        val nap = app.container.alarms.createNap(20)
        assertEquals("Sieste", nap.label)
        assertTrue(!nap.sunrise && nap.smartWakeMinutes == 0 && !nap.repeating)
        val expected = before.plusMinutes(14L + 20 + 1)
        val minutes = nap.hour * 60 + nap.minute
        val diff = Math.floorMod(minutes - (expected.hour * 60 + expected.minute), 1440)
        assertTrue("nap at ${nap.hour}:${nap.minute}, expected ≈ $expected", diff <= 1)
    }

    @Test
    fun `creating an alarm schedules an alarm clock plus the sunrise`() = runBlocking {
        val alarm = app.container.alarms.create(6, 50)
        val scheduled = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
        val clock = scheduled.first { it.alarmClockInfo != null }
        val next = AlarmScheduler.nextTrigger(alarm.hour, alarm.minute, alarm.days).toInstant().toEpochMilli()
        assertEquals(next, clock.triggerAtTime)
        assertTrue("sunrise 10 min before", scheduled.any { it.triggerAtTime == next - 10 * 60_000L })
        assertTrue(app.container.scheduler.snapshot.specs().any { it.alarmId == alarm.id && it.triggerAt == next })
    }
}
