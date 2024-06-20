package su.thepeople.musicplayer.ui

import android.app.Activity
import android.view.View

/**
 * As of right now, our "device" build flavor needs no special customization at all. This is just boilerplate do-nothing code.
 */
class DeviceCustomizer : MainUICustomizer() {
    override fun onCreateActivity(ui: Activity, mainView: View) {}
    override fun onNewSongLoaded() {}
}

val CUSTOMIZER: MainUICustomizer = DeviceCustomizer()
