package su.thepeople.musicplayer.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

/**
 * This class forces the screen to stay on whenever Bluetooth is connected.
 *
 * It's assumed that "bluetooth connected" is a good-enough proxy for "The user wants to play music", when we are using the "console" build flavor.
 */
@Suppress("DEPRECATION")
class BluetoothScreenLocker(ui: Activity) : BroadcastReceiver() {
    private val rawScreenLocker = ScreenLocker(ui)

    init {
        val filter =  IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        ui.registerReceiver(this, filter)

        // Bluetooth may already be connected by the time we get here. If so, lock screen now.
        if (ActivityCompat.checkSelfPermission(ui, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            if (BluetoothAdapter.getDefaultAdapter().bondedDevices.isNotEmpty()) {
                rawScreenLocker.ensureScreenOn()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Force screen on when bluetooth connects, but don't force anything when bluetooth is off
        when(intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {rawScreenLocker.ensureScreenOn()}
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {rawScreenLocker.allowScreenToShutOff()}
        }
    }
}