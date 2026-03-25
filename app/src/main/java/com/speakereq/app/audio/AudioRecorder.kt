package com.speakereq.app.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.speakereq.app.SpeakerEqApp

/**
 * Records audio from the microphone for frequency response analysis.
 */
class AudioRecorder(
    private val sampleRate: Int = SignalGenerator.SAMPLE_RATE
) {
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate * 2) // At least 1 second buffer

    /**
     * Erstellt einen AudioRecord mit UNPROCESSED-Quelle (kein AGC, kein Noise-Cancelling)
     * auf API 24+. Falls nicht unterstützt, Fallback auf MIC.
     */
    private fun createAudioRecord(): AudioRecord {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val ar = AudioRecord(
                MediaRecorder.AudioSource.UNPROCESSED,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize
            )
            if (ar.state == AudioRecord.STATE_INITIALIZED) return ar
            ar.release()
        }
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )
    }

    /**
     * Start recording and return samples after the specified duration.
     * Must be called with RECORD_AUDIO permission granted.
     */
    fun record(durationSeconds: Double): ShortArray {
        if (ContextCompat.checkSelfPermission(
                SpeakerEqApp.instance,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        val totalSamples = (sampleRate * durationSeconds).toInt()
        val allSamples = ShortArray(totalSamples)
        var samplesRead = 0

        audioRecord = createAudioRecord()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        isRecording = true
        audioRecord?.startRecording()

        val readBuffer = ShortArray(bufferSize / 2)

        while (isRecording && samplesRead < totalSamples) {
            val toRead = minOf(readBuffer.size, totalSamples - samplesRead)
            val read = audioRecord?.read(readBuffer, 0, toRead) ?: -1

            if (read > 0) {
                System.arraycopy(readBuffer, 0, allSamples, samplesRead, read)
                samplesRead += read
            } else if (read < 0) {
                break
            }
        }

        stop()

        // Return only the actually recorded samples
        return if (samplesRead < totalSamples) {
            allSamples.copyOfRange(0, samplesRead)
        } else {
            allSamples
        }
    }

    /**
     * Continuously streams microphone peak level (0.0–1.0) via [onLevel] callback.
     * Return false from [onLevel] to stop streaming.
     */
    fun streamLevel(onLevel: (Float) -> Boolean) {
        if (ContextCompat.checkSelfPermission(
                SpeakerEqApp.instance,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        val ar = createAudioRecord()

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        audioRecord = ar
        isRecording = true
        ar.startRecording()

        val buf = ShortArray(bufferSize / 2)
        while (isRecording) {
            val read = ar.read(buf, 0, buf.size)
            if (read > 0) {
                var peak = 0
                for (i in 0 until read) {
                    val abs = kotlin.math.abs(buf[i].toInt())
                    if (abs > peak) peak = abs
                }
                val continueStreaming = onLevel(peak.toFloat() / 32767f)
                if (!continueStreaming) break
            }
        }

        ar.apply {
            try { stop(); release() } catch (_: Exception) {}
        }
        audioRecord = null
        isRecording = false
    }

    fun stop() {
        isRecording = false
        audioRecord?.apply {
            try {
                stop()
                release()
            } catch (_: Exception) { }
        }
        audioRecord = null
    }

    val isCurrentlyRecording: Boolean get() = isRecording
}
