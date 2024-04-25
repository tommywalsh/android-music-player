package su.thepeople.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MIXED
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.internalId

enum class MajorMode {
    COLLECTION,
    BAND,
    ALBUM,
    YEAR
}

class PlayerRestartConfig(val songProviderState: SongProviderState, val songId: String?) {
    fun persist(prefs : SharedPreferences, prefix: String) {
        with (prefs.edit()) {
            putString(prefix + "song_id", songId)
            putString(prefix + "provider_type", songProviderState.providerClass.toString())
            var i = 0
            songProviderState.optionalParams?.forEach {
                putInt(prefix + "param" + i.toString(), it)
                i += 1
            }
            putInt(prefix + "param" + i.toString(), -1)
            apply()
        }
    }
}

fun recoverConfig(prefs: SharedPreferences, prefix: String) : PlayerRestartConfig? {
    val songId = prefs.getString(prefix + "song_id", null) ?: return null
    val providerClass = prefs.getString(prefix + "provider_type", null) ?: return null
    val params = ArrayList<Int>()
    var i = 0
    while (true) {
        val param = prefs.getInt(prefix + "param" + i.toString(), -1)
        if (param == -1) break
        params.add(param)
        i += 1
    }
    return PlayerRestartConfig(SongProviderState(SongProviderState.ProviderClass.valueOf(providerClass), params), songId)
}

/**
 * The Android "media3" API mushed together two different concepts: music playback, and library exploration.  This class
 * handles solely the music playback.
 *
 * The Android-provided player object works on the concept of a "playlist", which we try to hide from the end user. Instead, our end user is presented
 * with "play modes" (random-playing the entire library, playing an album, etc.).  We interact with the player by loading more items onto the end of
 * its playlist, and removing any already-played items from the beginning of the playlist.
 */
class CustomPlayer(private val database: Database, private val context: Context, config: PlayerRestartConfig?) {

    private val androidPlayer = ExoPlayer.Builder(context).build()

    private var provider: SongProvider = ShuffleProvider()

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
        Log.d("McotpService","Initializing player")
        config?.let{ goodConfig ->
            Log.d("McotpService","Initializing player with config")
            val initialSongId = goodConfig.songId?.let{ extId -> internalId(extId)}
            initialSongId?.let {songId ->
                Log.d("McotpService","Initializing player with song ID %d".format(songId))
                Futures.addCallback(
                    database.async {database.songDao().get(songId)?.let{database.mediaItem(it)}},
                    successCallback{mediaItem ->
                        Log.d("McotpService","Got media item from database".format(songId))
                        mediaItem?.let{androidPlayer.setMediaItem(it)}
                        initProvider(goodConfig)},
                    ContextCompat.getMainExecutor(context))
            } ?: initProvider(goodConfig)
        } ?: requestNextBatch(false)
        androidPlayer.addListener(transitionListener)
    }

    private fun appendSongBatch(songs: List<MediaItem>) {
        androidPlayer.addMediaItems(songs)
        androidPlayer.prepare()
    }

    private fun onIncomingSongBatch(songs: List<MediaItem>, playNow: Boolean) {
        Log.d("Player", "Received song list with ${songs.size} items")
        if (songs.isEmpty()) {
            // An empty song list is a signal that the provider has completed its work and should be swapped out
            Log.d("Player", "Swapping back to shuffle provider")
            androidPlayer.playlistMetadata = MediaMetadata.Builder().setMediaType(MEDIA_TYPE_MIXED).build()
            swapProvider(ShuffleProvider(), playNow)
        } else {
            appendSongBatch(songs)

            // Clear out any stale songs from the front of the playlist that we've already played.
            val lastPlayedIndex = androidPlayer.nextMediaItemIndex - 2
            if (lastPlayedIndex > 0) {
                androidPlayer.removeMediaItems(0, lastPlayedIndex)
            }

            if (playNow) {
                Log.d("Player", "Playing new batch immediately")
                androidPlayer.seekToNextMediaItem()
            }
        }
    }

    private fun requestNextBatch(playNow: Boolean) {
        // Database access is always done from another thread.
        val future = database.async {
            provider.getNextBatch(database).map{database.mediaItem(it)}
        }
        Futures.addCallback(
            future,
            successCallback{ songs -> onIncomingSongBatch(songs, playNow)},
            ContextCompat.getMainExecutor(context))
    }

    private fun initProvider(config: PlayerRestartConfig) {
        provider = when(config.songProviderState.providerClass) {
            SongProviderState.ProviderClass.BAND_SHUFFLE -> BandShuffleProvider(config.songProviderState.optionalParams!![0])
            SongProviderState.ProviderClass.YEAR_SHUFFLE -> YearRangeShuffleProvider(config.songProviderState.optionalParams!![0], config.songProviderState.optionalParams[1])
            SongProviderState.ProviderClass.BLOCK_PARTY -> BlockPartyProvider()
            SongProviderState.ProviderClass.DOUBLE_SHOT -> DoubleShotProvider()
            SongProviderState.ProviderClass.ALBUM_SEQUENTIAL -> AlbumSequentialProvider(config.songProviderState.optionalParams!![0], config.songId?.let{internalId(it)})
            else -> ShuffleProvider()
        }
        swapProvider(provider, false)
    }

    private fun swapProvider(newProvider: SongProvider, switchNow: Boolean) {
        provider = newProvider
        androidPlayer.playlistMetadata = MediaMetadata.Builder()
            .setMediaType(provider.mediaType)
            .setTitle(provider.subTypeLabel)
            .build()

        // Clear out any stray songs in the "still to play" portion of the playlist
        val nextIndex = androidPlayer.nextMediaItemIndex
        if (nextIndex >= 0) {
            androidPlayer.removeMediaItems(nextIndex, Int.MAX_VALUE)
        }
        requestNextBatch(switchNow)
    }

    fun changeBandLock(forceBandId: Int? = null) {
        if (forceBandId != null || provider.mode != MajorMode.BAND) {
            val bandId = forceBandId?:androidPlayer.currentMediaItem?.mediaMetadata?.extras?.getInt("band")
            if (bandId != null) {
                swapProvider(BandShuffleProvider(bandId), forceBandId != null)
                forceBandId?.let{ androidPlayer.seekToNextMediaItem() }
            }
        } else {
            swapProvider(ShuffleProvider(), false)
        }
    }

    fun getRestartConfig() : PlayerRestartConfig {
        val providerState  = provider.getRestartConfig()
        return PlayerRestartConfig(providerState, androidPlayer.currentMediaItem?.mediaId)
    }

    fun changeYearLock() {
        val year = androidPlayer.currentMediaItem?.mediaMetadata?.releaseYear
        if (year == null || provider.mode == MajorMode.YEAR) {
            swapProvider(ShuffleProvider(), false)
        } else {
            swapProvider(YearShuffleProvider(year), false)
        }
    }

    fun changeAlbumLock(forceAlbumId: Int? = null) {
        // If an album is passed in, then we lock on that album
        // If not, then we toggle between album and shuffle mode
        if (forceAlbumId != null || provider.mode != MajorMode.ALBUM) {
            val albumId = forceAlbumId?:androidPlayer.currentMediaItem?.mediaMetadata?.extras?.getInt("album")
            val songId = androidPlayer.currentMediaItem?.mediaId?.let{internalId(it)}
            if (albumId != null && albumId != 0) {
                swapProvider(AlbumSequentialProvider(albumId, songId), forceAlbumId != null)
                forceAlbumId?.let{ androidPlayer.seekToNextMediaItem()}
            }
        } else {
            swapProvider(ShuffleProvider(), false)
        }
    }

    fun changeSubMode() {
        // TODO: Should we have a mode interface, where each main mode knows its default submode, plus full list of submodes?
        if (provider.mode == MajorMode.COLLECTION) {
            when (provider.subTypeLabel) {
                DoubleShotProvider.subType -> {
                    swapProvider(BlockPartyProvider(), false)
                }
                BlockPartyProvider.subType -> {
                    swapProvider(ShuffleProvider(), false)
                }
                else -> {
                    swapProvider(DoubleShotProvider(), false)
                }
            }
        }
    }

    fun forcePause() {
        androidPlayer.pause()
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
            "decade" -> {
                swapProvider(DecadeShuffleProvider(id), true)
            }
            "year" -> {
                swapProvider(YearShuffleProvider(id), true)
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
                    }
                }
            }
        }
    }

    fun release() {
        androidPlayer.release()
    }
}