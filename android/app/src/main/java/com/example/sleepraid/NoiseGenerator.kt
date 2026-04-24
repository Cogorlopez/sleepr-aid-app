package com.example.sleepraid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.random.Random

class NoiseGenerator {

    enum class NoiseType { WHITE, PINK, BROWN }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_MULTIPLIER = 4

        // Perceptual loudness normalization. White noise has a flat spectrum so its
        // energy lands heavily in the 1–4kHz range where human hearing is most sensitive.
        // Pink rolls off 3dB/octave and brown 6dB/octave, so both need a gain boost to
        // sound equally loud at the same system volume. Tune these by ear.
        private const val WHITE_GAIN = 0.25f
        private const val PINK_GAIN  = 0.85f  // compensates for the 0.11 internal normalization
        private const val BROWN_GAIN = 0.95f
    }

    private var audioTrack: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile var noiseType: NoiseType = NoiseType.WHITE

    fun start(type: NoiseType = noiseType) {
        stop()
        this.noiseType = type

        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferBytes = minBytes * BUFFER_MULTIPLIER
        val bufferSamples = bufferBytes / Float.SIZE_BYTES

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }

        job = scope.launch {
            val buffer = FloatArray(bufferSamples)

            // Pink noise filter state (Paul Kellet algorithm)
            var b0 = 0f; var b1 = 0f; var b2 = 0f
            var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f

            // Brown noise accumulator
            var lastBrown = 0f

            while (isActive) {
                val currentType = noiseType

                for (i in buffer.indices) {
                    buffer[i] = when (currentType) {
                        NoiseType.WHITE -> {
                            (Random.nextFloat() * 2f - 1f) * WHITE_GAIN
                        }
                        NoiseType.PINK -> {
                            val w = Random.nextFloat() * 2f - 1f
                            b0 = 0.99886f * b0 + w * 0.0555179f
                            b1 = 0.99332f * b1 + w * 0.0750759f
                            b2 = 0.96900f * b2 + w * 0.1538520f
                            b3 = 0.86650f * b3 + w * 0.3104856f
                            b4 = 0.55000f * b4 + w * 0.5329522f
                            b5 = -0.7616f * b5 - w * 0.0168980f
                            val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.11f
                            b6 = w * 0.115926f
                            pink.coerceIn(-1f, 1f) * PINK_GAIN
                        }
                        NoiseType.BROWN -> {
                            val w = Random.nextFloat() * 2f - 1f
                            lastBrown = (lastBrown + w * 0.02f).coerceIn(-1f, 1f)
                            lastBrown * BROWN_GAIN
                        }
                    }
                }

                // WRITE_BLOCKING pauses this coroutine until the track consumes the buffer.
                // stop() calls audioTrack.stop() first, which causes this write to return
                // early so the while-loop can see isActive == false and exit cleanly.
                audioTrack?.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    fun stop() {
        // Stop the track first so any blocking write() returns immediately
        audioTrack?.stop()
        job?.cancel()
        job = null
        audioTrack?.release()
        audioTrack = null
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
