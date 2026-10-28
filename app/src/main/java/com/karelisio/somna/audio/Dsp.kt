package com.karelisio.somna.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

// Small allocation-free DSP building blocks shared by the generators. Pure Kotlin (unit-testable on the JVM).

internal const val TWO_PI = (2.0 * PI).toFloat()

/** xorshift64* — fast, good enough for audio noise. */
internal class Rng(seed: Long) {
    private var state: Long = splitMix(seed).let { if (it == 0L) 0x2545F4914F6CDD1DL else it }

    fun nextLong(): Long {
        var x = state
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        state = x
        return x * 0x2545F4914F6CDD1DL
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() * (1f / (1 shl 24))

    /** Uniform in [-1, 1). */
    fun bipolar(): Float = nextFloat() * 2f - 1f

    fun range(min: Float, max: Float): Float = min + (max - min) * nextFloat()

    fun nextInt(bound: Int): Int = (nextFloat() * bound).toInt().coerceAtMost(bound - 1)

    fun chance(probability: Float): Boolean = nextFloat() < probability

    /** Samples until the next event of a Poisson process with [ratePerSecond]. */
    fun poissonInterval(ratePerSecond: Float, sampleRate: Int): Int =
        (-ln(1f - nextFloat()) / ratePerSecond * sampleRate).toInt().coerceAtLeast(1)

    private fun splitMix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}

/** Interpolated sine lookup; phase is expressed in cycles, in [0, 1). */
internal object Sine {
    private const val SIZE = 4096
    private const val MASK = SIZE - 1
    private val table = FloatArray(SIZE + 1) { sin(2.0 * PI * it / SIZE).toFloat() }

    fun of(phase: Float): Float {
        val p = phase * SIZE
        val i = p.toInt()
        val frac = p - i
        val idx = i and MASK
        val a = table[idx]
        return a + (table[idx + 1] - a) * frac
    }
}

/** One-pole lowpass coefficient for cutoff [hz]. */
internal fun lowpassCoef(hz: Float, sampleRate: Int): Float =
    1f - exp(-TWO_PI * hz / sampleRate)

internal class OnePole(var coef: Float) {
    var y = 0f
        private set

    fun process(x: Float): Float {
        y += coef * (x - y)
        return y
    }
}

/** One-pole highpass (x minus its lowpass) — removes rumble and DC drift. */
internal class HighPass(hz: Float, sampleRate: Int) {
    private val low = OnePole(lowpassCoef(hz, sampleRate))

    fun process(x: Float): Float = x - low.process(x)
}

/** Paul Kellet's economy pink noise, normalised to RMS ≈ 0.25. */
internal class PinkNoise(private val rng: Rng) {
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f

    fun next(): Float {
        val w = rng.bipolar()
        b0 = 0.99765f * b0 + w * 0.0990460f
        b1 = 0.96300f * b1 + w * 0.2965164f
        b2 = 0.57000f * b2 + w * 1.0526913f
        return (b0 + b1 + b2 + w * 0.1848f) * PINK_GAIN
    }

    private companion object {
        const val PINK_GAIN = 0.22f
    }
}

/** Leaky-integrated (brown) noise, RMS ≈ 0.25. */
internal class BrownNoise(private val rng: Rng) {
    private var y = 0f

    fun next(): Float {
        y = 0.985f * y + 0.03f * rng.bipolar()
        return y * 2.8f
    }
}

/** Two-pole resonator: an impulse of 1 rings as a decaying sine of amplitude ~1. */
internal class Resonator {
    private var c1 = 0f
    private var c2 = 0f
    private var norm = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun tune(hz: Float, decaySeconds: Float, sampleRate: Int) {
        val w = TWO_PI * hz / sampleRate
        val r = exp(-1f / (decaySeconds * sampleRate))
        c1 = 2f * r * cos(w)
        c2 = -r * r
        norm = sin(w)
    }

    fun reset() {
        y1 = 0f
        y2 = 0f
    }

    fun process(x: Float): Float {
        val y = c1 * y1 + c2 * y2 + x * norm
        y2 = y1
        y1 = y
        return y
    }
}

/** Slowly wandering random value (e.g. flicker, gusts): new targets at random intervals, smoothed. */
internal class Drift(
    private val rng: Rng,
    private val sampleRate: Int,
    private val min: Float,
    private val max: Float,
    private val minInterval: Float,
    private val maxInterval: Float,
    smoothingHz: Float,
) {
    private val smoother = OnePole(lowpassCoef(smoothingHz, sampleRate))
    private var target = (min + max) / 2f
    private var countdown = 0

    init {
        repeat(sampleRate) { smoother.process(target) }
    }

    fun next(): Float {
        if (--countdown <= 0) {
            target = rng.range(min, max)
            countdown = (rng.range(minInterval, maxInterval) * sampleRate).toInt()
        }
        return smoother.process(target)
    }
}

/** Transparent below 0.6, smoothly saturates towards ±0.9 above (guarantees headroom). */
internal fun softLimit(x: Float): Float {
    val ax = abs(x)
    if (ax <= 0.6f) return x
    val t = (ax - 0.6f) / 0.3f
    val sat = if (t >= 3f) 1f else t * (27f + t * t) / (27f + 9f * t * t)
    val y = 0.6f + 0.3f * sat
    return if (x < 0f) -y else y
}
