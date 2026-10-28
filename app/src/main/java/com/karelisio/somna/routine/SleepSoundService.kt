package com.karelisio.somna.routine

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.karelisio.somna.MainActivity
import com.karelisio.somna.R
import com.karelisio.somna.alarm.Notifications
import com.karelisio.somna.audio.SoundType
import com.karelisio.somna.audio.SynthPlayer
import com.karelisio.somna.core.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SleepSoundStatus(
    val type: SoundType? = null,
    /** 0 = no timer. */
    val endsAt: Long = 0L,
    val volume: Float = 0.6f,
) {
    val playing: Boolean get() = type != null
}

/** Falling-asleep sounds (rain, white noise, fire…) with a sleep timer and a slow fade-out. */
class SleepSoundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: SynthPlayer? = null
    private var job: Job? = null
    private var focus: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val type = runCatching { SoundType.valueOf(intent.getStringExtra(EXTRA_TYPE).orEmpty()) }.getOrDefault(SoundType.RAIN)
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 30)
                val volume = intent.getFloatExtra(EXTRA_VOLUME, 0.6f)
                play(type, minutes, volume)
            }
            ACTION_VOLUME -> _status.value = _status.value.copy(volume = intent.getFloatExtra(EXTRA_VOLUME, 0.6f))
            ACTION_EXTEND -> {
                val s = _status.value
                if (s.playing && s.endsAt > 0) {
                    _status.value = s.copy(endsAt = s.endsAt + 15 * 60_000L)
                    updateNotification()
                }
            }
            else -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun play(type: SoundType, minutes: Int, volume: Float) {
        val endsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        _status.value = SleepSoundStatus(type, endsAt, volume)
        updateNotification()
        requestFocus()
        if (player?.type != type) {
            player?.stop()
            player = SynthPlayer(this, type, AudioAttributes.USAGE_MEDIA).apply {
                setVolume(0f)
                start()
            }
        }
        job?.cancel()
        job = scope.launch {
            val started = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val s = _status.value
                val fadeIn = ((now - started) / FADE_IN_MS.toFloat()).coerceIn(0f, 1f)
                val fadeOut = if (s.endsAt > 0) ((s.endsAt - now) / FADE_OUT_MS.toFloat()).coerceIn(0f, 1f) else 1f
                player?.setVolume(s.volume * fadeIn * fadeOut * fadeOut)
                level = player?.level ?: 0f
                if (s.endsAt in 1..now) {
                    stopAll()
                    break
                }
                delay(50)
            }
        }
    }

    private fun requestFocus() {
        if (focus != null) return
        val audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { if (it == AudioManager.AUDIOFOCUS_LOSS) stopAll() }
            .build()
            .also { runCatching { audio.requestAudioFocus(it) } }
    }

    private fun updateNotification() {
        val s = _status.value
        val type = s.type ?: return
        val open = PendingIntent.getActivity(
            this, 40,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DEST_SOUNDS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, Notifications.CH_SOUNDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(type.label)
            .setContentText(if (s.endsAt > 0) "S'arrête en douceur à ${TimeFormat.clock(s.endsAt)}" else "Sans minuterie")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Arrêter", serviceIntent(this, ACTION_STOP))
        if (s.endsAt > 0) {
            builder.setWhen(s.endsAt).setUsesChronometer(true).setChronometerCountDown(true)
            builder.addAction(0, "+15 min", serviceIntent(this, ACTION_EXTEND))
        }
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifications.ID_SOUNDS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notifications.ID_SOUNDS, notification)
        }
    }

    private fun stopAll() {
        job?.cancel()
        player?.stop()
        player = null
        level = 0f
        focus?.let { runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) } }
        focus = null
        _status.value = SleepSoundStatus(volume = _status.value.volume)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        player?.stop()
        player = null
        _status.value = SleepSoundStatus(volume = _status.value.volume)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PLAY = "com.karelisio.somna.sounds.PLAY"
        private const val ACTION_STOP = "com.karelisio.somna.sounds.STOP"
        private const val ACTION_EXTEND = "com.karelisio.somna.sounds.EXTEND"
        private const val ACTION_VOLUME = "com.karelisio.somna.sounds.VOLUME"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_VOLUME = "volume"
        private const val FADE_IN_MS = 4_000L
        private const val FADE_OUT_MS = 90_000L

        private val _status = MutableStateFlow(SleepSoundStatus())
        val status: StateFlow<SleepSoundStatus> = _status

        /** Loudness of the sound being played (0..1), for the visualizer. */
        @Volatile var level: Float = 0f
            private set

        fun play(context: Context, type: SoundType, minutes: Int, volume: Float) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SleepSoundService::class.java).setAction(ACTION_PLAY)
                    .putExtra(EXTRA_TYPE, type.name).putExtra(EXTRA_MINUTES, minutes).putExtra(EXTRA_VOLUME, volume),
            )
        }

        fun setVolume(context: Context, volume: Float) {
            if (!_status.value.playing) return
            context.startService(Intent(context, SleepSoundService::class.java).setAction(ACTION_VOLUME).putExtra(EXTRA_VOLUME, volume))
        }

        fun stop(context: Context) {
            if (!_status.value.playing) return
            context.startService(Intent(context, SleepSoundService::class.java).setAction(ACTION_STOP))
        }

        private fun serviceIntent(context: Context, action: String) = PendingIntent.getService(
            context, action.hashCode(),
            Intent(context, SleepSoundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
