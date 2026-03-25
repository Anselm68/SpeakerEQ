package com.speakereq.app.audio

import kotlin.math.*

/**
 * Analyzes FFT results and groups them into 1/3 octave bands (31 bands).
 */
class FrequencyAnalyzer {

    /**
     * Group FFT spectrum into 31 third-octave bands.
     * Returns array of 31 dB values, one per band.
     */
    fun analyzeThirdOctaveBands(spectrum: FFTProcessor.MagnitudeSpectrum): FloatArray {
        val bandLevels = FloatArray(BAND_COUNT)

        for (i in BAND_CENTER_FREQUENCIES.indices) {
            val centerFreq = BAND_CENTER_FREQUENCIES[i]

            // 1/3 octave band edges
            val lowerFreq = centerFreq / THIRD_OCTAVE_FACTOR
            val upperFreq = centerFreq * THIRD_OCTAVE_FACTOR

            // Skip bands outside Nyquist
            if (lowerFreq >= spectrum.sampleRate / 2.0) {
                bandLevels[i] = -120f
                continue
            }

            // Average all FFT bins within this band
            val lowerBin = maxOf(1, (lowerFreq / spectrum.frequencyResolution).toInt())
            val upperBin = minOf(
                spectrum.magnitudesDb.size - 1,
                (upperFreq / spectrum.frequencyResolution).toInt()
            )

            if (lowerBin >= upperBin) {
                // Single bin or no bins in range - interpolate
                bandLevels[i] = spectrum.getMagnitudeAtFrequency(centerFreq).toFloat()
            } else {
                // RMS average of bins in the band (energy average in dB domain)
                var sumLinear = 0.0
                var count = 0
                for (bin in lowerBin..upperBin) {
                    if (bin < spectrum.magnitudesDb.size) {
                        sumLinear += 10.0.pow(spectrum.magnitudesDb[bin] / 20.0)
                        count++
                    }
                }
                bandLevels[i] = if (count > 0) {
                    (20.0 * log10(sumLinear / count)).toFloat()
                } else {
                    -120f
                }
            }
        }

        return bandLevels
    }

    /**
     * Normalize band levels relative to the average level.
     * Makes the response relative, so we see deviations from flat.
     */
    fun normalizeLevels(bandLevels: FloatArray): FloatArray {
        val validLevels = bandLevels.filter { it > -100f }
        if (validLevels.isEmpty()) return bandLevels

        val avgLevel = validLevels.average().toFloat()
        return FloatArray(bandLevels.size) { i ->
            if (bandLevels[i] > -100f) bandLevels[i] - avgLevel else -60f
        }
    }

    companion object {
        const val BAND_COUNT = 31

        // Factor for 1/3 octave band edges: 2^(1/6)
        val THIRD_OCTAVE_FACTOR = 2.0.pow(1.0 / 6.0)

        // ISO 31 third-octave center frequencies (20 Hz to 20 kHz)
        val BAND_CENTER_FREQUENCIES = doubleArrayOf(
            20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0,
            125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
            800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0,
            5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0
        )

        // Labels for display
        val BAND_LABELS = arrayOf(
            "20", "25", "31.5", "40", "50", "63", "80", "100",
            "125", "160", "200", "250", "315", "400", "500", "630",
            "800", "1k", "1.25k", "1.6k", "2k", "2.5k", "3.15k", "4k",
            "5k", "6.3k", "8k", "10k", "12.5k", "16k", "20k"
        )
    }
}
