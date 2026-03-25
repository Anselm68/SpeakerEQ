package com.speakereq.app.eq

import android.media.audiofx.Equalizer
import android.util.Log
import com.speakereq.app.audio.FrequencyAnalyzer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Wraps Android's AudioEffect Equalizer to apply system-wide frequency correction.
 * Attaches to audio session 0 (global output mix) for system-wide effect.
 */
class SystemEqualizer {

    private var equalizer: Equalizer? = null
    private var isInitialized = false

    /**
     * Number of bands available on this device's EQ hardware.
     */
    var numberOfBands: Short = 0
        private set

    /**
     * The frequency range of each EQ band [lowerFreq, upperFreq] in millihertz.
     */
    var bandFrequencyRanges: Array<IntArray> = emptyArray()
        private set

    /**
     * The level range [min, max] in millibels.
     */
    var levelRange: ShortArray = shortArrayOf(-1500, 1500) // ±15 dB default
        private set

    /**
     * Initialize the equalizer on the global audio session.
     * @return true if initialization was successful
     */
    fun initialize(): Boolean {
        return try {
            // Session 0 = global output mix
            equalizer = Equalizer(0, 0).also { eq ->
                numberOfBands = eq.numberOfBands
                levelRange = eq.bandLevelRange

                bandFrequencyRanges = Array(numberOfBands.toInt()) { band ->
                    eq.getBandFreqRange(band.toShort())
                }

                Log.i(TAG, "Equalizer initialized: $numberOfBands bands")
                for (i in 0 until numberOfBands) {
                    val range = eq.getBandFreqRange(i.toShort())
                    val center = eq.getCenterFreq(i.toShort())
                    Log.i(TAG, "Band $i: ${range[0]/1000}Hz - ${range[1]/1000}Hz (center: ${center/1000}Hz)")
                }
            }
            isInitialized = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize equalizer", e)
            isInitialized = false
            false
        }
    }

    /**
     * Apply a 31-band correction curve by mapping it to the available EQ bands.
     *
     * @param correction31 The 31-band correction curve in dB
     */
    fun applyCorrection(correction31: FloatArray) {
        val eq = equalizer ?: return
        if (!isInitialized) return

        require(correction31.size == FrequencyAnalyzer.BAND_COUNT) {
            "Expected 31-band correction curve"
        }

        for (band in 0 until numberOfBands) {
            val centerFreqHz = eq.getCenterFreq(band.toShort()) / 1000.0 // milliHz to Hz

            // Find the closest 1/3 octave band
            val closestBandIndex = findClosestBand(centerFreqHz)

            // Interpolate between nearby bands for smoother correction
            val correctionDb = interpolateCorrection(correction31, centerFreqHz)

            // Convert dB to millibels and clamp to device range
            val correctionMb = (correctionDb * 100).roundToInt().toShort()
            val clampedMb = correctionMb.coerceIn(levelRange[0], levelRange[1])

            eq.setBandLevel(band.toShort(), clampedMb)
            Log.d(TAG, "Band $band (${centerFreqHz.toInt()}Hz): ${clampedMb/100.0}dB")
        }
    }

    /**
     * Interpolate the 31-band correction curve at a specific frequency.
     */
    private fun interpolateCorrection(correction31: FloatArray, frequencyHz: Double): Float {
        val frequencies = FrequencyAnalyzer.BAND_CENTER_FREQUENCIES

        // Find surrounding bands
        var lowerIdx = 0
        for (i in frequencies.indices) {
            if (frequencies[i] <= frequencyHz) lowerIdx = i
        }
        val upperIdx = (lowerIdx + 1).coerceAtMost(frequencies.size - 1)

        if (lowerIdx == upperIdx) return correction31[lowerIdx]

        // Logarithmic interpolation
        val logFreq = Math.log10(frequencyHz)
        val logLower = Math.log10(frequencies[lowerIdx])
        val logUpper = Math.log10(frequencies[upperIdx])

        val fraction = if (logUpper > logLower) {
            ((logFreq - logLower) / (logUpper - logLower)).coerceIn(0.0, 1.0)
        } else 0.0

        return (correction31[lowerIdx] * (1 - fraction) + correction31[upperIdx] * fraction).toFloat()
    }

    private fun findClosestBand(frequencyHz: Double): Int {
        var closestIdx = 0
        var closestDist = Double.MAX_VALUE

        for (i in FrequencyAnalyzer.BAND_CENTER_FREQUENCIES.indices) {
            val dist = abs(
                Math.log10(frequencyHz) - Math.log10(FrequencyAnalyzer.BAND_CENTER_FREQUENCIES[i])
            )
            if (dist < closestDist) {
                closestDist = dist
                closestIdx = i
            }
        }
        return closestIdx
    }

    /**
     * Enable or disable the equalizer effect.
     */
    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
    }

    val isEnabled: Boolean get() = equalizer?.enabled ?: false

    /**
     * Reset all bands to 0 (flat).
     */
    fun reset() {
        val eq = equalizer ?: return
        for (band in 0 until numberOfBands) {
            eq.setBandLevel(band.toShort(), 0)
        }
    }

    /**
     * Release the equalizer resources.
     */
    fun release() {
        equalizer?.apply {
            enabled = false
            release()
        }
        equalizer = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "SystemEqualizer"
    }
}
