package com.karelisio.somna.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import com.karelisio.somna.audio.SoundType
import com.karelisio.somna.audio.SynthPlayer
import com.karelisio.somna.data.SYSTEM_SOUND
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays the alarm sound for a few seconds at the real alarm volume (ALARM stream, full crescendo),
 * so the chosen volume isn't a surprise in the morning.
 */
object AlarmPreview {
    private var synth: SynthPlayer? = null
    private var media: MediaPlayer? = null
    private var job: Job? = null
    private var previousVolume = -1

    fun play(context: Context, sound: String, volume: Float, scope: CoroutineScope) {
        if (AlarmRuntime.session.value?.ringing == true) return
        stop(context)
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, (volume * max).roundToInt().coerceIn(1, max), 0)
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (sound == SYSTEM_SOUND) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            media = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(app, uri)
                    setVolume(0f, 0f)
                    prepare()
                    start()
                }
            }.getOrNull()
        } else {
            val type = runCatching { SoundType.valueOf(sound) }.getOrDefault(SoundType.BIRDS)
            synth = SynthPlayer(app, type, AudioAttributes.USAGE_ALARM).apply {
                setVolume(0f)
                start()
            }
        }
        job = scope.launch {
            // 0.3 s fade-in, ~2.5 s at full level, 0.4 s fade-out.
            val steps = 64
            for (i in 0..steps) {
                val t = i / steps.toFloat() * 3.2f
                val level = minOf(1f, t / 0.3f, (3.2f - t) / 0.4f).coerceIn(0f, 1f)
                synth?.setVolume(level)
                media?.setVolume(level, level)
                delay(50)
            }
            stop(app)
        }
    }

    fun stop(context: Context) {
        job?.cancel()
        job = null
        val hadSound = synth != null || media != null
        synth?.stop()
        synth = null
        media?.runCatching { stop(); release() }
        media = null
        val restore = previousVolume
        previousVolume = -1
        if (hadSound && restore >= 0) {
            val audio = context.applicationContext.getSystemService(AudioManager::class.java)
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, restore, 0) }
            }, 400)
        }
    }
}
