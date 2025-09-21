package su.thepeople.musicplayer.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST
import android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
import android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
import android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
import android.media.AudioManager
import android.os.Build
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * This class toggles a "modal UI" based on the presence/absence of a Bluetooth connection.
 * Being modal means that the device behaves as if this app is the only one on the system:
 * The screen is locked on, the app is in full-screen mode, etc.
 */
class BluetoothModalController(private val ui: Activity, private val view: View) : BroadcastReceiver() {
    private val rawScreenLocker = ScreenLocker(ui)
    private var wantToBeLocked = false
    private var wasRegistered = false

    override fun onReceive(context: Context, intent: Intent) {
        // Force screen on when bluetooth connects, but don't force anything when bluetooth is off
        when(intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                enterModalFocus()
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                leaveModalFocus()
            }
        }
    }

    private fun enterModalFocus() {
        rawScreenLocker.ensureScreenOn()
        val insetController = WindowCompat.getInsetsController(ui.window, view)
        insetController.hide(WindowInsetsCompat.Type.systemBars())
        ui.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ui.setTurnScreenOn(true)
        wantToBeLocked = true
    }

    private fun leaveModalFocus() {
        val insetController = WindowCompat.getInsetsController(ui.window, view)
        insetController.show(WindowInsetsCompat.Type.systemBars())
        ui.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        rawScreenLocker.allowScreenToShutOff()
        ui.setTurnScreenOn(false)
        wantToBeLocked = false
    }

    private fun isBluetoothAlreadyConnected(): Boolean {

        val audioManager = ui.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                it.communicationDevice?.let { device ->
                    return when (device.type) {
                        TYPE_BLE_BROADCAST, TYPE_BLE_HEADSET, TYPE_BLUETOOTH_A2DP, TYPE_BLUETOOTH_SCO -> true
                        else -> false
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                return it.isBluetoothScoOn || it.isBluetoothA2dpOn
            }
        }
        return false
    }

    fun registerForBluetoothNotificationsNow() {
        val filter =  IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        ui.registerReceiver(this, filter)
        wasRegistered = true
    }

    private fun registerForBluetoothNotificationsIfAllowed() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // In S and later, this is a "runtime permission", so we might have to explicitly ask the user
            if (ui.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ui.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 666)
            } else {
                registerForBluetoothNotificationsNow()
            }
        } else {
            // On versions before S, we will always have this permission since the user agreed at install-time
            registerForBluetoothNotificationsNow()
        }
    }

    fun activate() {
        registerForBluetoothNotificationsIfAllowed()

        // Bluetooth may already be connected by the time we get here. If so, lock screen now.
        if (isBluetoothAlreadyConnected()) {
            enterModalFocus()
        }
    }

    fun deactivate() {
        if (wasRegistered) {
            ui.unregisterReceiver(this)
        }
        leaveModalFocus()
    }

    fun renewScreenLock() {
        if (wantToBeLocked) {
            rawScreenLocker.ensureScreenOn()
        }
    }
}