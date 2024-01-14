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
import su.thepeople.musicplayer.data.Album
import su.thepeople.musicplayer.data.BAND_PREFIX
import su.thepeople.musicplayer.data.Band
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.SONG_PREFIX
import su.thepeople.musicplayer.data.Song
import su.thepeople.musicplayer.data.dbId
import java.io.File

const val BANDS_ID = "root:bands"
const val ROOT_ID = "root"

class DiskLibrary(private val context: Context) : MediaLibraryService.MediaLibrarySession.Callback {

    private val database = Room.databaseBuilder(context, Database::class.java, "mcotp-database").build()

    private fun isCollectionDirectory(candidate: File) : Boolean
    {
        return candidate.isDirectory && candidate.name == "mcotp"
    }

    private fun startBackgroundScan() {
        val mcotp = findMcotp(context.externalMediaDirs)
        if (!database.isScanned()) {
            database.async {
                mcotp?.listFiles()?.forEach { bandDir ->
                    if (bandDir.isDirectory && bandDir.name != ".." && bandDir.name != "." && !bandDir.name.startsWith('[')) {
                        val band = Band(0, bandDir.name, bandDir.absolutePath)
                        val bandId = database.bandDao().insert(band).toInt()
                        bandDir.listFiles()?.forEach { childObj ->
                            if (childObj.isDirectory && childObj.name != ".." && childObj.name != "." && !childObj.name.startsWith('[')) {
                                val album = Album(0, childObj.name, childObj.absolutePath, bandId)
                                val albumId = database.albumDao().insert(album).toInt()
                                childObj.listFiles()?.forEach { songFile ->
                                    if (songFile.isFile && !songFile.name.startsWith("[") && songFile.extension != "json") {
                                        val song = Song(0, songFile.name, songFile.absolutePath, bandId, albumId)
                                        database.songDao().insert(song)
                                    }
                                }
                            } else if  (childObj.isFile && !childObj.name.startsWith("[") && childObj.extension != "json") {
                                val song = Song(0, childObj.name, childObj.absolutePath, bandId, null)
                                database.songDao().insert(song)
                            }
                        }
                    }
                }
            }
        }
    }


    private fun findMcotpViaAncestors(startChild: File) : File? {
        var current : File? = startChild
        while (current != null) {
            if (isCollectionDirectory(current)) {
                return current
            } else {
                val child = File(current, "mcotp")
                if (isCollectionDirectory(child)) {
                    return child
                }
            }
            current = current.parentFile
        }
        return null
    }

    private fun findMcotp(mediaDirs: Array<File>): File? {
        // TODO: Hack to allow working with emulated device, which is not appearing in externalMediaDirs for some reason
        val dirs = ArrayList<File>()
        dirs.addAll(mediaDirs)
        dirs.add(File("/storage/7F62-69AB"))

        dirs.forEach {
            val maybeMcotp = findMcotpViaAncestors(it)
            if (maybeMcotp != null) return maybeMcotp
        }
        return null

    }

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
                val bandId = dbId(mediaId)
                return database.async {
                    val band = database.bandDao().get(bandId)
                    val item = database.mediaItem(band)
                    LibraryResult.ofItem(item, null)
                }
            }
            mediaId.startsWith(ALBUM_PREFIX) -> {
                val albumId = dbId(mediaId)
                return database.async {
                    val album = database.albumDao().get(albumId)
                    val item = database.mediaItem(album)
                    LibraryResult.ofItem(item, null)
                }
            }
            mediaId.startsWith(SONG_PREFIX) -> {
                val songId = dbId(mediaId)
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
                val bandId = dbId(parentId)
                return database.async {
                    val albums = database.albumDao().getAllForBand(bandId)
                    val albumItems = albums.map { database.mediaItem(it) }.sortedBy { it.mediaMetadata.displayTitle.toString() }
                    val looseSongs = database.songDao().getLooseSongsForBand(bandId)
                    val songItems = looseSongs.map {database.mediaItem(it)}.sortedBy {it.mediaMetadata.displayTitle.toString()}
                    LibraryResult.ofItemList(ImmutableList.copyOf(albumItems + songItems), null)
                }
            }
            parentId.startsWith(ALBUM_PREFIX) -> {
                val albumId = dbId(parentId)
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