package com.speakereq.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

/**
 * Generates test signals (sine sweep or pink noise) for frequency response measurement.
 */
class SignalGenerator(
    private val sampleRate: Int = SAMPLE_RATE
) {

    enum class SignalType { SINE_SWEEP, PINK_NOISE }

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false

    /**
     * Generate a sine sweep signal from startFreq to endFreq over the given duration.
     * Uses logarithmic frequency sweep for equal energy per octave.
     */
    fun generateSineSweep(
        startFreq: Double = 20.0,
        endFreq: Double = 20000.0,
        durationSeconds: Double = 5.0
    ): ShortArray {
        val numSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        // Logarithmic sweep: f(t) = f0 * (f1/f0)^(t/T)
        val sweepRate = ln(endFreq / startFreq) / durationSeconds
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val instantFreq = startFreq * exp(sweepRate * t)
            phase += 2.0 * PI * instantFreq / sampleRate

            // Apply fade-in and fade-out (50ms each) to avoid clicks
            val fadeIn = min(1.0, t / 0.05)
            val fadeOut = min(1.0, (durationSeconds - t) / 0.05)
            val amplitude = AMPLITUDE * fadeIn * fadeOut

            samples[i] = (amplitude * sin(phase)).toInt().toShort()
        }

        return samples
    }

    /**
     * Generate pink noise using the Voss-McCartney algorithm.
     * Pink noise has equal energy per octave, useful for speaker measurement.
     */
    fun generatePinkNoise(durationSeconds: Double = 10.0): ShortArray {
        val numSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        // Voss-McCartney algorithm with 16 rows
        val numRows = 16
        val rowValues = DoubleArray(numRows)
        var runningSum = 0.0
        val random = java.util.Random(42) // Deterministic for reproducibility

        // Initialize
        for (i in 0 until numRows) {
            rowValues[i] = random.nextGaussian()
            runningSum += rowValues[i]
        }

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            // Find which row to update (based on trailing zeros in binary)
            val rowToUpdate = Integer.numberOfTrailingZeros(i + 1).coerceAtMost(numRows - 1)
            runningSum -= rowValues[rowToUpdate]
            rowValues[rowToUpdate] = random.nextGaussian()
            runningSum += rowValues[rowToUpdate]

            // Normalize and add white noise for high frequencies
            val pinkValue = (runningSum + random.nextGaussian()) / (numRows.toDouble() / 2.0)

            // Apply fade-in and fade-out
            val fadeIn = min(1.0, t / 0.05)
            val fadeOut = min(1.0, (durationSeconds - t) / 0.05)
            val amplitude = AMPLITUDE * fadeIn * fadeOut

            samples[i] = (amplitude * pinkValue).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        return samples
    }

    /**
     * Play the generated signal through the speaker.
     * @return The generated samples for reference.
     */
    fun play(signalType: SignalType, onProgress: ((Int) -> Unit)? = null): ShortArray {
        val samples = when (signalType) {
            SignalType.SINE_SWEEP -> generateSineSweep()
            SignalType.PINK_NOISE -> generatePinkNoise()
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize.coerceAtLeast(samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.let { track ->
            track.write(samples, 0, samples.size)
            isPlaying = true
            track.play()

            // Monitor playback progress
            Thread {
                while (isPlaying && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val head = track.playbackHeadPosition
                    val progress = (head * 100L / samples.size).toInt().coerceIn(0, 100)
                    onProgress?.invoke(progress)
                    if (head >= samples.size) break
                    Thread.sleep(50)
                }
                isPlaying = false
                onProgress?.invoke(100)
            }.start()
        }

        return samples
    }

    fun stop() {
        isPlaying = false
        audioTrack?.apply {
            try {
                stop()
                release()
            } catch (_: Exception) { }
        }
        audioTrack = null
    }

    val isCurrentlyPlaying: Boolean get() = isPlaying

    companion object {
        const val SAMPLE_RATE = 44100
        const val AMPLITUDE = 28000.0 // ~85% of Short.MAX_VALUE
    }
}
