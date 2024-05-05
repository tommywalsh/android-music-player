package su.thepeople.musicplayer.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.PowerManager

/**
 * This class handles all the work associated with turning the screen on and allowing the screen to shut off
 *
 * This class uses a couple of deprecated and warned-against techniques, which are actually appropriate for the "console" build flavor.
 *   FULL_WAKE_LOCK is deprecated, as they'd like you to use Window flags instead. But, I could not find a way to make
 *     window flags give the desired behavior of forcing the screen on in response to a bluetooth event. I think this is
 *     by design, as ordinarily this would not be desired behavior on a normal phone app. But, this is not meant to be
 *     a well-behaved phone app, so we use the old deprecated flag instead.
 *   It's recommended to use a timeout when acquiring a wake lock so as not to drain a user's battery for long
 *     operations. But, we really do want the screen to stay on "forever" (until the user explicitly decide to shut it off)
 */

@Suppress("DEPRECATION")
class ScreenLocker(ui: Activity) {

    private val wakeLock: PowerManager.WakeLock
    private var isLocked: Boolean = false

    init {
        val powerServiceAny = ui.getSystemService(Context.POWER_SERVICE)
        val powerManager = powerServiceAny as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.FULL_WAKE_LOCK, "musicplayer:wakelock")
    }

    @SuppressLint("WakelockTimeout")
    fun ensureScreenOn() {
        if (!isLocked) {
            wakeLock.acquire()
            isLocked = true
        }
    }

    fun allowScreenToShutOff() {
        if (isLocked) {
            wakeLock.release()
            isLocked = false
        }
    }
}
