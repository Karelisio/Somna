package com.karelisio.somna.alarm

import android.content.Context
import androidx.core.os.UserManagerCompat
import com.karelisio.somna.data.AlarmDao
import com.karelisio.somna.data.AlarmEntity
import com.karelisio.somna.data.SettingsRepository
import com.karelisio.somna.routine.BedtimeScheduler
import com.karelisio.somna.widget.SomnaWidget
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val context: Context,
    private val dao: AlarmDao,
    private val settingsRepo: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val bedtime: BedtimeScheduler,
) {
    val alarms: Flow<List<AlarmEntity>> = dao.observeAll()

    fun observe(id: Int): Flow<AlarmEntity?> = dao.observe(id)

    suspend fun get(id: Int): AlarmEntity? = dao.get(id)

    /** Creates (or re-enables an identical one-shot) alarm using the default alarm settings. */
    suspend fun create(
        hour: Int,
        minute: Int,
        days: Int = 0,
        label: String = "",
        sunrise: Boolean? = null,
        smartWakeMinutes: Int? = null,
    ): AlarmEntity {
        val s = settingsRepo.current()
        val existing = if (days == 0) dao.findOneShot(hour, minute) else null
        val alarm = if (existing != null) {
            existing.copy(enabled = true).also { dao.update(it) }
        } else {
            val fresh = AlarmEntity(
                hour = hour, minute = minute, days = days, label = label,
                sound = s.defaultSound, vibrate = s.vibration, sunrise = sunrise ?: s.sunriseEnabled,
                smartWakeMinutes = smartWakeMinutes ?: s.smartWakeMinutes, mission = s.mission.name,
            )
            fresh.copy(id = dao.insert(fresh).toInt())
        }
        scheduler.schedule(alarm, s)
        onAlarmsChanged()
        return alarm
    }

    suspend fun save(alarm: AlarmEntity) {
        val before = dao.get(alarm.id)
        if (before != null && (before.hour != alarm.hour || before.minute != alarm.minute || before.days != alarm.days || !alarm.enabled)) {
            AlarmService.abortPending(context, alarm.id)
        }
        dao.update(alarm)
        scheduler.schedule(alarm, settingsRepo.current())
        onAlarmsChanged()
    }

    suspend fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        if (!enabled) scheduler.cancelSnooze(alarm.id)
        save(alarm.copy(enabled = enabled))
    }

    suspend fun delete(alarm: AlarmEntity) {
        AlarmService.abortPending(context, alarm.id)
        dao.delete(alarm)
        scheduler.cancel(alarm.id)
        scheduler.cancelSnooze(alarm.id)
        scheduler.snapshot.remove(alarm.id)
        onAlarmsChanged()
    }

    /** Undo for [delete]. */
    suspend fun restore(alarm: AlarmEntity) {
        dao.insert(alarm)
        scheduler.schedule(alarm, settingsRepo.current())
        onAlarmsChanged()
    }

    suspend fun rescheduleAll() {
        val s = settingsRepo.current()
        scheduler.snapshot.consumeFiredOneShots().forEach { id ->
            dao.get(id)?.takeIf { !it.repeating }?.let { dao.update(it.copy(enabled = false)) }
        }
        dao.all().forEach { scheduler.schedule(it, s) }
        onAlarmsChanged()
    }

    /** Next enabled alarm and when it rings. */
    suspend fun next(now: ZonedDateTime = ZonedDateTime.now()): Pair<AlarmEntity, ZonedDateTime>? =
        dao.all().filter { it.enabled }
            .map { it to scheduler.effectiveNext(it, now) }
            .minByOrNull { it.second }

    /**
     * Nap: a one-shot alarm after falling asleep + [minutes] (20–30 min power nap, or one cycle).
     * No sunrise nor smart window, they don't make sense for a short nap.
     */
    suspend fun createNap(minutes: Int): AlarmEntity {
        val s = settingsRepo.current()
        val at = LocalDateTime.now().plusMinutes((s.fallAsleepMinutes + minutes).toLong() + 1).withSecond(0).withNano(0)
        return create(at.hour, at.minute, label = "Sieste", sunrise = false, smartWakeMinutes = 0)
    }

    /** "Passer le prochain": the upcoming occurrence of a repeating alarm won't ring. */
    suspend fun skipNext(alarm: AlarmEntity) {
        if (!alarm.repeating) return
        AlarmService.abortPending(context, alarm.id)
        val upcoming = AlarmScheduler.nextTrigger(alarm.hour, alarm.minute, alarm.days)
        scheduler.snapshot.markConsumed(alarm.id, upcoming.toInstant().toEpochMilli())
        scheduler.schedule(alarm, settingsRepo.current())
        onAlarmsChanged()
    }

    suspend fun unskip(alarm: AlarmEntity) {
        scheduler.snapshot.markConsumed(alarm.id, 0L)
        scheduler.schedule(alarm, settingsRepo.current())
        onAlarmsChanged()
    }

    /**
     * An occurrence has been consumed: it rang (possibly early, through smart wake) or was dismissed
     * during the sunrise. Re-arms repeating alarms for their next day, disables one-shots.
     */
    suspend fun onOccurrenceConsumed(spec: AlarmSpec) {
        if (spec.test) return
        scheduler.cancel(spec.alarmId)
        scheduler.snapshot.markConsumed(spec.alarmId, spec.triggerAt)
        val unlocked = UserManagerCompat.isUserUnlocked(context)
        val after = AlarmScheduler.zoned(spec.triggerAt)
        if (spec.repeating) {
            if (unlocked) {
                // Deleted meanwhile: never resurrect it from the spec.
                val alarm = runCatching { dao.get(spec.alarmId) }.getOrNull()
                if (alarm != null) scheduler.schedule(alarm, settingsRepo.current(), after)
                else scheduler.snapshot.remove(spec.alarmId)
            } else {
                val next = AlarmScheduler.nextTrigger(spec.hour, spec.minute, spec.days, after)
                scheduler.scheduleSpec(spec.copy(triggerAt = next.toInstant().toEpochMilli(), snoozeCount = 0, early = false, backup = false))
            }
        } else {
            scheduler.snapshot.remove(spec.alarmId)
            if (unlocked) {
                runCatching { dao.get(spec.alarmId)?.let { dao.update(it.copy(enabled = false)) } }
            } else {
                scheduler.snapshot.markOneShotFired(spec.alarmId)
            }
        }
        if (unlocked) runCatching { onAlarmsChanged() }
    }

    private suspend fun onAlarmsChanged() {
        bedtime.recompute()
        SomnaWidget.refresh(context)
    }
}
