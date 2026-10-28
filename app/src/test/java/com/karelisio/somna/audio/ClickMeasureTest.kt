package com.karelisio.somna.audio

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fireplace must crackle softly: no sharp clicks (large jumps between two samples). */
class ClickMeasureTest {
    @Test
    fun `fire has no sharp clicks`() {
        for (seed in 1L..3L) {
            val generator = SoundGenerators.create(SoundType.FIRE, seed)
            val buffer = FloatArray(1024)
            var previous = 0f
            var maxJump = 0f
            repeat(SoundGenerators.SAMPLE_RATE * 60 / buffer.size) {
                generator.render(buffer, buffer.size)
                for (s in buffer) {
                    maxJump = maxOf(maxJump, abs(s - previous))
                    previous = s
                }
            }
            assertTrue("seed $seed: max jump $maxJump", maxJump < 0.3f)
        }
    }
}
