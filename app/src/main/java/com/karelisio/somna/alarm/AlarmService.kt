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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = AlarmRuntime.session.value
        val spec = AlarmSpec.fromJson(intent?.getStringExtra(AlarmScheduler.EXTRA_SPEC)) ?: current?.spec
        // Always enter the foreground immediately (required after startForegroundService).
        if (current != null) refreshNotification() else goForeground(buildPlaceholder(), smart = false)
        if (spec == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_SMART -> startSmart(spec)
            ACTION_SUNRISE -> startSunrise(spec)
            ACTION_RING -> ring(spec)
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            ACTION_MISSION -> player?.setDucked(intent.getBooleanExtra(EXTRA_DUCK, false))
            else -> refreshNotification()
        }
        return START_NOT_STICKY
    }

    private fun startSmart(spec: AlarmSpec) {
        val session = AlarmRuntime.session.value
        if (session?.ringing == true) return
        AlarmRuntime.outcome.value = null
        AlarmRuntime.session.value = (session?.takeIf { it.spec.alarmId == spec.alarmId } ?: AlarmSession(spec))
            .copy(smartActive = true)
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
        // Light sleep detected inside the window: ring now rather than in the middle of a deep phase.
        if (session.spec.triggerAt - System.currentTimeMillis() > 60_000) {
            AlarmRuntime.session.value = session.copy(smartTriggered = true)
            ring(session.spec)
        }
    }

    private fun startSunrise(spec: AlarmSpec) {
        val session = AlarmRuntime.session.value
        if (session?.ringing == true) return
        AlarmRuntime.outcome.value = null
        val base = session?.takeIf { it.spec.alarmId == spec.alarmId } ?: AlarmSession(spec)
        AlarmRuntime.session.value = base.copy(sunriseStart = System.currentTimeMillis())
        acquireWakeLock(spec.sunriseMinutes + 5)
        refreshNotification()
    }

    private fun ring(spec: AlarmSpec) {
        val session = AlarmRuntime.session.value
        if (session?.ringing == true) {
            // Another alarm is already ringing: just consume this occurrence.
            if (session.spec.alarmId != spec.alarmId && spec.snoozeCount == 0) consume(spec)
            return
        }
        AlarmRuntime.outcome.value = null
        if (spec.snoozeCount == 0) consume(spec) else container.scheduler.snapshot.removeSnooze(spec.alarmId)
        monitor?.stop()
        monitor = null
        val ringSpec = if (session != null && session.spec.alarmId == spec.alarmId && spec.snoozeCount == 0) session.spec else spec
        AlarmRuntime.session.value = (session?.takeIf { it.spec.alarmId == spec.alarmId } ?: AlarmSession(ringSpec))
            .copy(spec = ringSpec, ringing = true, ringStartedAt = System.currentTimeMillis(), smartActive = false)
        acquireWakeLock(RING_TIMEOUT_MINUTES + 2)
        NotificationManagerCompat.from(this).cancel(Notifications.ID_SNOOZE_BASE + spec.alarmId)
        refreshNotification()
        player?.stop()
        player = AlarmPlayer(this, ringSpec).also { it.start(scope) }
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(RING_TIMEOUT_MINUTES * 60_000L)
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

    private fun timeOut() {
        val session = AlarmRuntime.session.value ?: return
        stopPlayback()
        val notification = NotificationCompat.Builder(this, Notifications.CH_MISSED)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle("Réveil manqué")
            .setContentText("Le réveil de ${TimeFormat.clock(session.spec.triggerAt)} a sonné pendant $RING_TIMEOUT_MINUTES min.")
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(Notifications.ID_MISSED, notification) }
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
        AlarmRuntime.session.value = null
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopIfIdle() {
        if (AlarmRuntime.session.value == null) {
            stopForegroundCompat()
            stopSelf()
        }
    }

    // region Notifications

    private fun refreshNotification() {
        val session = AlarmRuntime.session.value ?: return
        val notification = when {
            session.ringing -> buildRinging(session)
            session.sunriseStart != null -> buildSunrise(session)
            else -> buildSmart(session)
        }
        goForeground(notification, smart = session.smartActive && !session.ringing)
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
            .setSilent(true)
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
            .setSilent(true)
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
        runCatching { NotificationManagerCompat.from(this).notify(Notifications.ID_SNOOZE_BASE + spec.alarmId, notification) }
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

    private fun goForeground(notification: Notification, smart: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val media = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            val type = if (smart && Build.VERSION.SDK_INT >= 34) media or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else media
            try {
                startForeground(Notifications.ID_ALARM_SERVICE, notification, type)
            } catch (_: RuntimeException) {
                startForeground(Notifications.ID_ALARM_SERVICE, notification, media)
            }
        } else {
            startForeground(Notifications.ID_ALARM_SERVICE, notification)
        }
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
        const val EXTRA_DUCK = "duck"
        private const val RING_TIMEOUT_MINUTES = 15

        fun start(context: Context, action: String, spec: AlarmSpec? = null) {
            val intent = Intent(context, AlarmService::class.java).setAction(action)
            spec?.let { intent.putExtra(AlarmScheduler.EXTRA_SPEC, it.toJson()) }
            ContextCompat.startForegroundService(context, intent)
        }

        fun command(context: Context, action: String, extras: Intent.() -> Unit = {}) {
            val intent = Intent(context, AlarmService::class.java).setAction(action).apply(extras)
            runCatching { context.startService(intent) }
        }
    }
}
