package su.thepeople.musicplayer

import android.content.ComponentName
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import android.view.Menu
import android.view.MenuItem
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import su.thepeople.musicplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var playerView: PlayerView
    private lateinit var mediaSession: MediaSession
    private var mediaController: MediaController? = null
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playerView = findViewById(R.id.player_view)

        // TODO: move this to a service
        val player = ExoPlayer.Builder(applicationContext).build()
        mediaSession = MediaSession.Builder(applicationContext, player).build()
        playerView.player = player
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlayerAndLibraryService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController?.release()
                mediaController = controllerFuture.get()
                playerView.player = mediaController

                val path = "/storage/7F62-69AB/mcotp/Quarterflash/1981 Harden My Heart.mp3"
                val mediaItem = MediaItem.Builder().setUri(path).build()
                mediaController?.setMediaItem(mediaItem)
                mediaController?.prepare()
                mediaController?.play()

            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        MediaController.releaseFuture(controllerFuture)
        mediaController?.release()
        mediaController = null
        super.onStop()
    }

    override fun onDestroy() {
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}