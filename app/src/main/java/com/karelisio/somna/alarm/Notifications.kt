package com.karelisio.somna.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object Notifications {
    const val CH_ALARM = "alarm"
    const val CH_SUNRISE = "sunrise"
    const val CH_SMART = "smart_wake"
    const val CH_SNOOZE = "snooze"
    const val CH_MISSED = "missed"
    const val CH_BEDTIME = "bedtime"
    const val CH_SOUNDS = "sleep_sounds"
    const val CH_UPDATES = "updates"

    const val ID_ALARM_SERVICE = 100
    const val ID_SNOOZE_BASE = 200
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

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
