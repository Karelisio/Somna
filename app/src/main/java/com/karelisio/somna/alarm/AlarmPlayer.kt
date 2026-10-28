package com.karelisio.somna.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays the alarm: soft built-in sound (or the phone's ringtone) in crescendo on the ALARM stream,
 * plus progressive vibration. USAGE_ALARM keeps it audible in silent / vibrate mode.
 */
class AlarmPlayer(private val context: Context, private val spec: AlarmSpec) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val vibrator = ProgressiveVibrator(context)
    private var synth: SynthPlayer? = null
    private var media: MediaPlayer? = null
    private var rampJob: Job? = null
    private var focus: AudioFocusRequest? = null
    private var previousStreamVolume = -1

    @Volatile private var duck = false

    fun start(scope: CoroutineScope) {
        applyStreamVolume()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
            .also { runCatching { audio.requestAudioFocus(it) } }
        startSound()
        if (spec.vibrate) vibrator.start(scope, spec.crescendoSeconds)
        val started = System.currentTimeMillis()
        rampJob = scope.launch {
            while (isActive) {
                val t = ((System.currentTimeMillis() - started) / 1000f / spec.crescendoSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
                // Perceptual ease-in: quiet for a while, then rises.
                val volume = (0.04f + 0.96f * t * t) * (if (duck) 0.3f else 1f)
                synth?.setVolume(volume)
                media?.setVolume(volume, volume)
                AlarmRuntime.level = synth?.level ?: (volume * 0.6f)
                delay(100)
            }
        }
    }

    /** Lower the sound while the user solves the wake-up mission. */
    fun setDucked(ducked: Boolean) {
        duck = ducked
    }

    fun stop() {
        rampJob?.cancel()
        vibrator.stop()
        synth?.stop()
        synth = null
        media?.runCatching { stop(); release() }
        media = null
        AlarmRuntime.level = 0f
        // Restoring a louder system alarm volume while the last buffer still plays made a loud
        // spike: wait until the track is silent and released.
        val focusRequest = focus
        focus = null
        Handler(Looper.getMainLooper()).postDelayed({
            focusRequest?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
            restoreStreamVolume()
        }, 400)
    }

    private fun startSound() {
        if (spec.sound == SYSTEM_SOUND) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val started = uri != null && runCatching {
                media = MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(context, uri)
                    isLooping = true
                    setVolume(0.04f, 0.04f)
                    prepare()
                    start()
                }
            }.isSuccess
            if (started) return
            media = null
        }
        val type = runCatching { SoundType.valueOf(spec.sound) }.getOrDefault(SoundType.BIRDS)
        synth = SynthPlayer(context, type, AudioAttributes.USAGE_ALARM).apply {
            setVolume(0.04f)
            start()
        }
    }

    private fun applyStreamVolume() = runCatching {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        previousStreamVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val target = (spec.volume * max).roundToInt().coerceIn(1, max)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
    }

    private fun restoreStreamVolume() = runCatching {
        if (previousStreamVolume >= 0) audio.setStreamVolume(AudioManager.STREAM_ALARM, previousStreamVolume, 0)
    }
}
