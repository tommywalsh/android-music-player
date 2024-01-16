package su.thepeople.musicplayer

import android.content.ComponentName
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import su.thepeople.musicplayer.databinding.ActivityMainBinding

/**
 * Main application.  This largely handles setup. Most of the app logic lives in other classes.
 */
class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding
    var mediaBrowser: MediaBrowser? = null
    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private lateinit var customPlayerUI: CustomPlayerUI
    private lateinit var libraryUI: LibraryUI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up fragments
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)

        viewPager.adapter = object: FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> {
                        customPlayerUI = CustomPlayerUI(this@MainActivity)
                        customPlayerUI
                    }
                    else -> {
                        libraryUI = LibraryUI(this@MainActivity)
                        libraryUI
                    }
                }
            }

            override fun getItemCount() = 2
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, McotpService::class.java))
        browserFuture = MediaBrowser.Builder(this, sessionToken).buildAsync()
        browserFuture.addListener(
            {
                mediaBrowser?.release()
                val mb = browserFuture.get()
                mediaBrowser = mb

                // The "media browser" serves as both a library and a player.
                customPlayerUI.player = mb
                libraryUI.library = mb
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        MediaController.releaseFuture(browserFuture)
        mediaBrowser?.release()
        mediaBrowser = null
        super.onStop()
    }
}