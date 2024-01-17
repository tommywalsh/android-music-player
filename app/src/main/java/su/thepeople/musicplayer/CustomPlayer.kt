package su.thepeople.musicplayer

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MIXED
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.internalId

private enum class MajorMode {
    COLLECTION,
    BAND,
    ALBUM
}

/**
 * The Android "media3" API mushed together two different concepts: music playback, and library exploration.  This class
 * handles solely the music playback.
 */
class CustomPlayer(private val database: Database, private val context: Context) {

    private val androidPlayer = ExoPlayer.Builder(context).build()

    private var provider: SongProvider = ShuffleProvider()
    private var majorMode = MajorMode.COLLECTION
    private var subMode = "shuffle"  // TODO: There has to be a better way to do this than just a string

    val playerAPIHandler: Player = androidPlayer

    /**
     * Every time the player jumps to a new song, we check if the player is about to run out of songs, and if so, we send more.
     */
    private var transitionListener = object: Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if(!androidPlayer.hasNextMediaItem()) {
                requestNextBatch()
            }
        }
    }

    init {
        requestNextBatch()
        androidPlayer.addListener(transitionListener)
    }

    private fun onSongBatchReceived(songs: List<MediaItem>) {
        androidPlayer.addMediaItems(songs)
        androidPlayer.prepare()
        androidPlayer.play() // TODO: keep track of if we're paused to decide if we should play here
    }

    private fun requestNextBatch() {
        // Database access is always done from another thread.
        val future = database.async {
            provider.getNextBatch(database).map{database.mediaItem(it)}
        }
        Futures.addCallback(
            future,
            successCallback(::onSongBatchReceived),
            ContextCompat.getMainExecutor(context))
    }

    private fun swapProvider(newProvider: SongProvider) {
        provider = newProvider

        // Clear out any stray songs in the "still to play" portion of the playlist
        val nextIndex = androidPlayer.nextMediaItemIndex
        if (nextIndex >= 0) {
            androidPlayer.removeMediaItems(nextIndex, Int.MAX_VALUE)
        }

        requestNextBatch()
    }

    fun toggleBandLock() {
        if (majorMode == MajorMode.BAND) {
            majorMode = MajorMode.COLLECTION
            swapProvider(ShuffleProvider())
            androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_MIXED).build()
        } else {
            val bandId = androidPlayer.currentMediaItem?.mediaMetadata?.extras?.getInt("band")
            if (bandId != null) {
                majorMode = MajorMode.BAND
                swapProvider(BandShuffleProvider(bandId))
                androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_ARTIST).build()
            }
        }
    }

    fun toggleAlbumLock() {
        if (majorMode == MajorMode.ALBUM) {
            majorMode = MajorMode.COLLECTION
            swapProvider(ShuffleProvider())
            androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_MIXED).build()
        } else {
            val albumId = androidPlayer.currentMediaItem?.mediaMetadata?.extras?.getInt("album")
            val songId = androidPlayer.currentMediaItem?.mediaId?.let{internalId(it)}
            if (albumId != null && albumId != 0) {
                majorMode = MajorMode.ALBUM
                swapProvider(AlbumSequentialProvider(albumId, songId))
                androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_ARTIST).build()
            }
        }
    }

    fun changeSubMode() {
        if (majorMode == MajorMode.COLLECTION) {
            if (subMode == "shuffle") {
                subMode = "double-shot"
                swapProvider(DoubleShotProvider())
                androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_ARTIST).build()
            } else {
                subMode = "shuffle"
                swapProvider(ShuffleProvider())
                androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_ARTIST).build()
            }
        }
    }

    fun release() {
        androidPlayer.release()
    }
}