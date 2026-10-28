package com.karelisio.somna.routine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.os.UserManagerCompat
import com.karelisio.somna.alarm.AlarmScheduler
import com.karelisio.somna.data.AlarmDao
import com.karelisio.somna.data.SettingsRepository
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Schedules the "va te coucher" reminder at the ideal bedtime before the next alarm. */
class BedtimeScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
    private val alarmDao: AlarmDao,
    private val scheduler: AlarmScheduler,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val _next = MutableStateFlow<Long?>(null)

    /** Next scheduled reminder (epoch ms), for display. */
    val next: StateFlow<Long?> = _next

    suspend fun recompute() {
        if (!UserManagerCompat.isUserUnlocked(context)) return
        val operation = pending(BedtimeReceiver.ACTION_BEDTIME)
        alarmManager.cancel(operation)
        _next.value = null
        val s = settings.current()
        if (!s.bedtimeReminder) return
        val now = ZonedDateTime.now()
        val wake = alarmDao.all().filter { it.enabled }
            .map { scheduler.effectiveNext(it, now) }
            .minOrNull() ?: return
        if (Duration.between(now, wake).toHours() > 36) return
        val wakeMs = wake.toInstant().toEpochMilli()
        val computed = wakeMs - (s.preferredCycles * s.cycleMinutes + s.fallAsleepMinutes) * 60_000L
        val bedtime = s.bedtimeOverride.takeIf { it > System.currentTimeMillis() && it < wakeMs } ?: computed
        if (bedtime <= System.currentTimeMillis() + 60_000) return
        // Same PendingIntent (extras are updated) carrying the wake time for the notification text.
        scheduler.setExact(bedtime, pending(BedtimeReceiver.ACTION_BEDTIME, wakeMs))
        _next.value = bedtime
    }

    /** "Dans 15 min": its own PendingIntent, so a later [recompute] doesn't cancel it. */
    fun remindLater(minutes: Int, wakeAt: Long) {
        scheduler.setExact(System.currentTimeMillis() + minutes * 60_000L, pending(BedtimeReceiver.ACTION_BEDTIME, wakeAt, requestCode = 78))
    }

    private fun pending(action: String, wakeAt: Long = 0L, requestCode: Int = 77): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, BedtimeReceiver::class.java).setAction(action).putExtra(BedtimeReceiver.EXTRA_WAKE, wakeAt),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
