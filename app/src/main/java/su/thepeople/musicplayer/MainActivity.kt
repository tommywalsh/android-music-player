package su.thepeople.musicplayer

import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.common.util.concurrent.ListenableFuture
import su.thepeople.musicplayer.databinding.ActivityMainBinding

/**
 * Main application.  This largely handles setup and teardown. Most of the app logic lives in other classes.
 *
 * Full setup of the UI is a four-step process:
 *   1) in onCreate, UI objects are created, but are not yet functional
 *   2) in onStart, we kick off the background process of creating a connection to the backend
 *   3) When the background connection is finished, we finalize the UI objects to make them functional and kick off a new background process to
 *      query the status of the backend
 *   4) When the backend status info comes back to us, finally, we update the on-screen widgets to reflect that status.
 *
 */
class MainActivity : FragmentActivity() {

    private var backendConnection: MediaBrowser? = null
    private lateinit var connectionFinalizeCallback: ListenableFuture<MediaBrowser>
    private lateinit var playerUI: PlayerUI
    private lateinit var libraryUI: LibraryUI
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the main UI
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the helper UI fragments
        playerUI = PlayerUI(this@MainActivity)
        libraryUI = LibraryUI(this@MainActivity, this@MainActivity)

        // Set up the pager that allows swiping between the UI fragments
        viewPager = binding.viewPager
        viewPager.adapter = object: FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> playerUI
                    else -> libraryUI
                }
            }
            override fun getItemCount() = 2
        }
    }

    override fun onStart() {
        super.onStart()

        // Start the process of connecting to the backend
        val sessionToken = SessionToken(this, ComponentName(this, McotpService::class.java))
        connectionFinalizeCallback = MediaBrowser.Builder(this, sessionToken).buildAsync()
        connectionFinalizeCallback.addListener(::onBackendConnectionFinalized, ContextCompat.getMainExecutor(this))
    }

    private fun onBackendConnectionFinalized() {

        // Dispose of our old connection, if necessary
        backendConnection?.release()

        // Remember the new connection...
        val mb = connectionFinalizeCallback.get()
        backendConnection = mb

        // ... and supply the connection (which acts as both a player and a library) to our UI fragments
        playerUI.setPlayer(mb)
        libraryUI.setBackendLibrary(mb)
    }

    override fun onStop() {
        // Abandon any connection attempt that has not yet been finalized.
        MediaController.releaseFuture(connectionFinalizeCallback)

        // Release any backend connection we already have
        backendConnection?.release()
        backendConnection = null
        super.onStop()
    }

    fun navigateToPlayer() {
        viewPager.currentItem = 0
    }

    fun navigateTo(parentIds: List<String>, childId: String) {
        libraryUI.navigateTo(parentIds, childId)
        viewPager.currentItem = 1
    }
}