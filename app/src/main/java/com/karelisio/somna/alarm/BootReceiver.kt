package com.karelisio.somna.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.os.UserManagerCompat
import com.karelisio.somna.container
import com.karelisio.somna.widget.WidgetTicker
import kotlinx.coroutines.launch

/**
 * Re-arms alarms after a reboot, a time / time zone change, an app update or when the exact alarm
 * permission is granted. Before the first unlock, uses the device-protected snapshot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED || !UserManagerCompat.isUserUnlocked(context)) {
                    restoreFromSnapshot(context)
                } else {
                    context.container.alarms.rescheduleAll()
                    WidgetTicker.schedule(context)
                }
            } catch (_: Exception) {
                restoreFromSnapshot(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun restoreFromSnapshot(context: Context) {
        val scheduler = context.container.scheduler
        val now = System.currentTimeMillis()
        scheduler.snapshot.specs().forEach { spec ->
            when {
                spec.triggerAt > now -> scheduler.scheduleSpec(spec)
                spec.repeating -> {
                    val next = AlarmScheduler.nextTrigger(spec.hour, spec.minute, spec.days)
                    scheduler.scheduleSpec(spec.copy(triggerAt = next.toInstant().toEpochMilli()))
                }
            }
        }
        scheduler.snapshot.snoozes().filter { it.triggerAt > now }.forEach { scheduler.rearmSnooze(it) }
    }

    companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
