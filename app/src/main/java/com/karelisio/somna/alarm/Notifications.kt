package com.karelisio.somna.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.karelisio.somna.R
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifications {
    const val CH_ALARM = "alarm"
    const val CH_SUNRISE = "sunrise"
    const val CH_SMART = "smart_wake"
    const val CH_SNOOZE = "snooze"
    const val CH_MISSED = "missed"
    const val CH_BEDTIME = "bedtime"
    const val CH_SOUNDS = "sleep_sounds"
    const val CH_UPDATES = "updates"

    const val ID_SMART = 100
    const val ID_SUNRISE = 101
    const val ID_RING = 102
    const val ID_FALLBACK = 103
    const val ID_INSTALL = 104
    const val ID_SNOOZE_BASE = 10_000
    const val ID_BEDTIME = 300
    const val ID_SOUNDS = 400
    const val ID_UPDATE = 500
    const val ID_MISSED = 600

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CH_ALARM, "Réveils", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Sonnerie du réveil, en plein écran"
                setSound(null, null) // Somna plays its own crescendo
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_SUNRISE, "Lever de soleil", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Écran qui s'illumine progressivement avant le réveil"
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_SMART, "Réveil intelligent", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Surveillance des mouvements pendant la fenêtre de réveil"
            },
            NotificationChannel(CH_SNOOZE, "Snooze", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_MISSED, "Réveils manqués", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_BEDTIME, "Rappel du coucher", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "« Va te coucher » à l'heure idéale"
            },
            NotificationChannel(CH_SOUNDS, "Sons d'endormissement", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_UPDATES, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannels(channels)
    }

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Posts a notification if allowed (POST_NOTIFICATIONS on Android 13+). */
    fun post(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Full-screen alarm notification used when the alarm service couldn't be started. */
    fun postFallback(context: Context, spec: AlarmSpec) {
        val open = PendingIntent.getActivity(
            context, 8,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_SPEC, spec.toJson())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(spec.label.ifBlank { "Réveil" })
            .setContentText("C'est l'heure de se lever ☀️")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        post(context, ID_FALLBACK, notification)
    }

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
