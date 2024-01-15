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

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding
    var mediaBrowser: MediaBrowser? = null
    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private var defaultPlayerUI: DefaultPlayerUI? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up fragments
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)

        viewPager.adapter = object: FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return if (position == 0) {
                    val pf = DefaultPlayerUI(this@MainActivity)
                    defaultPlayerUI = pf
                    pf
                } else {
                    LibraryUI(this@MainActivity)
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
                mediaBrowser = browserFuture.get()
                defaultPlayerUI?.player = mediaBrowser

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