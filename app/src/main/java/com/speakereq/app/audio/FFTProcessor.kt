package com.speakereq.app.audio

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import kotlin.math.*

/**
 * Performs FFT analysis on audio samples to extract frequency spectrum.
 */
class FFTProcessor(
    private val fftSize: Int = FFT_SIZE,
    private val sampleRate: Int = SignalGenerator.SAMPLE_RATE
) {
    private val transformer = FastFourierTransformer(DftNormalization.STANDARD)

    /**
     * Compute the magnitude spectrum of the given samples.
     * Returns array of [fftSize/2] magnitudes in dB, one per frequency bin.
     *
     * @param samples The audio samples to analyze
     * @return MagnitudeSpectrum with frequency resolution info
     */
    fun computeSpectrum(samples: ShortArray): MagnitudeSpectrum {
        // Average multiple overlapping frames for a more stable result
        val frameCount = maxOf(1, (samples.size - fftSize) / (fftSize / 2))
        val avgMagnitudes = DoubleArray(fftSize / 2)

        for (frame in 0 until frameCount) {
            val offset = frame * (fftSize / 2)
            if (offset + fftSize > samples.size) break

            // Extract frame and apply Hann window
            val windowedFrame = DoubleArray(fftSize)
            for (i in 0 until fftSize) {
                val window = 0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
                windowedFrame[i] = samples[offset + i].toDouble() * window
            }

            // Perform FFT
            val fftResult: Array<Complex> = transformer.transform(windowedFrame, TransformType.FORWARD)

            // Accumulate magnitudes (only first half - Nyquist)
            for (i in 0 until fftSize / 2) {
                avgMagnitudes[i] += fftResult[i].abs()
            }
        }

        // Average and convert to dB
        val effectiveFrames = maxOf(1, frameCount)
        val magnitudesDb = DoubleArray(fftSize / 2) { i ->
            val avgMag = avgMagnitudes[i] / effectiveFrames
            if (avgMag > 0) 20.0 * log10(avgMag) else -120.0
        }

        return MagnitudeSpectrum(
            magnitudesDb = magnitudesDb,
            frequencyResolution = sampleRate.toDouble() / fftSize,
            sampleRate = sampleRate,
            fftSize = fftSize
        )
    }

    /**
     * Get the frequency corresponding to a given FFT bin index.
     */
    fun binToFrequency(binIndex: Int): Double {
        return binIndex.toDouble() * sampleRate / fftSize
    }

    data class MagnitudeSpectrum(
        val magnitudesDb: DoubleArray,
        val frequencyResolution: Double,
        val sampleRate: Int,
        val fftSize: Int
    ) {
        /**
         * Get the magnitude at a specific frequency (interpolated).
         */
        fun getMagnitudeAtFrequency(frequency: Double): Double {
            val binIndex = frequency / frequencyResolution
            val lowerBin = binIndex.toInt().coerceIn(0, magnitudesDb.size - 2)
            val fraction = binIndex - lowerBin
            return magnitudesDb[lowerBin] * (1 - fraction) + magnitudesDb[lowerBin + 1] * fraction
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MagnitudeSpectrum) return false
            return magnitudesDb.contentEquals(other.magnitudesDb) &&
                    frequencyResolution == other.frequencyResolution
        }

        override fun hashCode(): Int = magnitudesDb.contentHashCode()
    }

    companion object {
        const val FFT_SIZE = 4096 // ~10 Hz resolution at 44.1kHz
    }
}
