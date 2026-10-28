package com.karelisio.somna.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class SoundGeneratorsTest {

    @Test
    fun everySoundIsFiniteBoundedAndAudible() {
        val sampleRate = SoundGenerators.SAMPLE_RATE
        val chunkSizes = intArrayOf(1, 7, 64, 333, 1024, 4096)
        val buffer = FloatArray(chunkSizes.max())

        for (type in SoundType.entries) {
            val generator = SoundGenerators.create(type, seed = 42L)
            val total = sampleRate * 20
            var rendered = 0
            var chunk = 0
            var sumSquares = 0.0
            while (rendered < total) {
                val n = min(chunkSizes[chunk++ % chunkSizes.size], total - rendered)
                generator.render(buffer, n)
                for (i in 0 until n) {
                    val s = buffer[i]
                    assertTrue("$type produced a non-finite sample", s.isFinite())
                    assertTrue("$type sample out of range: $s", abs(s) <= 1f)
                    sumSquares += s * s
                }
                rendered += n
            }
            val rms = sqrt(sumSquares / total)
            assertTrue("$type RMS out of range: $rms", rms in 0.03..0.5)
        }
    }

    @Test
    fun sameSeedIsDeterministic() {
        for (type in SoundType.entries) {
            val a = FloatArray(2048).also { SoundGenerators.create(type, seed = 7L).render(it, it.size) }
            val b = FloatArray(2048).also { SoundGenerators.create(type, seed = 7L).render(it, it.size) }
            assertTrue("$type is not deterministic for a given seed", a.contentEquals(b))
        }
    }

    @Test
    fun soundListsSplitAlarmAndSleepSounds() {
        assertEquals(listOf(SoundType.BIRDS, SoundType.CHIMES, SoundType.WAVES), SoundType.alarmSounds)
        assertEquals(listOf(SoundType.RAIN, SoundType.WHITE_NOISE, SoundType.FIRE), SoundType.sleepSounds)
    }
}
