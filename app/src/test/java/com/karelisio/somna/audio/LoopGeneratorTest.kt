package com.karelisio.somna.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The looped recordings must never click at a seam, nor pump in loudness at a crossfade. */
class LoopGeneratorTest {

    /** Stationary noise, to check that crossfades keep a steady loudness. */
    private fun noise(seconds: Int, sampleRate: Int, channels: Int): Recording {
        val rng = Rng(7)
        val lowpass = Array(channels) { OnePole(lowpassCoef(3_000f, sampleRate)) }
        val pcm = ShortArray(seconds * sampleRate * channels)
        for (i in pcm.indices) pcm[i] = (lowpass[i % channels].process(rng.bipolar()) * 20_000f).toInt().toShort()
        return Recording(pcm, channels, sampleRate)
    }

    /**
     * Slow, smooth, swelling tones: consecutive samples barely differ, so any seam (a hard jump from
     * one place of the clip to another) stands out by orders of magnitude.
     */
    private fun smooth(seconds: Int, sampleRate: Int): Recording {
        val pcm = ShortArray(seconds * sampleRate) { f ->
            val t = f.toDouble() / sampleRate
            val swell = 0.2 + 0.8 * sin(PI * t / 9).let { it * it }
            (swell * (sin(2 * PI * 3.1 * t) * 0.6 + sin(2 * PI * 4.7 * t + 1) * 0.4) * 20_000).toInt().toShort()
        }
        return Recording(pcm, 1, sampleRate)
    }

    private class Stats(val maxJump: Float, val windowsDb: List<Double>)

    private fun measure(generator: SoundGenerator, seconds: Int): Stats {
        val ch = generator.channels
        val buffer = FloatArray(1024 * ch)
        val previous = FloatArray(ch)
        var first = true
        var maxJump = 0f
        val windowsDb = ArrayList<Double>()
        var sum = 0.0
        var n = 0
        repeat(seconds * generator.sampleRate / 1024) {
            generator.render(buffer, 1024)
            for (i in buffer.indices) {
                val s = buffer[i]
                // The very first frame starts from silence: the player's gain ramp handles it.
                if (!first) maxJump = maxOf(maxJump, abs(s - previous[i % ch]))
                if (i % ch == ch - 1) first = false
                previous[i % ch] = s
                sum += s * s
                if (++n == generator.sampleRate * ch) {
                    windowsDb += 10 * log10(sum / n + 1e-12)
                    sum = 0.0
                    n = 0
                }
            }
        }
        return Stats(maxJump, windowsDb)
    }

    private fun sourceJump(recording: Recording): Float {
        var max = 0
        val ch = recording.channels
        for (i in ch until recording.pcm.size) max = maxOf(max, abs(recording.pcm[i] - recording.pcm[i - ch]))
        return max / 32_768f
    }

    @Test
    fun `crossfades are seamless and keep a steady loudness`() {
        val recording = noise(seconds = 80, sampleRate = 16_000, channels = 2)
        val generator = LoopGenerator(recording, Rng(1))
        val stats = measure(generator, seconds = 600)
        assertTrue("crossfades ${generator.crossfades}", generator.crossfades >= 600 / 40)
        val mean = stats.windowsDb.average()
        val spread = stats.windowsDb.maxOf { abs(it - mean) }
        assertTrue("loudness spread $spread dB", spread < 1.5)
    }

    @Test
    fun `no seam, whatever the clip length, and never out of bounds`() {
        for (seed in 1L..20L) {
            val recording = smooth(seconds = 13 + 4 * seed.toInt(), sampleRate = 8_000)
            val generator = LoopGenerator(recording, Rng(seed))
            val stats = measure(generator, seconds = 1_800)
            // Two equal-power stretches may add up to √2 × the steepest slope of the source; a seam would be ~100×.
            assertTrue("seed $seed: jump ${stats.maxJump} vs ${sourceJump(recording)}", stats.maxJump <= sourceJump(recording) * SQRT2)
            assertTrue("seed $seed: crossfades ${generator.crossfades}", generator.crossfades >= 1_800 / 45)
        }
    }

    /**
     * Opt-in check on the real assets, decoded to raw s16le by ffmpeg:
     * ./gradlew testDebugUnitTest --tests '*LoopGeneratorTest*' -Ploops.dir=… (files: name_<rate>_<channels>.raw)
     */
    @Test
    fun `real recordings loop without clicks`() {
        val dir = System.getProperty("somna.loops.dir")?.let(::File)
        assumeTrue(dir?.isDirectory == true)
        for (file in dir!!.listFiles { f -> f.name.endsWith(".raw") }!!.sorted()) {
            val parts = file.nameWithoutExtension.split("_")
            val rate = parts[parts.size - 2].toInt()
            val channels = parts.last().toInt()
            val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val pcm = ShortArray(bytes.remaining()).also { bytes.get(it) }
            val recording = Recording(pcm, channels, rate)
            val generator = LoopGenerator(recording, Rng(3))
            val stats = measure(generator, seconds = 900)
            val sourceDb = sourceWindowsDb(recording)
            println(
                "${file.name}: jump ${"%.3f".format(stats.maxJump)} (source ${"%.3f".format(sourceJump(recording))}), " +
                    "crossfades ${generator.crossfades}, loudness ${"%.1f".format(stats.windowsDb.min())}..${"%.1f".format(stats.windowsDb.max())} dB " +
                    "(source ${"%.1f".format(sourceDb.min())}..${"%.1f".format(sourceDb.max())})",
            )
            assertTrue(file.name, stats.maxJump <= sourceJump(recording) * SQRT2)
        }
    }

    private companion object {
        const val SQRT2 = 1.4143f
    }

    private fun sourceWindowsDb(recording: Recording): List<Double> {
        val window = recording.sampleRate * recording.channels
        return (0 until recording.pcm.size / window).map { w ->
            var sum = 0.0
            for (i in w * window until (w + 1) * window) {
                val s = recording.pcm[i] / 32_768.0
                sum += s * s
            }
            10 * log10(sum / window + 1e-12)
        }
    }
}
