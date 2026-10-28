package com.karelisio.somna.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Detects light sleep from body movements transmitted through the mattress: counts the seconds
 * whose acceleration deviates from gravity above a threshold, over a sliding minute.
 */
class SmartWakeMonitor(
    context: Context,
    sensitivity: Int,
    private val onLightSleep: () -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val threshold = when (sensitivity) {
        0 -> 0.35f
        2 -> 0.10f
        else -> 0.18f
    }
    private val requiredActiveSeconds = when (sensitivity) {
        0 -> 5
        2 -> 2
        else -> 3
    }

    private var gravity = SensorManager.GRAVITY_EARTH
    private var startedAt = 0L
    private var currentSecond = -1L
    private var secondPeak = 0f
    private val activeSeconds = ArrayDeque<Long>()
    private var triggered = false

    val available: Boolean get() = sensor != null

    fun start(): Boolean {
        val s = sensor ?: return false
        startedAt = SystemClock.elapsedRealtime()
        return sensorManager.registerListener(this, s, 50_000)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val magnitude = sqrt(x * x + y * y + z * z)
        gravity = gravity * 0.95f + magnitude * 0.05f
        val deviation = abs(magnitude - gravity)

        val second = event.timestamp / 1_000_000_000L
        if (second != currentSecond) {
            if (currentSecond >= 0 && secondPeak > threshold) activeSeconds.addLast(currentSecond)
            currentSecond = second
            secondPeak = 0f
            while (activeSeconds.isNotEmpty() && second - activeSeconds.first() > 60) activeSeconds.removeFirst()
        }
        secondPeak = max(secondPeak, deviation)

        // Ignore the first 90 s: the phone has just been put down / the screen woke up.
        val warmedUp = SystemClock.elapsedRealtime() - startedAt > 90_000
        if (warmedUp && !triggered && activeSeconds.size >= requiredActiveSeconds) {
            triggered = true
            onLightSleep()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
