package com.speakereq.app.eq

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.speakereq.app.MainActivity
import com.speakereq.app.R
import com.speakereq.app.SpeakerEqApp
import kotlinx.coroutines.*

/**
 * Foreground service that keeps the EQ active and monitors Bluetooth connections
 * to automatically load the appropriate speaker profile.
 */
class EqService : Service() {

    private val binder = EqBinder()
    val systemEqualizer = SystemEqualizer()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentDeviceName: String? = null
    private var currentDeviceAddress: String? = null

    inner class EqBinder : Binder() {
        fun getService(): EqService = this@EqService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        // startForeground SOFORT aufrufen – auf Android 12+ crasht die App
        // mit ForegroundServiceDidNotStartInTimeException wenn das zu spät passiert
        startForeground(SpeakerEqApp.NOTIFICATION_ID, createNotification())

        // Initialize equalizer (kann auf manchen Geräten fehlschlagen)
        if (!systemEqualizer.initialize()) {
            Log.e(TAG, "Failed to initialize system equalizer")
            // Service trotzdem weiterlaufen lassen – Bluetooth-Erkennung funktioniert dennoch
        }

        // Register for Bluetooth connection changes
        // Android 13+ (API 33) erfordert explizites RECEIVER_EXPORTED-Flag,
        // sonst SecurityException → Service-Crash
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }

        // Check currently connected Bluetooth audio device
        detectConnectedDevice()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                systemEqualizer.setEnabled(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Notification aktualisieren (startForeground wurde bereits in onCreate aufgerufen)
        startForeground(SpeakerEqApp.NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, EqService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (currentDeviceName != null) {
            getString(R.string.notification_text, currentDeviceName)
        } else {
            getString(R.string.notification_text_no_speaker)
        }

        return NotificationCompat.Builder(this, SpeakerEqApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun hasBluetoothPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // Auf API < 31 ist BLUETOOTH eine normale (nicht-runtime) Permission –
            // sie ist automatisch gewährt wenn im Manifest deklariert
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun detectConnectedDevice() {
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Bluetooth permission not granted, skipping device detection")
            return
        }

        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: return

            // Check A2DP connected devices
            adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val devices = proxy.connectedDevices
                    if (devices.isNotEmpty()) {
                        val device = devices[0]
                        onBluetoothDeviceConnected(device.name ?: "Unknown", device.address)
                    }
                    adapter.closeProfileProxy(profile, proxy)
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.A2DP)
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting Bluetooth device", e)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!hasBluetoothPermission()) return

            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    onBluetoothDeviceConnected(device.name ?: "Unknown", device.address)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (device.address == currentDeviceAddress) {
                        onBluetoothDeviceDisconnected()
                    }
                }
            }
        }
    }

    private fun onBluetoothDeviceConnected(name: String, address: String) {
        currentDeviceName = name
        currentDeviceAddress = address
        Log.i(TAG, "Bluetooth device connected: $name ($address)")

        // Try to load profile for this device
        scope.launch {
            val dao = SpeakerEqApp.instance.database.profileDao()
            val profile = dao.getProfileForDevice(address)

            if (profile != null) {
                Log.i(TAG, "Found profile for device: ${profile.profileName}")
                val correction = jsonToFloatArray(profile.correctionCurve)
                if (correction != null) {
                    systemEqualizer.applyCorrection(correction)
                    systemEqualizer.setEnabled(true)
                    dao.activateProfile(profile.id)
                }
            }

            // Update notification
            startForeground(SpeakerEqApp.NOTIFICATION_ID, createNotification())
        }

        onDeviceChangedListener?.invoke(name, address)
    }

    private fun onBluetoothDeviceDisconnected() {
        Log.i(TAG, "Bluetooth device disconnected: $currentDeviceName")
        currentDeviceName = null
        currentDeviceAddress = null
        systemEqualizer.reset()

        // Update notification
        startForeground(SpeakerEqApp.NOTIFICATION_ID, createNotification())

        onDeviceChangedListener?.invoke(null, null)
    }

    fun getConnectedDeviceName(): String? = currentDeviceName
    fun getConnectedDeviceAddress(): String? = currentDeviceAddress

    var onDeviceChangedListener: ((name: String?, address: String?) -> Unit)? = null
        set(value) {
            field = value
            // Sofort den aktuellen Zustand melden, falls Gerät bereits verbunden war
            // bevor der Listener gesetzt wurde (Race Condition vermeiden)
            if (value != null && currentDeviceName != null) {
                value.invoke(currentDeviceName, currentDeviceAddress)
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) { }
        systemEqualizer.release()
    }

    companion object {
        private const val TAG = "EqService"
        const val ACTION_STOP = "com.speakereq.app.STOP_EQ"

        fun jsonToFloatArray(json: String): FloatArray? {
            return try {
                json.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().toFloat() }
                    .toFloatArray()
            } catch (e: Exception) {
                null
            }
        }

        fun floatArrayToJson(array: FloatArray): String {
            return array.joinToString(",", "[", "]") { "%.2f".format(it) }
        }
    }
}
