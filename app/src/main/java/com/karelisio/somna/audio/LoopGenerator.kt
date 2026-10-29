package com.karelisio.somna.audio

import kotlin.math.max
import kotlin.math.min

/** A decoded field recording: interleaved 16-bit PCM, in the first [size] samples of [pcm]. */
class Recording(val pcm: ShortArray, val channels: Int, val sampleRate: Int, size: Int = pcm.size) {
    val frames: Int = size / channels
}

/**
 * Plays a [Recording] endlessly with neither seam nor audible period. The clip is read in long
 * random stretches, each one equal-power crossfaded into the next. Crossfades only happen at quiet
 * moments of the recording (between two waves, between crackles) and never touch the file's own
 * boundaries, so codec priming or padding can't be heard either. Pure Kotlin, unit-testable.
 */
internal class LoopGenerator(
    private val recording: Recording,
    private val rng: Rng,
    crossfadeSeconds: Float = 3f,
    private val minStretchSeconds: Float = 16f,
    private val maxStretchSeconds: Float = 40f,
) : SoundGenerator {

    override val sampleRate: Int get() = recording.sampleRate
    override val channels: Int get() = recording.channels

    private val pcm = recording.pcm
    private val ch = recording.channels
    private val fade = (crossfadeSeconds * sampleRate).toInt()
    private val margin = sampleRate / 4

    /** Latest frame where a crossfade may start, so that it ends before the file's tail. */
    private val lastStart = recording.frames - margin - fade

    /** Starts of quiet windows (one crossfade long), sorted. */
    private val cues: IntArray

    /** Cues where a stretch may begin, leaving room for at least one stretch after them. */
    private val entries: IntArray

    private var position = 0
    private var voiceStart = 0
    private var remaining = 0
    private var incoming = -1
    private var faded = 0

    init {
        require(recording.frames >= 4 * fade + 2 * margin) { "Recording too short to loop" }
        cues = quietWindows()
        val roomy = cues.filter { it <= lastStart - fade - (minStretchSeconds * sampleRate).toInt() }
        entries = (roomy.ifEmpty { cues.filter { it <= lastStart - 2 * fade } }.ifEmpty { listOf(margin) }).toIntArray()
        startVoice(entries[rng.nextInt(entries.size)])
    }

    /** Number of crossfades so far — for tests. */
    var crossfades = 0
        private set

    override fun render(buffer: FloatArray, count: Int) {
        var o = 0
        for (f in 0 until count) {
            if (incoming < 0) {
                val p = position * ch
                for (c in 0 until ch) buffer[o++] = pcm[p + c] * SCALE
                position++
                if (--remaining <= 0) beginCrossfade()
            } else {
                val t = faded.toFloat() / fade
                // Equal power (the two stretches are uncorrelated): sin² + cos² = 1, no dip, no bump.
                val gainIn = Sine.of(t * 0.25f)
                val gainOut = Sine.of(t * 0.25f + 0.25f)
                val p = position * ch
                val q = incoming * ch
                for (c in 0 until ch) buffer[o++] = (pcm[p + c] * gainOut + pcm[q + c] * gainIn) * SCALE
                position++
                incoming++
                if (++faded >= fade) {
                    position = incoming
                    incoming = -1
                    planStretch()
                }
            }
        }
    }

    private fun startVoice(at: Int) {
        voiceStart = at
        position = at
        planStretch()
    }

    /** Picks the quiet moment where the current stretch will hand over to the next one. */
    private fun planStretch() {
        val stretch = (rng.range(minStretchSeconds, maxStretchSeconds) * sampleRate).toInt()
        val target = voiceStart + stretch
        var out = -1
        for (cue in cues) {
            if (cue > lastStart) break
            if (cue <= position) continue
            out = cue
            if (cue >= target) break
        }
        if (out < 0) out = max(position + 1, min(lastStart, position + stretch))
        remaining = out - position
    }

    private fun beginCrossfade() {
        // Prefer material not heard recently: away from the stretch that is ending.
        val avoidFrom = voiceStart - AVOID_SECONDS * sampleRate
        val avoidTo = position + AVOID_SECONDS * sampleRate
        var candidates = entries.filter { it < avoidFrom || it > avoidTo }
        if (candidates.isEmpty()) candidates = entries.filter { it < position - fade || it > position + fade }
        val next = if (candidates.isEmpty()) entries[rng.nextInt(entries.size)] else candidates[rng.nextInt(candidates.size)]
        incoming = next
        voiceStart = next
        faded = 0
        crossfades++
    }

    /** Window starts (every 100 ms) whose mean energy over one crossfade is in the quietest 40 %. */
    private fun quietWindows(): IntArray {
        val hop = sampleRate / 10
        val hops = recording.frames / hop
        val energy = DoubleArray(hops)
        for (h in 0 until hops) {
            var sum = 0.0
            val from = h * hop * ch
            for (i in from until from + hop * ch) {
                val s = pcm[i].toDouble()
                sum += s * s
            }
            energy[h] = sum
        }
        val span = max(1, fade / hop)
        val starts = ArrayList<Int>()
        val windows = ArrayList<Double>()
        var sum = 0.0
        for (h in 0 until hops) {
            sum += energy[h]
            if (h >= span) sum -= energy[h - span]
            val start = (h - span + 1) * hop
            if (h >= span - 1 && start >= margin && start <= lastStart) {
                starts += start
                windows += sum
            }
        }
        if (starts.isEmpty()) return intArrayOf(margin)
        val threshold = windows.sorted()[(windows.size * QUIET_FRACTION).toInt().coerceAtMost(windows.size - 1)]
        return starts.indices.filter { windows[it] <= threshold }.map { starts[it] }.toIntArray()
    }

    private companion object {
        const val SCALE = 1f / 32_768f
        const val QUIET_FRACTION = 0.4f
        const val AVOID_SECONDS = 12
    }
}
