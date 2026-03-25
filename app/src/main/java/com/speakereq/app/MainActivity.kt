package com.speakereq.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.speakereq.app.databinding.ActivityMainBinding
import com.speakereq.app.eq.EqService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var eqService: EqService? = null
    private var serviceBound = false

    // Bluetooth-Gerätedaten zwischenspeichern, damit sie auch bei
    // asynchronem Timing zuverlässig verfügbar sind
    private var connectedDeviceName: String? = null
    private var connectedDeviceAddress: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startEqService()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as EqService.EqBinder
            eqService = binder.getService()
            serviceBound = true
            updateUI()

            eqService?.onDeviceChangedListener = { name, address ->
                connectedDeviceName = name
                connectedDeviceAddress = address
                runOnUiThread {
                    updateBluetoothInfo(name)
                    // EQ-Status neu laden – Profil könnte automatisch aktiviert worden sein
                    lifecycleScope.launch {
                        delay(600)
                        updateUI()
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            eqService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.app_name)

        setupListeners()
        checkPermissionsAndStart()
        observeActiveProfile()
    }

    private fun setupListeners() {
        binding.switchEq.setOnCheckedChangeListener { _, isChecked ->
            eqService?.systemEqualizer?.setEnabled(isChecked)
            updateEqStatus(isChecked)
        }

        binding.btnMeasure.setOnClickListener {
            val intent = Intent(this, MeasurementActivity::class.java).apply {
                putExtra(MeasurementActivity.EXTRA_DEVICE_NAME,
                    connectedDeviceName ?: eqService?.getConnectedDeviceName() ?: getString(R.string.unknown_speaker))
                putExtra(MeasurementActivity.EXTRA_DEVICE_ADDRESS,
                    connectedDeviceAddress ?: eqService?.getConnectedDeviceAddress() ?: "00:00:00:00:00:00")
            }
            startActivity(intent)
        }

        binding.btnProfiles.setOnClickListener {
            startActivity(Intent(this, ProfileListActivity::class.java))
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        // BLUETOOTH_CONNECT ist nur auf Android 12+ (API 31) eine Runtime-Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startEqService()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startEqService() {
        val intent = Intent(this, EqService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeActiveProfile() {
        lifecycleScope.launch {
            SpeakerEqApp.instance.database.profileDao().getAllProfiles().collectLatest { profiles ->
                val active = profiles.find { it.isActive }
                if (active != null) {
                    binding.tvActiveProfile.text = getString(R.string.active_profile, active.profileName)

                    // Show the correction on the graph
                    val correction = EqService.jsonToFloatArray(active.correctionCurve)
                    val measured = EqService.jsonToFloatArray(active.measuredResponse)
                    if (measured != null) {
                        binding.frequencyGraph.setMeasuredResponse(measured)
                    }
                    if (correction != null) {
                        binding.frequencyGraph.setCorrectedResponse(correction)
                    }
                } else {
                    binding.tvActiveProfile.text = getString(R.string.no_active_profile)
                    binding.frequencyGraph.clearData()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val isEqEnabled = eqService?.systemEqualizer?.isEnabled ?: false
        binding.switchEq.isChecked = isEqEnabled
        updateEqStatus(isEqEnabled)
        updateBluetoothInfo(eqService?.getConnectedDeviceName())
    }

    private fun updateEqStatus(enabled: Boolean) {
        binding.tvEqStatus.text = getString(
            if (enabled) R.string.eq_status_active else R.string.eq_status_inactive
        )
        binding.viewEqIndicator.setBackgroundColor(
            getColor(if (enabled) R.color.eq_active else R.color.eq_inactive)
        )
    }

    private fun updateBluetoothInfo(deviceName: String?) {
        binding.tvConnectedSpeaker.text = if (deviceName != null) {
            "${getString(R.string.connected_speaker)}: $deviceName"
        } else {
            getString(R.string.no_speaker_connected)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
