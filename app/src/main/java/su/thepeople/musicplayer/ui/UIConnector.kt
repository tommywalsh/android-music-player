package su.thepeople.musicplayer.ui

import android.content.ComponentName
import android.content.Context
import android.util.ArraySet
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import su.thepeople.musicplayer.backend.McotpService
import kotlin.jvm.Volatile

/**
 * Singleton object that handles hooking up a backend music player/library to frontend UI widgets.
 *
 * Our backend connection may appear or disappear at any time. Same for our UI views. Worse, the UI views may be "restored" automatically by the
 * system. This makes it really hard to get each side to know about the other. This single centralized connector object solves this problem. Each
 * side simply needs to say "I'm starting up" and "I'm shutting down".  This class can then tell each side about the other side, when appropriate.
 */
class UIConnector private constructor() {

    companion object {
        @Volatile private var instance: UIConnector? = null
        fun get() : UIConnector {
            return instance ?: synchronized(this) {
                instance ?: UIConnector().also { instance = it}
            }
        }
    }

    private var backendConnection: MediaBrowser? = null
    private var pendingConnectionFuture: ListenableFuture<MediaBrowser>? = null
    private var mainUI: MainUI? = null

    fun requestConnection(mainUI: MainUI, context: Context) {
        this.mainUI = mainUI
        // Start the process of connecting to the backend
        if (backendConnection == null) {
            Log.d("Main Activity", "Making new backend connection in onStart")
            val sessionToken = SessionToken(context, ComponentName(context, McotpService::class.java))
            val future = MediaBrowser.Builder(context, sessionToken).buildAsync()
            pendingConnectionFuture = future
            future.addListener({onBackendConnectionFinalized()}, ContextCompat.getMainExecutor(context))
        }
    }

    private fun onBackendConnectionFinalized() {
        Log.d("BackendConnection", "New backend connection finalized")

        // Dispose of our old connection, if necessary
        backendConnection?.release()

        // Remember the new connection and forget the pending future...
        val cx = pendingConnectionFuture!!.get()
        backendConnection = cx
        pendingConnectionFuture = null

        // Since we have a brand-new connection, we should do a full update on any listener we've already registered
        val main = mainUI!!  // Should be impossible to have a connection without a main UI, at least for now
        registeredUIs.forEach { ui ->
            ui.connect(UIConnectionData(main, cx))
        }

    }

    fun abandonConnection() {
        Log.d("BackendConnection", "Abandoning backend connection")
        pendingConnectionFuture?.let{MediaController.releaseFuture(it)}
        backendConnection?.release()
        backendConnection = null
    }

    private val registeredUIs = ArraySet<ConnectableUI>()

    fun registerUI(newUI: ConnectableUI) {
        registeredUIs.add(newUI)
        backendConnection?.let{cx ->
            newUI.connect(UIConnectionData(mainUI!!, cx))
        }
    }

    fun unregisterUI(oldUI: ConnectableUI) {
        registeredUIs.remove(oldUI)
        // There is no need for us to "disconnect" the player UI. It will disconnect on its own.
    }
}

class UIConnectionData(val mainUI: MainUI, val connection: MediaBrowser)

interface ConnectableUI {
    // When a backend connection is ready, this method will be called on all registered UIs, to provide them with connection-related data
    fun connect(connectionData: UIConnectionData)
}
