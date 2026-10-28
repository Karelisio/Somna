package com.karelisio.somna.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.UserManagerCompat
import com.karelisio.somna.container
import kotlinx.coroutines.launch

/** Entry point of every AlarmManager alarm; hands over to [AlarmService] (exact alarms may start FGS). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val spec = AlarmSpec.fromJson(intent.getStringExtra(AlarmScheduler.EXTRA_SPEC)) ?: return
        when (intent.action) {
            AlarmScheduler.ACTION_RING, AlarmScheduler.ACTION_SNOOZE_RING ->
                AlarmService.start(context, AlarmService.ACTION_RING, spec)
            AlarmScheduler.ACTION_SUNRISE -> AlarmService.start(context, AlarmService.ACTION_SUNRISE, spec)
            AlarmScheduler.ACTION_SMART -> AlarmService.start(context, AlarmService.ACTION_SMART, spec)
            ACTION_CANCEL_SNOOZE -> {
                context.container.scheduler.cancelSnooze(spec.alarmId)
                NotificationManagerCompat.from(context).cancel(Notifications.ID_SNOOZE_BASE + spec.alarmId)
                if (!spec.test && UserManagerCompat.isUserUnlocked(context)) {
                    val pending = goAsync()
                    context.container.appScope.launch {
                        try {
                            runCatching { context.container.sleep.recordWake(spec.triggerAt) }
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_SNOOZE = "com.karelisio.somna.action.CANCEL_SNOOZE"
    }
}
