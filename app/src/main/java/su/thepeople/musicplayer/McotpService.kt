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

// TODO: eliminate this object, which is duplicated elsewere. Put it in a utils file or something like that.
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

/**
 * Android's media API awkwardly lumps together two different tasks:
 *    1) Playing music
 *    2) Navigating through a collection of music
 *
 * This class handles both of those tasks, although it offloads the navigation via the "mediaSession" object.
 *
 *
 * Future directions:
 *    - Consider breaking out the music playing functionality to another class, and using this class just as "glue" for the player and navigator.
 */
class McotpService : MediaLibraryService() {

    private lateinit var mediaSession: MediaLibrarySession  // Navigation object
    private lateinit var player: ExoPlayer                  // Android-provided object that plays music
    private lateinit var database: Database                 // Storage of band/album/song information
    private lateinit var playbackManager: PlaybackManager   // Object that manages the player

    private val librarySession: McotpLibrarySession by lazy {
        McotpLibrarySession(applicationContext)
    }

    private var shuffleProvider = {database.songDao().getRandom(10)}


    /**
     * Helper class to handle feeding songs to the Android media player
     */
    private inner class PlaybackManager {

        /**
         * A "function pointer" than handles retrieving a batch of songs from the database.  This function may be called repeatedly.
         *
         * TODO: This might someday need state, and might have to move to a formal interface with implementing subclasses
         */
        private var provider: ()->List<Song> = shuffleProvider


        /**
         * Every time the player jumps to a new song, we check if the player is about to run out of songs, and if so, we send more.
         */
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
            // Database access is always done from another thread.
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