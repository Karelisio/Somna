package com.karelisio.somna.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.somna.MainActivity
import com.karelisio.somna.R
import com.karelisio.somna.alarm.Notifications
import com.karelisio.somna.container
import java.util.concurrent.TimeUnit

/** Background check of GitHub Releases, at most every 6 h. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val settings = container.settings.current()
        if (!settings.autoUpdateCheck) return Result.success()
        val result = container.updates.check(force = false)
        if (result is CheckResult.Available && result.info.version != settings.skippedVersion) notify(result.info)
        return Result.success()
    }

    private fun notify(info: ReleaseInfo) {
        val prefs = applicationContext.getSharedPreferences("update_worker", Context.MODE_PRIVATE)
        if (prefs.getString("notified", null) == info.version) return
        val open = PendingIntent.getActivity(
            applicationContext, 50,
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DEST_UPDATE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Somna ${info.version} est disponible")
            .setContentText("Touchez pour voir les nouveautés et mettre à jour.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(Notifications.ID_UPDATE, notification)
            prefs.edit { putString("notified", info.version) }
        }
    }

    companion object {
        private const val NAME = "somna-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
