package su.thepeople.musicplayer

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession

class PlayerAndLibraryService : MediaLibraryService() {

    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var player: ExoPlayer

    private val diskLibrary: DiskLibrary by lazy {
        DiskLibrary(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaLibrarySession.Builder(this, player, diskLibrary).setId("mcotp").build()
    }

    override fun onDestroy() {
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession
}