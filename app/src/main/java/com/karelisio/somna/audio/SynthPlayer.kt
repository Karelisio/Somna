package com.karelisio.somna.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Streams a procedural [SoundType] through an [AudioTrack] on a dedicated audio thread.
 * [usage] is [AudioAttributes.USAGE_ALARM] (plays even in silent mode) or [AudioAttributes.USAGE_MEDIA].
 */
class SynthPlayer(context: Context, val type: SoundType, private val usage: Int) {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    @Volatile
    private var session: AtomicBoolean? = null

    @Volatile
    private var targetVolume = 1f

    /** Smoothed RMS (0..1) of the last rendered buffer, after volume — handy for visualizers. */
    @Volatile
    var level: Float = 0f

    val isPlaying: Boolean
        get() = session?.get() == true

    @Synchronized
    fun start() {
        if (isPlaying) return
        val active = AtomicBoolean(true)
        session = active
        Thread({ run(active) }, "Somna-${type.name}").start()
    }

    fun setVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
    }

    /** Returns immediately; the audio thread fades out (150 ms) and releases the track by itself. */
    @Synchronized
    fun stop() {
        session?.set(false)
        session = null
    }

    private fun run(active: AtomicBoolean) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var track: AudioTrack? = null
        try {
            val sampleRate = SoundGenerators.SAMPLE_RATE
            val generator = SoundGenerators.create(type)
            val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val bufferBytes = max(minBytes, sampleRate / 4 * Float.SIZE_BYTES)
            val alarm = usage == AudioAttributes.USAGE_ALARM
            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(if (alarm) AudioAttributes.CONTENT_TYPE_SONIFICATION else AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val builder = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .setSessionId(audioManager?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE)
            if (!alarm) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
            track = builder.build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack not initialized for $type")
                return
            }

            val buffer = FloatArray(CHUNK)
            // Per-sample one-pole ramp (~50 ms time constant): starts from silence, no zipper noise.
            val smoothing = 1f / (0.05f * sampleRate)
            var gain = 0f
            track.play()

            while (active.get()) {
                generator.render(buffer, CHUNK)
                val target = targetVolume
                var sumSquares = 0f
                for (i in 0 until CHUNK) {
                    gain += (target - gain) * smoothing
                    val s = buffer[i] * gain
                    buffer[i] = s
                    sumSquares += s * s
                }
                level = level * 0.7f + sqrt(sumSquares / CHUNK).coerceAtMost(1f) * 0.3f
                if (track.write(buffer, 0, CHUNK, AudioTrack.WRITE_BLOCKING) < 0) break
            }

            // Linear fade-out to avoid a click.
            val fadeTotal = (sampleRate * 0.15f).toInt()
            var faded = 0
            while (faded < fadeTotal) {
                val n = min(CHUNK, fadeTotal - faded)
                generator.render(buffer, n)
                for (i in 0 until n) {
                    buffer[i] *= gain * (1f - (faded + i).toFloat() / fadeTotal)
                }
                track.write(buffer, 0, n, AudioTrack.WRITE_BLOCKING)
                faded += n
            }
            track.stop() // in streaming mode, plays what was written then stops
            Thread.sleep(bufferBytes * 1_000L / (sampleRate * Float.SIZE_BYTES) + 30)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            Log.e(TAG, "Synth playback failed for $type", t)
        } finally {
            active.set(false)
            if (session == null || session === active) level = 0f
            runCatching { track?.release() }
        }
    }

    private companion object {
        const val TAG = "SynthPlayer"
        const val CHUNK = 1024
    }
}
