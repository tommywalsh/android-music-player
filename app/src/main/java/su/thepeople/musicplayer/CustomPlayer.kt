package su.thepeople.musicplayer

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM
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
 *
 * The Android-provided player object works on the concept of a "playlist", which we try to hide from the end user. Instead, our end user is presented
 * with "play modes" (random-playing the entire library, playing an album, etc.).  We interact with the player by loading more items onto the end of
 * its playlist, and removing any already-played items from the beginning of the playlist.
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
                requestNextBatch(false)
            }
        }
    }

    init {
        requestNextBatch(true)
        androidPlayer.addListener(transitionListener)
    }

    private fun appendSongBatch(songs: List<MediaItem>) {
        androidPlayer.addMediaItems(songs)
        androidPlayer.prepare()
        //androidPlayer.play() // TODO: keep track of if we're paused to decide if we should play here
    }

    private fun playSongBatchNext(songs: List<MediaItem>) {
        appendSongBatch(songs)

        // Clear out any stale songs from the front of the playlist that we've already played.
        val lastPlayedIndex = androidPlayer.nextMediaItemIndex - 2
        if (lastPlayedIndex > 0) {
            androidPlayer.removeMediaItems(0, lastPlayedIndex)
        }
    }

    private fun playSongBatchNow(songs: List<MediaItem>) {
        androidPlayer.clearMediaItems()
        appendSongBatch(songs)
    }

    private fun requestNextBatch(playNow: Boolean) {
        // Database access is always done from another thread.
        val future = database.async {
            provider.getNextBatch(database).map{database.mediaItem(it)}
        }
        Futures.addCallback(
            future,
            successCallback(if (playNow) ::playSongBatchNow else ::playSongBatchNext),
            ContextCompat.getMainExecutor(context))
    }

    private fun swapProvider(newProvider: SongProvider, switchNow: Boolean) {
        provider = newProvider

        // Clear out any stray songs in the "still to play" portion of the playlist
        val nextIndex = androidPlayer.nextMediaItemIndex
        if (nextIndex >= 0) {
            androidPlayer.removeMediaItems(nextIndex, Int.MAX_VALUE)
        }
        requestNextBatch(switchNow)
    }

    fun changeBandLock(forceBandId: Int? = null) {
        if (forceBandId != null || majorMode != MajorMode.BAND) {
            val bandId = forceBandId?:androidPlayer.currentMediaItem?.mediaMetadata?.extras?.getInt("band")
            if (bandId != null) {
                majorMode = MajorMode.BAND
                swapProvider(BandShuffleProvider(bandId), forceBandId != null)
                forceBandId?.let{ androidPlayer.seekToNextMediaItem() }
                androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_ARTIST).build()
            }
        } else {
            majorMode = MajorMode.COLLECTION
            swapProvider(ShuffleProvider(), false)
            androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_MIXED).build()

        }
    }

    fun changeAlbumLock(forceAlbumId: Int? = null) {
        // If an album is passed in, then we lock on that album
        // If not, then we toggle between album and shuffle mode
        if (forceAlbumId != null || majorMode != MajorMode.ALBUM) {
            val albumId = forceAlbumId?:androidPlayer.currentMediaItem?.mediaMetadata?.extras?.getInt("album")
            val songId = androidPlayer.currentMediaItem?.mediaId?.let{internalId(it)}
            if (albumId != null && albumId != 0) {
                majorMode = MajorMode.ALBUM
                swapProvider(AlbumSequentialProvider(albumId, songId), forceAlbumId != null)
                forceAlbumId?.let{ androidPlayer.seekToNextMediaItem()}
                androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_ALBUM).build()
            }
        } else {
            majorMode = MajorMode.COLLECTION
            swapProvider(ShuffleProvider(), false)
            androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_MIXED).build()
        }
    }

    fun changeSubMode() {
        if (majorMode == MajorMode.COLLECTION) {
            if (subMode == "shuffle") {
                subMode = "double-shot"
                swapProvider(DoubleShotProvider(), false)
                androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_MIXED).build()
            } else {
                subMode = "shuffle"
                swapProvider(ShuffleProvider(), false)
                androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_MIXED).build()
            }
        }
    }

    fun forcePlayItem(externalId: String) {
        val type = externalId.substringBefore(":")
        val id = internalId(externalId)
        when (type) {
            "band" -> {
                changeBandLock(id)
            }
            "album" -> {
                changeAlbumLock(id)
            }
            "song" -> {
                database.async {
                    database.songDao().get(id)?.let {
                        database.mediaItem(it)
                    }
                }.onSuccess(context) { maybeItem : MediaItem? ->
                    maybeItem?.let { item ->
                        // TODO: If we are playing in album-sequential or band-sequential mode, and this song's band/album matches, then we should stay in that mode
                        // For now, though, we just force ourselves into shuffle mode every time
                        androidPlayer.setMediaItem(item)
                        swapProvider(ShuffleProvider(), false)
                        androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_MIXED).build()
                    }
                }
            }
        }
    }

    fun release() {
        androidPlayer.release()
    }
}