package com.karelisio.somna.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.karelisio.somna.MainActivity
import com.karelisio.somna.data.AlarmEntity
import com.karelisio.somna.data.SomnaSettings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything needed to ring an alarm occurrence, carried in the PendingIntent itself so the alarm
 * still works right after a reboot, before the user unlocks the phone (Direct Boot).
 */
@Serializable
data class AlarmSpec(
    val alarmId: Int,
    val triggerAt: Long,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val label: String = "",
    val sound: String,
    val vibrate: Boolean = true,
    val sunriseMinutes: Int = 0,
    val smartMinutes: Int = 0,
    val smartSensitivity: Int = 1,
    val mission: String = "NONE",
    val snoozeMinutes: Int = 9,
    val crescendoSeconds: Int = 90,
    val volume: Float = 0.8f,
    val snoozeCount: Int = 0,
    val test: Boolean = false,
    /** Rung before its time by the smart wake (light sleep detected). */
    val early: Boolean = false,
    /** Safety re-ring armed while ringing, in case the process gets killed. */
    val backup: Boolean = false,
) {
    val repeating: Boolean get() = days != 0

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun fromJson(value: String?): AlarmSpec? =
            value?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    val snapshot = AlarmSnapshot(context)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun buildSpec(alarm: AlarmEntity, settings: SomnaSettings, triggerAt: Long) = AlarmSpec(
        alarmId = alarm.id,
        triggerAt = triggerAt,
        hour = alarm.hour,
        minute = alarm.minute,
        days = alarm.days,
        label = alarm.label,
        sound = alarm.sound,
        vibrate = alarm.vibrate,
        sunriseMinutes = if (alarm.sunrise) settings.sunriseMinutes else 0,
        smartMinutes = alarm.smartWakeMinutes,
        smartSensitivity = settings.smartSensitivity,
        mission = alarm.mission,
        snoozeMinutes = settings.snoozeMinutes,
        crescendoSeconds = settings.crescendoSeconds,
        volume = settings.alarmVolume,
    )

    /** Schedules the next occurrence of [alarm] (or cancels it when disabled). */
    fun schedule(alarm: AlarmEntity, settings: SomnaSettings, after: ZonedDateTime = ZonedDateTime.now()) {
        cancel(alarm.id)
        if (!alarm.enabled) {
            snapshot.remove(alarm.id)
            return
        }
        val trigger = effectiveNext(alarm, after)
        scheduleSpec(buildSpec(alarm, settings, trigger.toInstant().toEpochMilli()))
    }

    /**
     * Next time [alarm] will really ring: an occurrence already consumed early (smart wake,
     * "je suis réveillé") or skipped by the user ("passer le prochain") is never rung again.
     */
    fun effectiveNext(alarm: AlarmEntity, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val trigger = nextTrigger(alarm.hour, alarm.minute, alarm.days, after)
        return if (isSkipped(alarm, trigger)) nextTrigger(alarm.hour, alarm.minute, alarm.days, trigger) else trigger
    }

    /** True when the upcoming occurrence of a repeating alarm has been skipped. */
    fun isSkipped(alarm: AlarmEntity, upcoming: ZonedDateTime = nextTrigger(alarm.hour, alarm.minute, alarm.days)): Boolean =
        alarm.repeating && upcoming.toInstant().toEpochMilli() == snapshot.consumedAt(alarm.id)

    fun scheduleSpec(spec: AlarmSpec) {
        setAlarmClock(spec.triggerAt, pending(KIND_RING, spec))
        val now = System.currentTimeMillis()
        if (spec.sunriseMinutes > 0) {
            val at = spec.triggerAt - spec.sunriseMinutes * 60_000L
            if (at > now) setExact(at, pending(KIND_SUNRISE, spec))
        }
        if (spec.smartMinutes > 0) {
            val at = spec.triggerAt - spec.smartMinutes * 60_000L
            if (at > now) setExact(at, pending(KIND_SMART, spec))
        }
        if (!spec.test) snapshot.put(spec)
    }

    fun scheduleSnooze(spec: AlarmSpec): AlarmSpec {
        val at = System.currentTimeMillis() + spec.snoozeMinutes * 60_000L
        val snoozed = spec.copy(
            triggerAt = at, sunriseMinutes = 0, smartMinutes = 0, snoozeCount = spec.snoozeCount + 1,
            early = false, backup = false,
        )
        setAlarmClock(at, pending(KIND_SNOOZE, snoozed))
        if (!spec.test) snapshot.putSnooze(snoozed)
        return snoozed
    }

    /** Smart wake: ring now through an alarm clock, so the service is (re)started with the exact-alarm exemption. */
    fun ringNow(spec: AlarmSpec) {
        setAlarmClock(System.currentTimeMillis() + 1_000, pending(KIND_RING, spec.copy(early = true)))
    }

    /** Another alarm is ringing: deliver this one a bit later instead of losing it. */
    fun requeue(spec: AlarmSpec, delayMs: Long = 60_000L) {
        setAlarmClock(System.currentTimeMillis() + delayMs, pending(KIND_SNOOZE, spec.copy(triggerAt = System.currentTimeMillis() + delayMs)))
    }

    /** While ringing, keeps a re-ring armed shortly after now: if the process dies, the alarm comes back. */
    fun armBackup(spec: AlarmSpec) {
        val at = System.currentTimeMillis() + 90_000L
        setAlarmClock(at, pending(KIND_BACKUP, spec.copy(backup = true)))
    }

    fun cancelBackup(alarmId: Int) = cancelKind(alarmId, KIND_BACKUP)

    /** Re-arms a pending snooze at its original time (after a reboot). */
    fun rearmSnooze(spec: AlarmSpec) {
        setAlarmClock(spec.triggerAt, pending(KIND_SNOOZE, spec))
    }

    /** Cancels the occurrence phases (ring, sunrise, smart window) of an alarm, not its snooze. */
    fun cancel(alarmId: Int) {
        listOf(KIND_RING, KIND_SUNRISE, KIND_SMART).forEach { cancelKind(alarmId, it) }
    }

    fun cancelSnooze(alarmId: Int) {
        cancelKind(alarmId, KIND_SNOOZE)
        snapshot.removeSnooze(alarmId)
        NotificationManagerCompat.from(context).cancel(Notifications.ID_SNOOZE_BASE + alarmId)
    }

    fun cancelSunriseAndSmart(alarmId: Int) {
        cancelKind(alarmId, KIND_SUNRISE)
        cancelKind(alarmId, KIND_SMART)
    }

    private fun cancelKind(alarmId: Int, kind: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(actionFor(kind))
        PendingIntent.getBroadcast(context, requestCode(alarmId, kind), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
    }

    private fun setAlarmClock(at: Long, operation: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DEST_ALARMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } catch (_: SecurityException) {
            // Exact alarms denied (Android 12): best effort until the permission is granted.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    fun setExact(at: Long, operation: PendingIntent) {
        try {
            if (canScheduleExact()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun pending(kind: Int, spec: AlarmSpec): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(actionFor(kind))
            .putExtra(EXTRA_SPEC, spec.toJson())
        return PendingIntent.getBroadcast(
            context, requestCode(spec.alarmId, kind), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_SPEC = "com.karelisio.somna.extra.SPEC"
        const val ACTION_RING = "com.karelisio.somna.action.RING"
        const val ACTION_SUNRISE = "com.karelisio.somna.action.SUNRISE"
        const val ACTION_SMART = "com.karelisio.somna.action.SMART"
        const val ACTION_SNOOZE_RING = "com.karelisio.somna.action.SNOOZE_RING"
        const val ACTION_BACKUP_RING = "com.karelisio.somna.action.BACKUP_RING"

        private const val KIND_RING = 0
        private const val KIND_SUNRISE = 1
        private const val KIND_SMART = 2
        private const val KIND_SNOOZE = 3
        private const val KIND_BACKUP = 4

        private fun actionFor(kind: Int) = when (kind) {
            KIND_RING -> ACTION_RING
            KIND_SUNRISE -> ACTION_SUNRISE
            KIND_SMART -> ACTION_SMART
            KIND_BACKUP -> ACTION_BACKUP_RING
            else -> ACTION_SNOOZE_RING
        }

        private fun requestCode(alarmId: Int, kind: Int) = 1_000 + alarmId * 8 + kind

        /**
         * Next time [hour]:[minute] strictly after [after], restricted to the repeat [days] when set.
         * Iterates on local dates so a daylight-saving gap on one day doesn't shift the following days.
         */
        fun nextTrigger(hour: Int, minute: Int, days: Int, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
            val start = after.toLocalDate()
            for (i in 0L..8L) {
                val date = start.plusDays(i)
                if (days != 0 && days and (1 shl (date.dayOfWeek.value - 1)) == 0) continue
                val candidate = date.atTime(hour, minute).atZone(after.zone)
                if (candidate.isAfter(after)) return candidate
            }
            return start.plusDays(1).atTime(hour, minute).atZone(after.zone)
        }

        fun zoned(epochMillis: Long): ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    }
}
