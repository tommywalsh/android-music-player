package su.thepeople.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
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

        val key = "mcotp_restart"
        val prefs = applicationContext.getSharedPreferences(key, Context.MODE_PRIVATE)
        Log.d("McotpService", prefs.toString())
        val config = recoverConfig(prefs, key)
        Log.d("McotpService", config.toString())
        customPlayer = CustomPlayer(database, applicationContext, config)

        librarySession = McotpLibrarySession(applicationContext, customPlayer)
        mediaSession = MediaLibrarySession.Builder(this, customPlayer.playerAPIHandler, librarySession).setId("mcotp").build()


        startBackgroundScan()

        registerReceiver(silencer, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    private val silencer = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("Mcotp Service", "intent received")

            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d("Mcotp Service", "pausing because audio is becoming noisy")
                customPlayer.forcePause()
            }
        }
    }

    override fun onDestroy() {
        val key = "mcotp_restart"
        val config = customPlayer.getRestartConfig()
        val prefs = applicationContext.getSharedPreferences(key, Context.MODE_PRIVATE)
        config.persist(prefs, key)

        unregisterReceiver(silencer)
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