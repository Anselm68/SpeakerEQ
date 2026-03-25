package com.speakereq.app.audio

import kotlin.math.abs

/**
 * Calculates the correction curve to linearize a speaker's frequency response.
 * The correction is the inverse of the measured response, clamped to safe limits.
 */
class CorrectionCalculator {

    /**
     * Calculate correction curve from measured frequency response.
     *
     * @param measuredLevels Normalized band levels in dB (31 bands, relative to average)
     * @param maxCorrection Maximum correction in dB (to avoid overdriving)
     * @return Correction curve in dB (31 bands). Positive = boost, Negative = cut.
     */
    fun calculateCorrection(
        measuredLevels: FloatArray,
        maxCorrection: Float = MAX_CORRECTION_DB
    ): FloatArray {
        require(measuredLevels.size == FrequencyAnalyzer.BAND_COUNT) {
            "Expected ${FrequencyAnalyzer.BAND_COUNT} bands, got ${measuredLevels.size}"
        }

        return FloatArray(measuredLevels.size) { i ->
            // Correction = negative of measured deviation from flat
            val correction = -measuredLevels[i]

            // Clamp to safe range
            correction.coerceIn(-maxCorrection, maxCorrection)
        }
    }

    /**
     * Apply smoothing to the correction curve to avoid harsh transitions.
     * Uses a simple 3-point moving average.
     */
    fun smoothCorrection(correction: FloatArray): FloatArray {
        val smoothed = FloatArray(correction.size)
        for (i in correction.indices) {
            val prev = if (i > 0) correction[i - 1] else correction[i]
            val next = if (i < correction.size - 1) correction[i + 1] else correction[i]
            smoothed[i] = (prev + correction[i] + next) / 3f
        }
        return smoothed
    }

    /**
     * Calculate what the corrected response would look like.
     * This is measured + correction, should ideally be flat (0 dB everywhere).
     */
    fun calculateCorrectedResponse(
        measuredLevels: FloatArray,
        correction: FloatArray
    ): FloatArray {
        require(measuredLevels.size == correction.size) {
            "Arrays must have same size"
        }
        return FloatArray(measuredLevels.size) { i ->
            measuredLevels[i] + correction[i]
        }
    }

    /**
     * Calculate the overall flatness score of a response.
     * Lower is better. 0 = perfectly flat.
     * Returns the standard deviation of the band levels.
     */
    fun calculateFlatnessScore(levels: FloatArray): Float {
        val validLevels = levels.filter { abs(it) < 100f }
        if (validLevels.isEmpty()) return Float.MAX_VALUE

        val mean = validLevels.average().toFloat()
        val variance = validLevels.map { (it - mean) * (it - mean) }.average().toFloat()
        return kotlin.math.sqrt(variance)
    }

    companion object {
        const val MAX_CORRECTION_DB = 12f
    }
}
