package com.speakereq.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.speakereq.app.audio.*
import com.speakereq.app.data.SpeakerProfile
import com.speakereq.app.databinding.ActivityMeasurementBinding
import com.speakereq.app.eq.EqService
import kotlinx.coroutines.*

class MeasurementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeasurementBinding

    private val signalGenerator = SignalGenerator()
    private val audioRecorder = AudioRecorder()
    private val fftProcessor = FFTProcessor()
    private val frequencyAnalyzer = FrequencyAnalyzer()
    private val correctionCalculator = CorrectionCalculator()

    private var measuredLevels: FloatArray? = null
    private var correctionCurve: FloatArray? = null
    private var measurementJob: Job? = null

    // Bluetooth-Gerätedaten aus dem Intent (übergeben von MainActivity)
    private val deviceName by lazy {
        intent.getStringExtra(EXTRA_DEVICE_NAME) ?: getString(R.string.unknown_speaker)
    }
    private val deviceAddress by lazy {
        intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: "00:00:00:00:00:00"
    }

    companion object {
        const val EXTRA_DEVICE_NAME = "DEVICE_NAME"
        const val EXTRA_DEVICE_ADDRESS = "DEVICE_ADDRESS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeasurementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.measurement_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnStartMeasurement.setOnClickListener {
            if (signalGenerator.isCurrentlyPlaying) {
                stopMeasurement()
            } else {
                startMeasurement()
            }
        }

        binding.btnApplyCorrection.setOnClickListener {
            showSaveProfileDialog()
        }
    }

    private fun startMeasurement() {
        binding.btnStartMeasurement.text = getString(R.string.btn_stop_measurement)
        binding.btnApplyCorrection.visibility = View.GONE
        binding.cardSignalType.isEnabled = false
        binding.frequencyGraph.clearData()

        measurementJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                // --- Phase 1: Level-Check ---
                val levelOk = runLevelCheck()

                if (!levelOk || !isActive) {
                    withContext(Dispatchers.Main) {
                        binding.tvLevelInstruction.text = getString(R.string.level_check_too_low)
                        binding.btnStartMeasurement.text = getString(R.string.btn_start_measurement)
                        binding.cardSignalType.isEnabled = true
                    }
                    return@launch
                }

                // --- Phase 2: Übergang zur Messung ---
                withContext(Dispatchers.Main) {
                    binding.cardLevelCheck.visibility = View.GONE
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvProgress.visibility = View.VISIBLE
                    binding.tvProgress.text = getString(R.string.level_check_ok)
                }
                delay(400)

                // --- Phase 3: Eigentliche Messung (ggf. mehrfach) ---
                val signalType = withContext(Dispatchers.Main) {
                    if (binding.rbSweep.isChecked) SignalGenerator.SignalType.SINE_SWEEP
                    else SignalGenerator.SignalType.PINK_NOISE
                }
                val measurementCount = withContext(Dispatchers.Main) {
                    when (binding.toggleMeasurementCount.checkedButtonId) {
                        R.id.btnCount3 -> 3
                        R.id.btnCount5 -> 5
                        else -> 1
                    }
                }
                val durationSeconds = when (signalType) {
                    SignalGenerator.SignalType.SINE_SWEEP -> 5.0
                    SignalGenerator.SignalType.PINK_NOISE -> 10.0
                }

                // Alle Band-Level-Ergebnisse sammeln
                val allBandLevels = mutableListOf<FloatArray>()

                for (run in 1..measurementCount) {
                    withContext(Dispatchers.Main) {
                        binding.tvProgress.text = getString(R.string.measuring_run_start, run, measurementCount)
                        binding.progressBar.progress = ((run - 1) * 100) / measurementCount
                    }

                    val recordJob = async { audioRecorder.record(durationSeconds + 0.5) }
                    delay(200)

                    signalGenerator.play(signalType) { runProgress ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            val overall = ((run - 1) * 100 + runProgress) / measurementCount
                            binding.progressBar.progress = overall
                            binding.tvProgress.text = getString(
                                R.string.measuring_run_progress, run, measurementCount, runProgress
                            )
                        }
                    }

                    val recordedSamples = recordJob.await()
                    val spectrum = fftProcessor.computeSpectrum(recordedSamples)
                    val bandLevels = frequencyAnalyzer.analyzeThirdOctaveBands(spectrum)
                    allBandLevels.add(bandLevels)

                    // Kurze Pause zwischen den Durchläufen
                    if (run < measurementCount) {
                        withContext(Dispatchers.Main) {
                            binding.tvProgress.text = getString(R.string.measuring_run_pause, run, measurementCount)
                        }
                        delay(800)
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = getString(R.string.measurement_complete)
                }

                // Mittelwert über alle Durchläufe berechnen
                val bandCount = allBandLevels[0].size
                val averagedBandLevels = FloatArray(bandCount) { i ->
                    allBandLevels.sumOf { it[i].toDouble() }.toFloat() / allBandLevels.size
                }

                val normalizedLevels = frequencyAnalyzer.normalizeLevels(averagedBandLevels)
                val correction = correctionCalculator.calculateCorrection(normalizedLevels)
                val smoothedCorrection = correctionCalculator.smoothCorrection(correction)
                val correctedResponse = correctionCalculator.calculateCorrectedResponse(
                    normalizedLevels, smoothedCorrection
                )

                measuredLevels = normalizedLevels
                correctionCurve = smoothedCorrection

                val flatnessBefore = correctionCalculator.calculateFlatnessScore(normalizedLevels)
                val flatnessAfter = correctionCalculator.calculateFlatnessScore(correctedResponse)

                withContext(Dispatchers.Main) {
                    binding.frequencyGraph.setMeasuredResponse(normalizedLevels)
                    binding.frequencyGraph.setCorrectedResponse(smoothedCorrection)
                    binding.btnApplyCorrection.visibility = View.VISIBLE
                    binding.btnStartMeasurement.text = getString(R.string.btn_measure_again)

                    Toast.makeText(
                        this@MeasurementActivity,
                        "Flatness: %.1f dB → %.1f dB".format(flatnessBefore, flatnessAfter),
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = getString(R.string.measurement_error, e.message)
                    Toast.makeText(
                        this@MeasurementActivity,
                        getString(R.string.measurement_error, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.cardLevelCheck.visibility = View.GONE
                    binding.cardSignalType.isEnabled = true
                    if (!signalGenerator.isCurrentlyPlaying) {
                        binding.btnStartMeasurement.text = getString(R.string.btn_start_measurement)
                    }
                }
            }
        }
    }

    /**
     * Spielt Pink Noise und überwacht den Mikrofon-Pegel.
     * Verwendet einen gleitenden Mittelwert über die letzten 10 Messungen,
     * um die starke Peak-Varianz von Pink Noise auszugleichen.
     * Bricht nach 15 Sekunden mit false ab, wenn der Pegel nicht erreicht wurde.
     */
    private suspend fun runLevelCheck(): Boolean = withContext(Dispatchers.Default) {
        withContext(Dispatchers.Main) {
            binding.cardLevelCheck.visibility = View.VISIBLE
            binding.progressLevel.progress = 0
            binding.progressLevel.progressTintList = ColorStateList.valueOf(Color.RED)
            binding.tvLevelPercent.text = "0%"
            binding.tvLevelInstruction.text = getString(R.string.level_check_instruction)
        }

        var levelAchieved = false
        val startTime = System.currentTimeMillis()
        val timeoutMs = 15_000L

        // Gleitender Mittelwert über die letzten 10 Peak-Werte
        val recentPeaks = mutableListOf<Float>()
        val windowSize = 10
        val threshold = 0.35f  // 35 % des ADC-Bereichs im Mittel = guter SNR

        val playJob = launch {
            signalGenerator.play(SignalGenerator.SignalType.PINK_NOISE) {}
        }

        audioRecorder.streamLevel { level ->
            recentPeaks.add(level)
            if (recentPeaks.size > windowSize) recentPeaks.removeAt(0)

            val avgLevel = if (recentPeaks.size >= windowSize)
                recentPeaks.average().toFloat()
            else
                level

            val percent = (avgLevel * 100).toInt().coerceIn(0, 100)
            lifecycleScope.launch(Dispatchers.Main) {
                binding.progressLevel.progress = percent
                binding.tvLevelPercent.text = "$percent%"
                binding.progressLevel.progressTintList = ColorStateList.valueOf(
                    if (avgLevel >= threshold) Color.GREEN else Color.RED
                )
            }

            if (avgLevel >= threshold && recentPeaks.size >= windowSize) {
                levelAchieved = true
                return@streamLevel false
            }

            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                return@streamLevel false
            }
            true
        }

        playJob.cancelAndJoin()
        signalGenerator.stop()
        // Kurze Pause damit der SignalGenerator sauber beendet wird
        delay(200)
        levelAchieved
    }

    private fun stopMeasurement() {
        signalGenerator.stop()
        audioRecorder.stop()
        measurementJob?.cancel()

        binding.btnStartMeasurement.text = getString(R.string.btn_start_measurement)
        binding.progressBar.visibility = View.GONE
        binding.tvProgress.visibility = View.GONE
        binding.cardLevelCheck.visibility = View.GONE
        binding.cardSignalType.isEnabled = true
    }

    private fun showSaveProfileDialog() {
        val measured = measuredLevels ?: return
        val correction = correctionCurve ?: return

        val editText = EditText(this).apply {
            hint = getString(R.string.save_profile_hint)
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_profile_for_device, deviceName))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = editText.text.toString().ifBlank { "Profile ${System.currentTimeMillis()}" }
                saveProfile(name, measured, correction)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveProfile(name: String, measured: FloatArray, correction: FloatArray) {
        lifecycleScope.launch {
            val dao = SpeakerEqApp.instance.database.profileDao()

            val profile = SpeakerProfile(
                profileName = name,
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                measuredResponse = EqService.floatArrayToJson(measured),
                correctionCurve = EqService.floatArrayToJson(correction)
            )

            val id = dao.insert(profile)
            dao.activateProfile(id)

            Toast.makeText(
                this@MeasurementActivity,
                getString(R.string.profile_activated, name),
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        signalGenerator.stop()
        audioRecorder.stop()
    }
}
