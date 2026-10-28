package com.karelisio.somna.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import com.karelisio.somna.R
import com.karelisio.somna.alarm.Notifications

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                UpdateInstaller.pendingConfirmation = confirm
                UpdateInstaller.publish(InstallState.WaitingForUser)
                if (confirm != null) {
                    // May be blocked if Somna isn't visible: the notification (and the sheet) can reopen it.
                    runCatching { context.startActivity(confirm) }
                    val open = PendingIntent.getActivity(context, 60, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    Notifications.post(
                        context, Notifications.ID_INSTALL,
                        NotificationCompat.Builder(context, Notifications.CH_UPDATES)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("Mise à jour prête")
                            .setContentText("Touchez pour confirmer l'installation.")
                            .setContentIntent(open)
                            .setAutoCancel(true)
                            .build(),
                    )
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                NotificationManagerCompat.from(context).cancel(Notifications.ID_INSTALL)
                UpdateInstaller.publish(InstallState.Success)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateInstaller.publish(InstallState.Failed("Installation annulée."))
            else -> {
                val message = when (status) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "Conflit de signature : l'APK doit être signé avec le même keystore que l'appli installée."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Espace de stockage insuffisant."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation bloquée par le système."
                    else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Échec de l'installation."
                }
                UpdateInstaller.publish(InstallState.Failed(message))
            }
        }
    }

    companion object {
        const val ACTION = "com.karelisio.somna.action.INSTALL_RESULT"
    }
}
