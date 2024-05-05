package su.thepeople.musicplayer.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View

/**
 * The application is being built in a "console" configuration. That means it expects to be the only application running on the device, and so we
 * want to hide any Android notofications, widgets, etc. We also want to prevent auto screen-shutoff, etc.
 */


// It's expected that there are lots of deprecation warnings here. We're intentionally "misusing" Android as a single-app platform.
@Suppress("DEPRECATION")
class ConsoleCustomizer : MainUICustomizer() {

    private lateinit var btScreenLocker : BluetoothScreenLocker

    override fun onCreateActivity(ui: Activity, mainView: View) {
        ui.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        mainView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        btScreenLocker = BluetoothScreenLocker(ui)
    }
}

val CUSTOMIZER: MainUICustomizer = ConsoleCustomizer()
