package su.thepeople.musicplayer

import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.room.Room
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.Song

private fun <T> successCallback(task: (T)->Unit): FutureCallback<T> {
    return object: FutureCallback<T> {
        override fun onSuccess(result: T) {
            task(result)
        }
        override fun onFailure(t: Throwable) {
            // TODO: what happens here???
        }
    }
}

// Handles both playing music and also library lookup into the MCotP catalog
class McotpService : MediaLibraryService() {

    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var player: ExoPlayer
    private lateinit var database: Database
    private lateinit var playbackManager: PlaybackManager

    private val librarySession: McotpLibrarySession by lazy {
        McotpLibrarySession(applicationContext)
    }

    private var shuffleProvider = {database.songDao().getRandom(10)}


    private inner class PlaybackManager {
        private var provider: ()->List<Song> = shuffleProvider
        private var transitionListener = object: Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if(!player.hasNextMediaItem()) {
                    requestNextBatch()
                }
            }
        }

        init {
            requestNextBatch()
            player.addListener(transitionListener)
        }

        private fun onSongBatchReceived(songs: List<MediaItem>) {
            player.addMediaItems(songs)
            player.prepare()
            player.play() // TODO: keep track of if we're paused to decide if we should play here
        }

        private fun requestNextBatch() {
            val future = database.async {
                provider().map{database.mediaItem(it)}
            }
            Futures.addCallback(future, successCallback(::onSongBatchReceived), ContextCompat.getMainExecutor(applicationContext))
        }

        // TODO: set new providers on the fly, might require a special setter?
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaLibrarySession.Builder(this, player, librarySession).setId("mcotp").build()
        database = Room.databaseBuilder(applicationContext, Database::class.java, "mcotp-database").build()
        playbackManager = PlaybackManager()
    }

    override fun onDestroy() {
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession
}