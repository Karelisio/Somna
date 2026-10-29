package com.karelisio.somna.audio

import kotlin.math.exp
import kotlin.math.pow

/**
 * Fills [buffer] with [count] frames of [channels] interleaved samples in [-1, 1];
 * successive calls continue the same stream.
 */
fun interface SoundGenerator {
    fun render(buffer: FloatArray, count: Int)

    val sampleRate: Int get() = SoundGenerators.SAMPLE_RATE
    val channels: Int get() = 1
}

/** Procedural, endless and non-repeating sound generators — no audio assets needed. */
object SoundGenerators {
    const val SAMPLE_RATE = 32_000

    fun create(type: SoundType, seed: Long = System.nanoTime()): SoundGenerator = when (type) {
        SoundType.BIRDS -> BirdsGenerator(Rng(seed))
        SoundType.CHIMES -> ChimesGenerator(Rng(seed))
        SoundType.WAVES -> WavesGenerator(Rng(seed))
        SoundType.RAIN -> RainGenerator(Rng(seed))
        SoundType.WHITE_NOISE -> WhiteNoiseGenerator(Rng(seed))
        SoundType.FIRE -> FireGenerator(Rng(seed))
    }
}

private const val SR = SoundGenerators.SAMPLE_RATE

// ---------------------------------------------------------------------------------------------
// White noise
// ---------------------------------------------------------------------------------------------

private class WhiteNoiseGenerator(private val rng: Rng) : SoundGenerator {
    private val soften = OnePole(lowpassCoef(7_000f, SR))
    private val air = OnePole(lowpassCoef(12_000f, SR))

    override fun render(buffer: FloatArray, count: Int) {
        for (i in 0 until count) {
            buffer[i] = air.process(soften.process(rng.bipolar())) * 0.48f
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Rain: pink bed + soft hiss + resonant droplets
// ---------------------------------------------------------------------------------------------

private class RainGenerator(private val rng: Rng) : SoundGenerator {
    private val pink = PinkNoise(rng)
    private val bedTone = OnePole(lowpassCoef(5_500f, SR))
    private val bedCut = HighPass(280f, SR)
    private val hissLow = OnePole(lowpassCoef(3_000f, SR))
    private val gust = Drift(rng, SR, 0.8f, 1.15f, 1.5f, 4f, 0.4f)

    private val drops = Array(MAX_DROPS) { Resonator() }
    private val dropLife = IntArray(MAX_DROPS)
    private val dropBurst = IntArray(MAX_DROPS)
    private val dropAmp = FloatArray(MAX_DROPS)
    private var nextDrop = 1

    override fun render(buffer: FloatArray, count: Int) {
        for (i in 0 until count) {
            if (--nextDrop <= 0) {
                spawnDrop()
                nextDrop = rng.poissonInterval(DROP_RATE, SR)
            }
            val g = gust.next()
            val bed = bedCut.process(bedTone.process(pink.next()))
            val white = rng.bipolar()
            val hiss = white - hissLow.process(white)

            var dropsOut = 0f
            for (d in 0 until MAX_DROPS) {
                if (dropLife[d] <= 0) continue
                dropLife[d]--
                val excitation = if (dropBurst[d] > 0) {
                    dropBurst[d]--
                    dropAmp[d] * rng.bipolar()
                } else 0f
                dropsOut += drops[d].process(excitation)
            }
            buffer[i] = softLimit((bed * 0.85f + hiss * 0.07f) * g + dropsOut * 0.8f)
        }
    }

    private fun spawnDrop() {
        val slot = (0 until MAX_DROPS).firstOrNull { dropLife[it] <= 0 } ?: return
        val heavy = rng.chance(0.08f)
        val hz = if (heavy) rng.range(900f, 2_000f) else 2_000f * 3f.pow(rng.nextFloat())
        val decay = if (heavy) rng.range(0.010f, 0.025f) else rng.range(0.0015f, 0.006f)
        val u = rng.nextFloat()
        dropAmp[slot] = if (heavy) rng.range(0.10f, 0.18f) else 0.015f + 0.11f * u * u * u
        dropBurst[slot] = 2 + rng.nextInt(10)
        dropLife[slot] = (decay * SR * 7f).toInt() + dropBurst[slot]
        drops[slot].reset()
        drops[slot].tune(hz, decay, SR)
    }

    private companion object {
        const val MAX_DROPS = 24
        const val DROP_RATE = 34f
    }
}

// ---------------------------------------------------------------------------------------------
// Ocean waves: overlapping swells, each a noise source whose brightness follows its envelope
// ---------------------------------------------------------------------------------------------

private class WavesGenerator(private val rng: Rng) : SoundGenerator {
    private val pink = PinkNoise(rng)
    private val brown = BrownNoise(rng)
    private val surf = OnePole(lowpassCoef(350f, SR))
    private val surfLevel = Drift(rng, SR, 0.7f, 1.2f, 3f, 8f, 0.2f)

    private val active = BooleanArray(SLOTS)
    private val position = IntArray(SLOTS)
    private val length = IntArray(SLOTS)
    private val peak = FloatArray(SLOTS)
    private val rise = FloatArray(SLOTS)
    private val lp1 = Array(SLOTS) { OnePole(0f) }
    private val lp2 = Array(SLOTS) { OnePole(0f) }
    private val envNow = FloatArray(SLOTS)
    private val envStep = FloatArray(SLOTS)
    private val foamHp = OnePole(lowpassCoef(2_500f, SR))
    private val subCut = HighPass(50f, SR)
    private var nextWave = (SR * 0.5f).toInt()
    private var blockLeft = 0

    override fun render(buffer: FloatArray, count: Int) {
        for (i in 0 until count) {
            if (--nextWave <= 0) startWave()
            if (blockLeft-- <= 0) {
                updateControl()
                blockLeft = BLOCK - 1
            }
            val noise = pink.next() * 0.9f + brown.next() * 0.3f
            val white = rng.bipolar()
            val foamNoise = white - foamHp.process(white)

            var out = surf.process(brown.next()) * 0.35f * surfLevel.next()
            for (w in 0 until SLOTS) {
                if (!active[w]) continue
                val e = envNow[w]
                envNow[w] = e + envStep[w]
                val body = lp2[w].process(lp1[w].process(noise))
                out += body * e * peak[w] * 1.05f + foamNoise * e * e * e * peak[w] * 0.08f
                if (++position[w] >= length[w]) active[w] = false
            }
            buffer[i] = softLimit(subCut.process(out))
        }
    }

    private fun startWave() {
        val period = rng.range(7f, 12f)
        nextWave = (period * SR).toInt()
        val slot = (0 until SLOTS).firstOrNull { !active[it] } ?: return
        active[slot] = true
        position[slot] = 0
        length[slot] = (period * rng.range(1.3f, 1.7f) * SR).toInt()
        peak[slot] = rng.range(0.6f, 1f)
        rise[slot] = rng.range(0.25f, 0.4f)
        envNow[slot] = 0f
        envStep[slot] = 0f
    }

    /** Envelope and filter cutoff at control rate; envelope is interpolated per sample. */
    private fun updateControl() {
        for (w in 0 until SLOTS) {
            if (!active[w]) continue
            val target = envelope(w, (position[w] + BLOCK).toFloat() / length[w])
            envStep[w] = (target - envNow[w]) / BLOCK
            val cutoff = 200f + 3_400f * envNow[w].pow(1.5f)
            val coef = lowpassCoef(cutoff, SR)
            lp1[w].coef = coef
            lp2[w].coef = coef
        }
    }

    private fun envelope(w: Int, t: Float): Float {
        if (t >= 1f) return 0f
        val r = rise[w]
        return if (t < r) {
            val s = Sine.of(t / r * 0.25f) // sin(π/2 · t/r)
            s * s
        } else {
            (1f - (t - r) / (1f - r)).pow(2.2f)
        }
    }

    private companion object {
        const val SLOTS = 4
        const val BLOCK = 32
    }
}

// ---------------------------------------------------------------------------------------------
// Fireplace: rumble + flame whoosh + bursty crackles + occasional pops
// ---------------------------------------------------------------------------------------------

private class FireGenerator(private val rng: Rng) : SoundGenerator {
    private val brown = BrownNoise(rng)
    private val pink = PinkNoise(rng)
    private val rumbleCut = HighPass(40f, SR)
    private val rumble1 = OnePole(lowpassCoef(260f, SR))
    private val rumble2 = OnePole(lowpassCoef(260f, SR))
    private val whooshLow = OnePole(lowpassCoef(220f, SR))
    private val whooshHigh = OnePole(lowpassCoef(1_400f, SR))
    private val flicker = Drift(rng, SR, 0.55f, 1.3f, 0.08f, 0.4f, 3f)
    private val breath = Drift(rng, SR, 0.7f, 1.2f, 1f, 3f, 0.5f)

    private val crackleEnv = FloatArray(CRACKLES)
    private val cracklePeak = FloatArray(CRACKLES)
    private val crackleAttack = IntArray(CRACKLES)
    private val crackleDecay = FloatArray(CRACKLES)
    private val crackleLife = IntArray(CRACKLES)
    private var lastWhite = 0f
    // Crackles are band-limited (no harsh top end) to sound like distant wood, not clicks.
    private val crackleLow = OnePole(lowpassCoef(2_200f, SR))
    private val crackleBody = OnePole(lowpassCoef(2_200f, SR))
    private val popSmooth = OnePole(lowpassCoef(900f, SR))
    private var nextCrackle = 1
    private var clusterLeft = 0
    private var nextCluster = SR

    private val pops = Array(POPS) { Resonator() }
    private val popLife = IntArray(POPS)
    private val popBurst = IntArray(POPS)
    private val popAmp = FloatArray(POPS)
    private var nextPop = SR * 2

    override fun render(buffer: FloatArray, count: Int) {
        for (i in 0 until count) {
            if (--nextCluster <= 0) {
                clusterLeft = (rng.range(0.05f, 0.3f) * SR).toInt()
                nextCluster = rng.poissonInterval(0.4f, SR)
            }
            val inCluster = clusterLeft-- > 0
            if (--nextCrackle <= 0) {
                spawnCrackle(inCluster)
                nextCrackle = rng.poissonInterval(if (inCluster) rng.range(10f, 25f) else 1.5f, SR)
            }
            if (--nextPop <= 0) {
                spawnPop()
                nextPop = rng.poissonInterval(0.12f, SR)
            }

            val f = flicker.next()
            val rumble = rumble2.process(rumble1.process(rumbleCut.process(brown.next()))) * 0.9f * breath.next()
            val p = pink.next()
            val whoosh = (whooshHigh.process(p) - whooshLow.process(p)) * 0.8f * f

            val white = rng.bipolar()
            val hp = crackleBody.process(crackleLow.process((white - lastWhite) * 0.5f)) * 3f
            lastWhite = white
            var crackles = 0f
            for (c in 0 until CRACKLES) {
                if (crackleLife[c] <= 0) continue
                crackleLife[c]--
                // Short linear attack, then exponential decay: a soft "tick", never a click.
                if (crackleAttack[c] > 0) {
                    crackleAttack[c]--
                    crackleEnv[c] = (crackleEnv[c] + cracklePeak[c] / ATTACK).coerceAtMost(cracklePeak[c])
                } else {
                    crackleEnv[c] *= crackleDecay[c]
                }
                crackles += hp * crackleEnv[c]
            }

            var popOut = 0f
            for (k in 0 until POPS) {
                if (popLife[k] <= 0) continue
                popLife[k]--
                val excitation = if (popBurst[k] > 0) {
                    popBurst[k]--
                    popAmp[k] * (0.6f + 0.4f * rng.bipolar())
                } else 0f
                popOut += pops[k].process(popSmooth.process(excitation))
            }
            buffer[i] = softLimit(rumble * f + whoosh + crackles + popOut)
        }
    }

    private fun spawnCrackle(inCluster: Boolean) {
        val slot = (0 until CRACKLES).firstOrNull { crackleLife[it] <= 0 } ?: return
        val u = rng.nextFloat()
        cracklePeak[slot] = (if (inCluster) 0.025f else 0.015f) + 0.07f * u * u * u * u
        crackleEnv[slot] = 0f
        crackleAttack[slot] = ATTACK
        val tau = rng.range(0.002f, 0.006f)
        crackleDecay[slot] = exp(-1f / (tau * SR))
        crackleLife[slot] = ATTACK + (tau * SR * 6f).toInt() + 2
    }

    private fun spawnPop() {
        val slot = (0 until POPS).firstOrNull { popLife[it] <= 0 } ?: return
        val decay = rng.range(0.01f, 0.04f)
        pops[slot].reset()
        pops[slot].tune(rng.range(300f, 900f), decay, SR)
        popAmp[slot] = rng.range(0.05f, 0.12f)
        popBurst[slot] = 2 + rng.nextInt(6)
        popLife[slot] = (decay * SR * 7f).toInt()
    }

    private companion object {
        const val CRACKLES = 10
        const val POPS = 3
        /** ~1.5 ms attack at 32 kHz. */
        const val ATTACK = 48
    }
}

// ---------------------------------------------------------------------------------------------
// Wind chimes: inharmonic bell voices on a pentatonic scale + light comb/allpass reverb
// ---------------------------------------------------------------------------------------------

private class ChimesGenerator(private val rng: Rng) : SoundGenerator {
    private val phase = FloatArray(VOICES * PARTIALS)
    private val increment = FloatArray(VOICES * PARTIALS)
    private val amp = FloatArray(VOICES * PARTIALS)
    private val decay = FloatArray(VOICES * PARTIALS)
    private val attack = IntArray(VOICES)
    private val voiceActive = BooleanArray(VOICES)
    private var nextStrike = (SR * 0.3f).toInt()
    private var graceStrikes = 0
    private var lastNote = -1

    private val combs = intArrayOf(1499, 1889, 2311).map { FloatArray(it) }
    private val combPos = IntArray(combs.size)
    private val combDamp = FloatArray(combs.size)
    private val allpass = FloatArray(347)
    private var allpassPos = 0

    private val pink = PinkNoise(rng)
    private val breezeTone = OnePole(lowpassCoef(700f, SR))
    private val breezeLevel = Drift(rng, SR, 0.3f, 1f, 2f, 6f, 0.3f)

    override fun render(buffer: FloatArray, count: Int) {
        for (i in 0 until count) {
            if (--nextStrike <= 0) {
                strike()
                nextStrike = if (graceStrikes > 0) {
                    graceStrikes--
                    (rng.range(0.08f, 0.25f) * SR).toInt()
                } else {
                    if (rng.chance(0.3f)) graceStrikes = 1 + rng.nextInt(2)
                    (rng.range(0.8f, 2.5f) * SR).toInt()
                }
            }

            var dry = 0f
            for (v in 0 until VOICES) {
                if (!voiceActive[v]) continue
                val base = v * PARTIALS
                var sum = 0f
                for (p in base until base + PARTIALS) {
                    val a = amp[p]
                    if (a == 0f) continue
                    var ph = phase[p] + increment[p]
                    if (ph >= 1f) ph -= 1f
                    phase[p] = ph
                    sum += Sine.of(ph) * a
                    amp[p] = a * decay[p]
                }
                if (attack[v] < ATTACK) {
                    sum *= attack[v].toFloat() / ATTACK
                    attack[v]++
                }
                dry += sum
                if (amp[base] < 1e-4f) voiceActive[v] = false
            }

            val wet = reverb(dry)
            val breeze = breezeTone.process(pink.next()) * 0.09f * breezeLevel.next()
            buffer[i] = softLimit(dry + wet * 0.35f + breeze)
        }
    }

    private fun strike() {
        val v = (0 until VOICES).firstOrNull { !voiceActive[it] }
            ?: (0 until VOICES).minBy { amp[it * PARTIALS] }
        var note = rng.nextInt(SCALE.size)
        if (note == lastNote) note = (note + 1 + rng.nextInt(SCALE.size - 1)) % SCALE.size
        lastNote = note
        val hz = SCALE[note]
        val velocity = rng.range(0.35f, 1f)
        // Higher notes ring shorter.
        val tau = rng.range(0.8f, 1.4f) * (SCALE[0] / hz).pow(0.3f)
        for (k in 0 until PARTIALS) {
            val p = v * PARTIALS + k
            val partialHz = hz * RATIOS[k] * (1f + rng.range(-0.003f, 0.003f))
            phase[p] = rng.nextFloat()
            increment[p] = partialHz / SR
            amp[p] = if (partialHz < 13_000f) velocity * LEVELS[k] * VOICE_GAIN else 0f
            decay[p] = exp(-1f / (tau * DECAYS[k] * SR))
        }
        attack[v] = 0
        voiceActive[v] = true
    }

    private fun reverb(x: Float): Float {
        var sum = 0f
        for (c in combs.indices) {
            val line = combs[c]
            val pos = combPos[c]
            val out = line[pos]
            combDamp[c] += 0.35f * (out - combDamp[c])
            line[pos] = x + combDamp[c] * 0.6f
            combPos[c] = if (pos + 1 == line.size) 0 else pos + 1
            sum += out
        }
        sum /= combs.size
        val delayed = allpass[allpassPos]
        val y = delayed - 0.5f * sum
        allpass[allpassPos] = sum + 0.5f * y
        allpassPos = if (allpassPos + 1 == allpass.size) 0 else allpassPos + 1
        return y
    }

    private companion object {
        const val VOICES = 10
        const val PARTIALS = 5
        const val ATTACK = 48
        const val VOICE_GAIN = 0.28f
        val RATIOS = floatArrayOf(1f, 2f, 2.76f, 5.4f, 8.93f)
        val LEVELS = floatArrayOf(1f, 0.5f, 0.35f, 0.18f, 0.1f)
        val DECAYS = floatArrayOf(1f, 0.75f, 0.6f, 0.4f, 0.25f)
        // C5 D5 E5 G5 A5 C6 D6 E6 G6 A6
        val SCALE = floatArrayOf(523.25f, 587.33f, 659.25f, 783.99f, 880f, 1046.5f, 1174.66f, 1318.51f, 1567.98f, 1760f)
    }
}

// ---------------------------------------------------------------------------------------------
// Birdsong: a few independent singers of different "species" over a soft forest ambience
// ---------------------------------------------------------------------------------------------

private enum class Pattern { WARBLE, TEE_CHA, TRILL, WHISTLE, CHIPS }

private class Species(
    val pattern: Pattern,
    val noteMin: Float, val noteMax: Float,
    val fLow: Float, val fHigh: Float,
    val sweep: Float,
    val notesMin: Int, val notesMax: Int,
    val gapMin: Float, val gapMax: Float,
    val vibRate: Float, val vibDepth: Float,
    val harmonic: Float,
)

private val SPECIES = listOf(
    Species(Pattern.WARBLE, 0.06f, 0.14f, 2_200f, 4_500f, 1_200f, 4, 8, 0.03f, 0.09f, 0f, 0f, 0.12f),
    Species(Pattern.TEE_CHA, 0.08f, 0.11f, 3_200f, 5_500f, 700f, 4, 6, 0.05f, 0.08f, 0f, 0f, 0.05f),
    Species(Pattern.TRILL, 0.022f, 0.03f, 3_500f, 4_100f, 150f, 12, 28, 0.02f, 0.035f, 45f, 300f, 0f),
    Species(Pattern.WHISTLE, 0.25f, 0.45f, 2_400f, 3_200f, 300f, 2, 3, 0.12f, 0.25f, 25f, 60f, 0.03f),
    Species(Pattern.CHIPS, 0.018f, 0.03f, 3_000f, 6_000f, 3_000f, 3, 5, 0.06f, 0.12f, 0f, 0f, 0f),
)

private class Singer(private val rng: Rng, private val species: Species, private val loudness: Float) {
    private var resting = true
    private var countdown = (rng.range(0.2f, 2.5f) * SR).toInt()
    private var notesLeft = 0
    private var noteIndex = 0
    private var inNote = false
    private var pos = 0
    private var len = 1
    private var f0 = 0f
    private var f1 = 0f
    private var shape = 0
    private var phrasePitch = 1f
    private var trillBase = 0f
    private var phase = 0f
    private var vibPhase = 0f

    fun next(): Float {
        if (!inNote) {
            if (--countdown > 0) return 0f
            if (resting) startPhrase()
            if (notesLeft <= 0) {
                resting = true
                countdown = (rng.range(0.4f, 3f) * SR).toInt()
                return 0f
            }
            startNote()
        }
        val t = pos.toFloat() / len
        val glide = when (shape) {
            0 -> t
            1 -> t * t
            2 -> Sine.of(t * 0.5f) // arch: f0 → f1 → f0
            else -> 1f - (1f - t) * (1f - t)
        }
        var hz = f0 + (f1 - f0) * glide
        if (species.vibDepth > 0f) {
            vibPhase += species.vibRate / SR
            if (vibPhase >= 1f) vibPhase -= 1f
            hz += species.vibDepth * Sine.of(vibPhase)
        }
        phase += hz / SR
        if (phase >= 1f) phase -= 1f
        val env = Sine.of(t * 0.5f).let { it * it } // sin²(πt): click-free
        var s = Sine.of(phase)
        if (species.harmonic > 0f) {
            val p2 = phase * 2f
            s += species.harmonic * Sine.of(if (p2 >= 1f) p2 - 1f else p2)
        }
        if (++pos >= len) {
            inNote = false
            notesLeft--
            countdown = (rng.range(species.gapMin, species.gapMax) * SR).toInt()
        }
        return s * env * loudness
    }

    private fun startPhrase() {
        resting = false
        notesLeft = species.notesMin + rng.nextInt(species.notesMax - species.notesMin + 1)
        noteIndex = 0
        phrasePitch = rng.range(0.94f, 1.06f)
        trillBase = rng.range(species.fLow, species.fHigh)
    }

    private fun startNote() {
        inNote = true
        pos = 0
        len = (rng.range(species.noteMin, species.noteMax) * SR).toInt().coerceAtLeast(16)
        shape = rng.nextInt(4)
        val s = species
        when (s.pattern) {
            Pattern.WARBLE -> {
                f0 = rng.range(s.fLow, s.fHigh)
                f1 = (f0 + rng.range(-s.sweep, s.sweep)).coerceIn(s.fLow * 0.8f, s.fHigh * 1.15f)
            }
            Pattern.TEE_CHA -> if (noteIndex % 2 == 0) {
                f0 = s.fHigh
                f1 = s.fHigh - s.sweep
                shape = 3
            } else {
                f0 = s.fLow
                f1 = s.fLow * 1.06f
                shape = 0
            }
            Pattern.TRILL -> {
                f0 = trillBase * rng.range(0.98f, 1.02f)
                f1 = f0 + rng.range(-s.sweep, s.sweep)
            }
            Pattern.WHISTLE -> {
                f0 = rng.range(s.fLow, s.fHigh)
                f1 = f0 * rng.range(0.9f, 1.12f)
                shape = 3
            }
            Pattern.CHIPS -> {
                f0 = s.fHigh * rng.range(0.9f, 1f)
                f1 = s.fLow * rng.range(1f, 1.1f)
                shape = 3
            }
        }
        f0 *= phrasePitch
        f1 *= phrasePitch
        noteIndex++
    }
}

private class BirdsGenerator(private val rng: Rng) : SoundGenerator {
    private val singers: Array<Singer>
    private val pink = PinkNoise(rng)
    private val forestTone = OnePole(lowpassCoef(1_500f, SR))
    private val forestCut = HighPass(250f, SR)
    private val leaves = Drift(rng, SR, 0.4f, 1f, 2f, 6f, 0.3f)

    init {
        // Three distinct species nearby plus one distant bird.
        val order = SPECIES.indices.shuffledWith(rng)
        singers = arrayOf(
            Singer(rng, SPECIES[order[0]], rng.range(0.26f, 0.34f)),
            Singer(rng, SPECIES[order[1]], rng.range(0.19f, 0.27f)),
            Singer(rng, SPECIES[order[2]], rng.range(0.14f, 0.22f)),
            Singer(rng, SPECIES[order[3]], rng.range(0.06f, 0.09f)),
        )
    }

    override fun render(buffer: FloatArray, count: Int) {
        for (i in 0 until count) {
            var out = forestCut.process(forestTone.process(pink.next())) * 0.16f * leaves.next()
            for (singer in singers) out += singer.next()
            buffer[i] = softLimit(out)
        }
    }
}

private fun IntRange.shuffledWith(rng: Rng): IntArray {
    val a = toList().toIntArray()
    for (i in a.size - 1 downTo 1) {
        val j = rng.nextInt(i + 1)
        val tmp = a[i]; a[i] = a[j]; a[j] = tmp
    }
    return a
}
