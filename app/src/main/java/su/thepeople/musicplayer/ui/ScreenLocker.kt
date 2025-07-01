package su.thepeople.musicplayer.ui

import android.app.Activity
import android.view.WindowManager

/**
 * This class handles all the work associated with forcing the screen to stay on and allowing the
 * screen to shut off.
 *
 * Note that this is NOT "turn on the screen" and "turn off the screen".  We only:
 *   - Force an already-on screen to stay on
 *   - Allow an already-on screen to turn off, if the system decides to do so
 */

class ScreenLocker(ui: Activity) {

    private var isLocked: Boolean = false
    private var window = ui.window

    fun ensureScreenOn() {
        if (!isLocked) {
            //wakeLock.acquire()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isLocked = true
        }
    }

    fun allowScreenToShutOff() {
        if (isLocked) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isLocked = false
        }
    }
}
