package com.karelisio.somna.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.UserManagerCompat
import com.karelisio.somna.R
import com.karelisio.somna.container
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.data.Mission
import com.karelisio.somna.routine.SleepSoundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service driving an alarm occurrence: smart-wake window (accelerometer), sunrise
 * (progressive light, full-screen) and ringing (crescendo sound + vibration), then snooze/dismiss.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: AlarmPlayer? = null
    private var monitor: SmartWakeMonitor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutJob: Job? = null
    private var foregroundId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = AlarmRuntime.session.value
        val spec = AlarmSpec.fromJson(intent?.getStringExtra(AlarmScheduler.EXTRA_SPEC)) ?: current?.spec
        if (spec == null) {
            ensureForeground()
            stopIfIdle()
            return START_NOT_STICKY
        }
        // Each branch enters the foreground synchronously (required after startForegroundService):
        // startForeground is only ever called here, while the start exemption applies.
        when (intent?.action) {
            ACTION_SMART -> startSmart(spec)
            ACTION_SUNRISE -> startSunrise(spec)
            ACTION_RING -> ring(spec)
            ACTION_SNOOZE -> {
                ensureForeground()
                snooze()
            }
            ACTION_DISMISS -> {
                ensureForeground()
                dismiss()
            }
            ACTION_ABORT -> {
                ensureForeground()
                abort(intent.getIntExtra(EXTRA_ALARM_ID, -1))
            }
            ACTION_MISSION -> {
                ensureForeground()
                player?.setDucked(intent.getBooleanExtra(EXTRA_DUCK, false))
            }
            else -> ensureForeground()
        }
        return START_NOT_STICKY
    }

    private fun startSmart(spec: AlarmSpec) {
        val session = AlarmRuntime.session.value
        if (session?.ringing == true || (session != null && session.spec.alarmId != spec.alarmId)) return ensureForeground()
        AlarmRuntime.outcome.value = null
        AlarmRuntime.session.value = (session ?: AlarmSession(spec)).copy(smartActive = true)
        acquireWakeLock(spec.smartMinutes + 5)
        monitor?.stop()
        monitor = SmartWakeMonitor(this, spec.smartSensitivity) { onLightSleep() }.also { m ->
            if (!m.start()) AlarmRuntime.session.value = AlarmRuntime.session.value?.copy(smartActive = false)
        }
        refreshNotification()
    }

    private fun onLightSleep() {
        val session = AlarmRuntime.session.value ?: return
        if (session.ringing) return
        // Light sleep inside the window: ring now rather than in a deep phase. Going through an
        // alarm clock restarts the service with the exact-alarm exemption (no background FGS call).
        if (session.spec.triggerAt - System.currentTimeMillis() > 60_000) {
            monitor?.stop()
            monitor = null
            container.scheduler.ringNow(session.spec)
        }
    }

    private fun startSunrise(spec: AlarmSpec) {
        val session = AlarmRuntime.session.value
        if (session?.ringing == true || (session != null && session.spec.alarmId != spec.alarmId)) return ensureForeground()
        AlarmRuntime.outcome.value = null
        AlarmRuntime.session.value = (session ?: AlarmSession(spec)).copy(sunriseStart = System.currentTimeMillis())
        acquireWakeLock(spec.sunriseMinutes + 5)
        refreshNotification()
    }

    private fun ring(spec: AlarmSpec) {
        val session = AlarmRuntime.session.value
        if (session?.ringing == true) {
            // Same alarm (backup re-ring while still alive): nothing to do. Another alarm: deliver it
            // once this one is handled rather than losing it.
            if (session.spec.alarmId != spec.alarmId) container.scheduler.requeue(spec)
            return ensureForeground()
        }
        if (session != null && session.spec.alarmId != spec.alarmId && !spec.test) {
            // Another alarm's sunrise / smart window is running: that one goes first.
            container.scheduler.requeue(spec)
            return ensureForeground()
        }
        AlarmRuntime.outcome.value = null
        monitor?.stop()
        monitor = null
        AlarmRuntime.session.value = (session ?: AlarmSession(spec)).copy(
            spec = spec,
            ringing = true,
            ringStartedAt = System.currentTimeMillis(),
            smartActive = false,
            smartTriggered = spec.early || session?.smartTriggered == true,
        )
        acquireWakeLock(RING_TIMEOUT_MINUTES + 2)
        refreshNotification()
        // Secure the ring first (re-ring if the process dies), then consume the occurrence.
        container.scheduler.armBackup(spec)
        if (spec.snoozeCount == 0 && !spec.backup) consume(spec)
        container.scheduler.cancelSnooze(spec.alarmId)
        SleepSoundService.stop(this) // no rain over the alarm
        player?.stop()
        player = AlarmPlayer(this, spec).also { it.start(scope) }
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            val started = System.currentTimeMillis()
            while (System.currentTimeMillis() - started < RING_TIMEOUT_MINUTES * 60_000L) {
                delay(60_000)
                container.scheduler.armBackup(spec)
            }
            timeOut()
        }
    }

    private fun consume(spec: AlarmSpec) {
        container.appScope.launch { runCatching { container.alarms.onOccurrenceConsumed(spec) } }
    }

    private fun snooze() {
        val session = AlarmRuntime.session.value ?: return stopIfIdle()
        if (!session.ringing) return
        stopPlayback()
        val snoozed = container.scheduler.scheduleSnooze(session.spec)
        postSnoozeNotification(snoozed)
        AlarmRuntime.outcome.value = AlarmOutcome.Snoozed(snoozed, snoozed.triggerAt)
        finishSession()
    }

    private fun dismiss() {
        val session = AlarmRuntime.session.value ?: return stopIfIdle()
        stopPlayback()
        // Dismissed before ringing (during the sunrise or the smart window): skip this occurrence.
        if (!session.ringing) consume(session.spec)
        recordWake(session.spec)
        AlarmRuntime.outcome.value = AlarmOutcome.Dismissed(session.spec, System.currentTimeMillis())
        finishSession()
    }

    /** The alarm was edited, disabled or deleted during its sunrise / smart window: drop the session. */
    private fun abort(alarmId: Int) {
        val session = AlarmRuntime.session.value ?: return stopIfIdle()
        if (session.ringing || session.spec.alarmId != alarmId) return
        stopPlayback()
        AlarmRuntime.outcome.value = null
        finishSession()
    }

    private fun timeOut() {
        val session = AlarmRuntime.session.value ?: return
        stopPlayback()
        val notification = NotificationCompat.Builder(this, Notifications.CH_MISSED)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle("Réveil manqué")
            .setContentText("Le réveil de ${TimeFormat.clock(session.spec.triggerAt)} a sonné pendant $RING_TIMEOUT_MINUTES min.")
            .setAutoCancel(true)
            .build()
        Notifications.post(this, Notifications.ID_MISSED, notification)
        AlarmRuntime.outcome.value = AlarmOutcome.TimedOut(session.spec)
        finishSession()
    }

    private fun recordWake(spec: AlarmSpec) {
        if (spec.test || !UserManagerCompat.isUserUnlocked(this)) return
        container.appScope.launch {
            withContext(NonCancellable) { runCatching { container.sleep.recordWake(spec.triggerAt) } }
        }
    }

    private fun stopPlayback() {
        timeoutJob?.cancel()
        player?.stop()
        player = null
        monitor?.stop()
        monitor = null
    }

    private fun finishSession() {
        AlarmRuntime.session.value?.let { container.scheduler.cancelBackup(it.spec.alarmId) }
        AlarmRuntime.session.value = null
        releaseWakeLock()
        stopForegroundCompat()
        NotificationManagerCompat.from(this).apply {
            cancel(Notifications.ID_SMART)
            cancel(Notifications.ID_SUNRISE)
            cancel(Notifications.ID_RING)
        }
        foregroundId = 0
        stopSelf()
    }

    private fun stopIfIdle() {
        if (AlarmRuntime.session.value == null) {
            stopForegroundCompat()
            stopSelf()
        }
    }

    // region Notifications

    /** Foreground with the notification of the current phase (each phase has its own id, so the
     * full-screen intent fires when the sunrise or the ringing starts). */
    private fun refreshNotification() {
        val session = AlarmRuntime.session.value ?: return
        when {
            session.ringing -> goForeground(Notifications.ID_RING, buildRinging(session), smart = false)
            session.sunriseStart != null -> goForeground(Notifications.ID_SUNRISE, buildSunrise(session), smart = session.smartActive)
            else -> goForeground(Notifications.ID_SMART, buildSmart(session), smart = session.smartActive)
        }
    }

    private fun ensureForeground() {
        if (AlarmRuntime.session.value != null) refreshNotification()
        else goForeground(Notifications.ID_SMART, buildPlaceholder(), smart = false)
    }

    private fun buildPlaceholder(): Notification =
        NotificationCompat.Builder(this, Notifications.CH_SMART)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle("Somna")
            .setContentText("Réveil en préparation…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

    private fun buildSmart(session: AlarmSession): Notification {
        val start = session.spec.triggerAt - session.spec.smartMinutes * 60_000L
        return NotificationCompat.Builder(this, Notifications.CH_SMART)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Réveil intelligent actif")
            .setContentText("Je guette ta phase de sommeil léger entre ${TimeFormat.clock(start)} et ${TimeFormat.clock(session.spec.triggerAt)}.")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(activityIntent())
            .addAction(0, "Je suis réveillé", serviceIntent(ACTION_DISMISS))
            .build()
    }

    private fun buildSunrise(session: AlarmSession): Notification =
        NotificationCompat.Builder(this, Notifications.CH_SUNRISE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lever de soleil")
            .setContentText("Réveil en douceur à ${TimeFormat.clock(session.spec.triggerAt)}")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(activityIntent(), true)
            .setContentIntent(activityIntent())
            .addAction(0, "Je suis réveillé", serviceIntent(ACTION_DISMISS))
            .build()

    private fun buildRinging(session: AlarmSession): Notification {
        val spec = session.spec
        val title = spec.label.ifBlank { "Réveil" }
        val builder = NotificationCompat.Builder(this, Notifications.CH_ALARM)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle("$title · ${TimeFormat.clock(System.currentTimeMillis())}")
            .setContentText(if (session.smartTriggered) "Tu es en sommeil léger : c'est le bon moment ☀️" else "Bonjour ☀️")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(activityIntent(), true)
            .setContentIntent(activityIntent())
            .addAction(0, "Snooze ${spec.snoozeMinutes} min", serviceIntent(ACTION_SNOOZE))
        val mission = runCatching { Mission.valueOf(spec.mission) }.getOrDefault(Mission.NONE)
        if (mission == Mission.NONE) builder.addAction(0, "Arrêter", serviceIntent(ACTION_DISMISS))
        else builder.addAction(0, "Arrêter (mission)", activityIntent())
        return builder.build()
    }

    private fun postSnoozeNotification(spec: AlarmSpec) {
        val cancel = PendingIntent.getBroadcast(
            this, spec.alarmId,
            Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_CANCEL_SNOOZE)
                .putExtra(AlarmScheduler.EXTRA_SPEC, spec.toJson()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, Notifications.CH_SNOOZE)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle("Snooze")
            .setContentText("Le réveil sonnera de nouveau à ${TimeFormat.clock(spec.triggerAt)}")
            .setOngoing(true)
            .setSilent(true)
            .setWhen(spec.triggerAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .addAction(0, "Arrêter le réveil", cancel)
            .build()
        Notifications.post(this, Notifications.ID_SNOOZE_BASE + spec.alarmId, notification)
    }

    private fun activityIntent(): PendingIntent = PendingIntent.getActivity(
        this, 7,
        Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getForegroundService(
        this, action.hashCode(),
        Intent(this, AlarmService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun goForeground(id: Int, notification: Notification, smart: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val media = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            val type = if (smart && Build.VERSION.SDK_INT >= 34) media or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else media
            try {
                startForeground(id, notification, type)
            } catch (e: IllegalArgumentException) {
                // Health type refused on this device: media playback alone is enough.
                startForeground(id, notification, media)
            }
        } else {
            startForeground(id, notification)
        }
        if (foregroundId != 0 && foregroundId != id) NotificationManagerCompat.from(this).cancel(foregroundId)
        foregroundId = id
    }

    private fun stopForegroundCompat() = stopForeground(STOP_FOREGROUND_REMOVE)

    // endregion

    private fun acquireWakeLock(minutes: Int) {
        releaseWakeLock()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Somna:alarm")
            .apply { acquire(minutes * 60_000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        stopPlayback()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SMART = "com.karelisio.somna.service.SMART"
        const val ACTION_SUNRISE = "com.karelisio.somna.service.SUNRISE"
        const val ACTION_RING = "com.karelisio.somna.service.RING"
        const val ACTION_SNOOZE = "com.karelisio.somna.service.SNOOZE"
        const val ACTION_DISMISS = "com.karelisio.somna.service.DISMISS"
        const val ACTION_MISSION = "com.karelisio.somna.service.MISSION"
        const val ACTION_ABORT = "com.karelisio.somna.service.ABORT"
        const val EXTRA_DUCK = "duck"
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val RING_TIMEOUT_MINUTES = 15

        fun start(context: Context, action: String, spec: AlarmSpec? = null) {
            val intent = Intent(context, AlarmService::class.java).setAction(action)
            spec?.let { intent.putExtra(AlarmScheduler.EXTRA_SPEC, it.toJson()) }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Drops a sunrise / smart-window session of [alarmId] that hasn't started ringing. */
        fun abortPending(context: Context, alarmId: Int) {
            val session = AlarmRuntime.session.value ?: return
            if (session.ringing || session.spec.alarmId != alarmId) return
            command(context, ACTION_ABORT) { putExtra(EXTRA_ALARM_ID, alarmId) }
        }

        fun command(context: Context, action: String, extras: Intent.() -> Unit = {}) {
            val intent = Intent(context, AlarmService::class.java).setAction(action).apply(extras)
            runCatching { context.startService(intent) }
        }
    }
}
