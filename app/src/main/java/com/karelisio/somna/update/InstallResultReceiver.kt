package com.karelisio.somna.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                UpdateInstaller.publish(InstallState.WaitingForUser)
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstaller.publish(InstallState.Success)
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
