package com.karelisio.somna.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import com.karelisio.somna.R
import java.lang.ref.SoftReference
import java.nio.ByteOrder

/**
 * Real field recordings (CC0, from Freesound — see README) played instead of synthesis.
 * They are decoded once to PCM in memory, then looped seamlessly by [LoopGenerator].
 */
object Recordings {
    private const val TAG = "Recordings"
    private const val TIMEOUT_US = 10_000L
    private const val MAX_DECODE_MS = 15_000L

    fun resource(type: SoundType): Int? = when (type) {
        SoundType.FIRE -> R.raw.ambience_fire
        SoundType.RAIN -> R.raw.ambience_rain
        SoundType.WAVES -> R.raw.ambience_waves
        SoundType.BIRDS -> R.raw.ambience_birds
        else -> null
    }

    /** Keeps the last decoded clip while memory allows, so previews restart instantly. */
    private var cache: Pair<Int, SoftReference<Recording>>? = null

    /** Seamless endless generator for [type], or null when it has no recording or it can't be decoded. */
    fun generator(context: Context, type: SoundType): SoundGenerator? {
        val res = resource(type) ?: return null
        return runCatching {
            LoopGenerator(load(context, res), Rng(System.nanoTime()))
        }.onFailure { Log.w(TAG, "Recording unavailable for $type, using synthesis", it) }.getOrNull()
    }

    private fun load(context: Context, res: Int): Recording {
        synchronized(this) { cache?.takeIf { it.first == res }?.second?.get()?.let { return it } }
        val recording = decode(context, res)
        synchronized(this) { cache = res to SoftReference(recording) }
        return recording
    }

    private fun decode(context: Context, res: Int): Recording {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            context.resources.openRawResourceFd(res).use { afd ->
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 60_000_000L
            var pcm = ShortArray(((durationUs / 1_000_000.0 + 1) * sampleRate * channels).toInt())
            var size = 0

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + MAX_DECODE_MS
            var inputDone = false
            while (true) {
                check(SystemClock.elapsedRealtime() < deadline) { "Decoding timed out" }
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val read = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                        if (read < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val output = codec.outputFormat
                    sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset).limit(info.offset + info.size)
                    val count = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) info.size / 4 else info.size / 2
                    if (size + count > pcm.size) pcm = pcm.copyOf(maxOf(size + count, pcm.size + pcm.size / 4))
                    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        val floats = buffer.asFloatBuffer()
                        repeat(count) { pcm[size++] = (floats.get().coerceIn(-1f, 1f) * 32_767f).toInt().toShort() }
                    } else {
                        buffer.asShortBuffer().get(pcm, size, count)
                        size += count
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            check(channels in 1..2) { "Unsupported channel count $channels" }
            return Recording(pcm, channels, sampleRate, size)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
