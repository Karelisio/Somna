package com.karelisio.somna.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
        val trigger = nextTrigger(alarm.hour, alarm.minute, alarm.days, after)
        scheduleSpec(buildSpec(alarm, settings, trigger.toInstant().toEpochMilli()))
    }

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
        val snoozed = spec.copy(triggerAt = at, sunriseMinutes = 0, smartMinutes = 0, snoozeCount = spec.snoozeCount + 1)
        setAlarmClock(at, pending(KIND_SNOOZE, snoozed))
        if (!spec.test) snapshot.putSnooze(snoozed)
        return snoozed
    }

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

        private const val KIND_RING = 0
        private const val KIND_SUNRISE = 1
        private const val KIND_SMART = 2
        private const val KIND_SNOOZE = 3

        private fun actionFor(kind: Int) = when (kind) {
            KIND_RING -> ACTION_RING
            KIND_SUNRISE -> ACTION_SUNRISE
            KIND_SMART -> ACTION_SMART
            else -> ACTION_SNOOZE_RING
        }

        private fun requestCode(alarmId: Int, kind: Int) = 1_000 + alarmId * 4 + kind

        /** Next time [hour]:[minute] strictly after [after], restricted to the repeat [days] when set. */
        fun nextTrigger(hour: Int, minute: Int, days: Int, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
            var candidate = after.toLocalDate().atTime(hour, minute).atZone(after.zone)
            if (!candidate.isAfter(after)) candidate = candidate.plusDays(1)
            if (days == 0) return candidate
            repeat(7) {
                if (days and (1 shl (candidate.dayOfWeek.value - 1)) != 0) return candidate
                candidate = candidate.plusDays(1)
            }
            return candidate
        }

        fun zoned(epochMillis: Long): ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    }
}
