package su.thepeople.musicplayer

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.session.LibraryResult
import androidx.media3.session.LibraryResult.RESULT_ERROR_BAD_VALUE
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.room.Room
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import su.thepeople.musicplayer.data.ALBUM_PREFIX
import su.thepeople.musicplayer.data.BAND_PREFIX
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.SONG_PREFIX
import su.thepeople.musicplayer.data.internalId

const val BANDS_ID = "root:bands"
const val ROOT_ID = "root"

/**
 * This class represents a "MediaLibrarySession.Callback". Despite that name, this class serves as a navigation API.  All bands/albums/songs are
 * represented as nodes in a tree, through which the user can navigate.
 *
 * This app's code is split  between the "activity" (frontend), and "service" (backend).  This class is on the service side, and it handles
 * navigation requests from the activity side. This class's job includes translates from the generic language of Android's "media" API to the
 * specific language of MCotP:
 *
 * onGetItem ->  Get information about a particular band, album, or song.
 * onGetChildren -> "Get all of this band's albums", "Get all of this album's songs", etc.
 *
 * Future directions:
 *   - Allow for navigation via date, genre, geography, style, etc.
 *   - Allow for searching?
 */
class McotpLibrarySession(private val context: Context) : MediaLibraryService.MediaLibrarySession.Callback {

    private val database = Room.databaseBuilder(context, Database::class.java, "mcotp-database").build()

    /**
     * This kicks off a full scan of the disk, if necessary. The scan is a long-running operation that is run asynchronously.
     */
    private fun startBackgroundScan() {
        if (!database.isScanned()) {
            val scanner = Scanner(context, database)
            database.async {
                scanner.fullScan()
            }
        }
    }

    /**
     * Our top-level navigation object contains an entry for each way to navigate through the collection. For now, there's only 1 option, and that
     * is via a list of Bands.  In future, we may add entries for navigating by year/decade, location, genre, style, etc.
     */
    private val rootItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(MediaMetadata.Builder()
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setDisplayTitle("Library")
            .setTitle("Library")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build())
        .build()

    /**
     * This object represents the root item when navigating via bands. This object will have one child for each band in the collection.
     */
    private val bandsItem = MediaItem.Builder()
        .setMediaId(BANDS_ID)
        .setMediaMetadata(MediaMetadata.Builder()
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setDisplayTitle("All Bands")
            .setTitle("Bands")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build())
        .build()

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<MediaItem>>
    {
        // TODO: This should really be coordinated elsewhere, perhaps in the service startup
        startBackgroundScan()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        return when {
            mediaId == ROOT_ID -> {
                Futures.immediateFuture(LibraryResult.ofItem(rootItem, null))
            }
            mediaId == BANDS_ID -> {
                Futures.immediateFuture(LibraryResult.ofItem(bandsItem, null))
            }
            mediaId.startsWith(BAND_PREFIX) -> {
                val bandId = internalId(mediaId)
                return database.async {
                    val band = database.bandDao().get(bandId)
                    val item = database.mediaItem(band)
                    LibraryResult.ofItem(item, null)
                }
            }
            mediaId.startsWith(ALBUM_PREFIX) -> {
                val albumId = internalId(mediaId)
                return database.async {
                    val album = database.albumDao().get(albumId)
                    val item = database.mediaItem(album)
                    LibraryResult.ofItem(item, null)
                }
            }
            mediaId.startsWith(SONG_PREFIX) -> {
                val songId = internalId(mediaId)
                return database.async {
                    val song = database.songDao().get(songId)
                    val item = database.mediaItem(song)
                    LibraryResult.ofItem(item, null)
                }
            }
            else -> {
                Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE))
            }
        }
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return when {
            parentId == ROOT_ID -> {
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(bandsItem), null))
            }
            parentId == BANDS_ID -> {
                return database.async {
                    val bands = database.bandDao().getAll()
                    val items = bands.map {database.mediaItem(it)}.sortedBy { it.mediaMetadata.displayTitle.toString() }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
                }
            }
            parentId.startsWith(BAND_PREFIX) -> {
                val bandId = internalId(parentId)
                return database.async {
                    val albums = database.albumDao().getAllForBand(bandId)
                    val albumItems = albums.map { database.mediaItem(it) }.sortedBy { it.mediaMetadata.displayTitle.toString() }
                    val looseSongs = database.songDao().getLooseSongsForBand(bandId)
                    val songItems = looseSongs.map {database.mediaItem(it)}.sortedBy {it.mediaMetadata.displayTitle.toString()}
                    LibraryResult.ofItemList(ImmutableList.copyOf(albumItems + songItems), null)
                }
            }
            parentId.startsWith(ALBUM_PREFIX) -> {
                val albumId = internalId(parentId)
                return database.async {
                    val songs = database.songDao().getSongsForAlbum(albumId)
                    val items = songs.map {database.mediaItem(it)}.sortedBy{it.mediaMetadata.displayTitle.toString()}
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
                }
            }
            else -> {
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), null))
            }
        }
    }
}