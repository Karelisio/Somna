package com.karelisio.somna.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.karelisio.somna.audio.SoundType
import com.karelisio.somna.audio.SynthPlayer
import com.karelisio.somna.data.SYSTEM_SOUND
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Short preview (with fades) of an alarm sound, stopped automatically. */
class SoundPreview(private val context: Context, private val scope: CoroutineScope) {
    var playing by mutableStateOf<String?>(null)
        private set
    private var synth: SynthPlayer? = null
    private var media: MediaPlayer? = null
    private var job: Job? = null

    fun toggle(sound: String) {
        if (playing == sound) stop() else play(sound)
    }

    fun play(sound: String, seconds: Int = 7) {
        stop()
        playing = sound
        if (sound == SYSTEM_SOUND) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            media = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    setDataSource(context, uri)
                    setVolume(0.6f, 0.6f)
                    prepare()
                    start()
                }
            }.getOrNull()
        } else {
            val type = runCatching { SoundType.valueOf(sound) }.getOrDefault(SoundType.BIRDS)
            synth = SynthPlayer(context, type, AudioAttributes.USAGE_MEDIA).apply {
                setVolume(0f)
                start()
            }
        }
        job = scope.launch {
            val steps = seconds * 20
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val v = 0.7f * minOf(1f, t * 6f, (1f - t) * 5f)
                synth?.setVolume(v)
                delay(50)
            }
            stop()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        synth?.stop()
        synth = null
        media?.runCatching { stop(); release() }
        media = null
        playing = null
    }
}

@Composable
fun rememberSoundPreview(): SoundPreview {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = remember { SoundPreview(context.applicationContext, scope) }
    DisposableEffect(preview) { onDispose { preview.stop() } }
    return preview
}
