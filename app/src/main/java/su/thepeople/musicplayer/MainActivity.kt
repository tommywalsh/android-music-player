package su.thepeople.musicplayer

import android.content.ComponentName
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import su.thepeople.musicplayer.databinding.ActivityMainBinding

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaSession: MediaSession
    var mediaBrowser: MediaBrowser? = null
    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private var playerFragment: PlayerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO: move this to a service
        val player = ExoPlayer.Builder(applicationContext).build()
        mediaSession = MediaSession.Builder(applicationContext, player).build()

        // Set up fragments
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)

        viewPager.adapter = object: FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return if (position == 0) {
                    val pf = PlayerFragment(this@MainActivity)
                    playerFragment = pf
                    pf
                } else {
                    LibraryFragment(this@MainActivity)
                }
            }

            override fun getItemCount() = 2
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlayerAndLibraryService::class.java))
        browserFuture = MediaBrowser.Builder(this, sessionToken).buildAsync()
        browserFuture.addListener(
            {
                mediaBrowser?.release()
                mediaBrowser = browserFuture.get()
                playerFragment?.player = mediaBrowser

                val path = "/storage/7F62-69AB/mcotp/Quarterflash/1981 Harden My Heart.mp3"
                val mediaItem = MediaItem.Builder().setUri(path).build()
                mediaBrowser?.setMediaItem(mediaItem)
                mediaBrowser?.prepare()
                mediaBrowser?.play()

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

    override fun onDestroy() {
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

}