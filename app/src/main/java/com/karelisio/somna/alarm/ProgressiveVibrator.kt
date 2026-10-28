package com.karelisio.somna.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Gentle vibration that grows in strength, flagged as an alarm so it works in silent mode. */
class ProgressiveVibrator(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        }
    private var job: Job? = null

    fun start(scope: CoroutineScope, rampSeconds: Int) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        job?.cancel()
        job = scope.launch {
            val started = System.currentTimeMillis()
            while (isActive) {
                val t = ((System.currentTimeMillis() - started) / 1000f / rampSeconds.coerceAtLeast(10)).coerceIn(0f, 1f)
                vibrate(v, pattern(v, t))
                delay(2_600)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        vibrator?.cancel()
    }

    private fun pattern(v: Vibrator, intensity: Float): VibrationEffect {
        val pulse = (180 + 320 * intensity).toLong()
        return if (v.hasAmplitudeControl()) {
            val amp = (30 + 225 * intensity * intensity).toInt().coerceIn(1, 255)
            VibrationEffect.createWaveform(longArrayOf(0, pulse, 260, pulse), intArrayOf(0, amp, 0, amp), -1)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, pulse, 260, pulse), -1)
        }
    }

    private fun vibrate(v: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }
}
