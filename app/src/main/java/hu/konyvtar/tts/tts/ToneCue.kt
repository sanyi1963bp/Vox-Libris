package hu.konyvtar.tts.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Rövid, kellemes hangjelzések a felolvasás tagolásához:
 * halk "csilingelés" minden bekezdés előtt, mélyebb kettős hang minden
 * fejezet előtt. A hangot menet közben állítjuk elő (nincs hangfájl).
 */
object ToneCue {

    private const val SAMPLE_RATE = 22050

    /** A bekezdésjelzés hossza ezredmásodpercben. */
    const val PARAGRAPH_MS = 130

    /** A fejezetjelzés hossza ezredmásodpercben. */
    const val CHAPTER_MS = 520

    /** Halk, magas jelzés — bekezdés eleje. */
    fun paragraph(volume: Float) {
        play(listOf(Tone(784.0, 130, volume * 0.5f)))
    }

    /** Mélyebb, kettős, ereszkedő jelzés — fejezet eleje. */
    fun chapter(volume: Float) {
        play(
            listOf(
                Tone(392.0, 240, volume * 0.65f),
                Tone(262.0, 280, volume * 0.65f)
            )
        )
    }

    private data class Tone(val freqHz: Double, val ms: Int, val gain: Float)

    private fun play(tones: List<Tone>) {
        val total = tones.sumOf { it.ms } * SAMPLE_RATE / 1000
        if (total <= 0) return
        val samples = ShortArray(total)
        var pos = 0
        for (t in tones) {
            val n = t.ms * SAMPLE_RATE / 1000
            // Lágy be- és kifutás, hogy ne pattanjon a hang
            val fadeIn = (n * 0.12).toInt().coerceAtLeast(1)
            val fadeOut = (n * 0.35).toInt().coerceAtLeast(1)
            for (i in 0 until n) {
                if (pos >= total) break
                val env = when {
                    i < fadeIn -> i.toFloat() / fadeIn
                    i > n - fadeOut -> (n - i).toFloat() / fadeOut
                    else -> 1f
                }
                val v = sin(2.0 * PI * t.freqHz * i / SAMPLE_RATE) * env * t.gain
                samples[pos++] = (v * Short.MAX_VALUE).toInt().toShort()
            }
        }

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            return
        }

        try {
            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) {
                        try {
                            t?.stop(); t?.release()
                        } catch (_: Exception) {
                        }
                    }

                    override fun onPeriodicNotification(t: AudioTrack?) {}
                }
            )
            track.play()
        } catch (e: Exception) {
            try {
                track.release()
            } catch (_: Exception) {
            }
        }
    }
}
