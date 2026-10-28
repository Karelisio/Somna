package com.karelisio.somna.routine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karelisio.somna.MainActivity
import com.karelisio.somna.R
import com.karelisio.somna.alarm.Notifications
import com.karelisio.somna.container
import com.karelisio.somna.core.TimeFormat
import kotlinx.coroutines.launch

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wakeAt = intent.getLongExtra(EXTRA_WAKE, 0L)
        val container = context.container
        when (intent.action) {
            ACTION_BEDTIME -> {
                val pending = goAsync()
                container.appScope.launch {
                    try {
                        val s = container.settings.current()
                        if (s.bedtimeReminder) notify(context, wakeAt, s.preferredCycles)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_GOING -> {
                NotificationManagerCompat.from(context).cancel(Notifications.ID_BEDTIME)
                val pending = goAsync()
                container.appScope.launch {
                    try {
                        container.sleep.markBedtime()
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_LATER -> {
                NotificationManagerCompat.from(context).cancel(Notifications.ID_BEDTIME)
                container.bedtime.remindLater(15, wakeAt)
            }
        }
    }

    private fun notify(context: Context, wakeAt: Long, cycles: Int) {
        val open = PendingIntent.getActivity(
            context, 30,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DEST_SOUNDS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(action: String, code: Int) = PendingIntent.getBroadcast(
            context, code,
            Intent(context, BedtimeReceiver::class.java).setAction(action).putExtra(EXTRA_WAKE, wakeAt),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (wakeAt > 0) {
            "Couche-toi maintenant pour $cycles cycles complets avant ton réveil de ${TimeFormat.clock(wakeAt)}."
        } else {
            "Couche-toi maintenant pour te réveiller entre deux cycles."
        }
        val notification = NotificationCompat.Builder(context, Notifications.CH_BEDTIME)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Va te coucher 🌙")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "J'y vais", action(ACTION_GOING, 31))
            .addAction(0, "Dans 15 min", action(ACTION_LATER, 32))
            .build()
        Notifications.post(context, Notifications.ID_BEDTIME, notification)
    }

    companion object {
        const val ACTION_BEDTIME = "com.karelisio.somna.action.BEDTIME"
        const val ACTION_GOING = "com.karelisio.somna.action.BEDTIME_GOING"
        const val ACTION_LATER = "com.karelisio.somna.action.BEDTIME_LATER"
        const val EXTRA_WAKE = "wake_at"
    }
}
