package su.thepeople.musicplayer

import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.room.Room
import su.thepeople.musicplayer.data.Database

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

    private lateinit var mediaSession: MediaLibrarySession   // Navigation object
    private lateinit var database: Database                  // Storage of band/album/song information
    private lateinit var customPlayer: CustomPlayer          // Our custom player that wraps the android player
    private lateinit var librarySession: McotpLibrarySession // Dipatches between UI and player/library

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(applicationContext, Database::class.java, "mcotp-database").build()
        customPlayer = CustomPlayer(database, applicationContext)
        librarySession = McotpLibrarySession(applicationContext, customPlayer)
        mediaSession = MediaLibrarySession.Builder(this, customPlayer.playerAPIHandler, librarySession).setId("mcotp").build()

        // TODO: This should really be coordinated elsewhere, perhaps in the service startup
        startBackgroundScan()

    }

    override fun onDestroy() {
        customPlayer.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    /**
     * This kicks off a full scan of the disk, if necessary. The scan is a long-running operation that is run asynchronously.
     */
    private fun startBackgroundScan() {
        if (!database.isScanned()) {
            val scanner = Scanner(applicationContext, database)
            database.async {
                scanner.fullScan()
            }
        }
    }



    // TODO: Clean separation between service, session manager, library navigator, music player
}