package su.thepeople.musicplayer.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.View
import androidx.core.app.ActivityCompat
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
        wantToBeLocked = true
    }

    private fun leaveModalFocus() {
        val insetController = WindowCompat.getInsetsController(ui.window, view)
        insetController.show(WindowInsetsCompat.Type.systemBars())
        ui.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        rawScreenLocker.allowScreenToShutOff()
        wantToBeLocked = false
    }

    private fun isBluetoothAlreadyConnected(): Boolean {
        if (ActivityCompat.checkSelfPermission(ui, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val service = ui.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            service?.let {
                if (it.adapter.bondedDevices.isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    fun activate() {
        val filter =  IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        ui.registerReceiver(this, filter)

        // Bluetooth may already be connected by the time we get here. If so, lock screen now.
        if (isBluetoothAlreadyConnected()) {
            enterModalFocus()
        }
    }

    fun deactivate() {
        ui.unregisterReceiver(this)
        leaveModalFocus()
    }

    fun renewScreenLock() {
        if (wantToBeLocked) {
            rawScreenLocker.ensureScreenOn()
        }
    }
}