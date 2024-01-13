package su.thepeople.musicplayer

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.session.LibraryResult
import androidx.media3.session.LibraryResult.RESULT_ERROR_BAD_VALUE
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Callable

const val BANDS_ID = "root:bands"
const val ROOT_ID = "root"

class DiskLibrary(private val context: Context) : MediaLibraryService.MediaLibrarySession.Callback {

    private fun isCollectionDirectory(candidate: File) : Boolean
    {
        return candidate.isDirectory && candidate.name == "mcotp"
    }

    private val database by lazy {
        val mcotp = findMcotp(context.externalMediaDirs)
        val db = MusicDatabase()
        mcotp?.listFiles()?.forEach {bandDir ->
            if (bandDir.isDirectory && bandDir.name != ".." && bandDir.name != "." && !bandDir.name.startsWith('[')) {
                val band = db.addBand(bandDir.name, bandDir.absoluteFile)
                bandDir.listFiles()?.forEach { childObj ->
                    if (childObj.isDirectory && childObj.name != ".." && childObj.name != "." && !childObj.name.startsWith('[')) {
                        val album = db.addAlbum(childObj.name, childObj.absoluteFile, band.mediaId)
                        childObj.listFiles()?.forEach { songFile ->
                            if (songFile.isFile && !songFile.name.startsWith("[") && songFile.extension != "json") {
                                db.addAlbumSong(songFile.name, songFile.absoluteFile, band.mediaId, album.mediaId)
                            }
                        }
                    } else if  (childObj.isFile && !childObj.name.startsWith("[") && childObj.extension != "json") {
                        db.addLooseSong(childObj.name, childObj.absoluteFile, band.mediaId)
                    }
                }
            }
        }
        db
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
            mediaId.startsWith("band:") -> {
                val task = Callable {
                    LibraryResult.ofItem(database.getBand(mediaId)!!, null)
                }
                Futures.submit(task, ContextCompat.getMainExecutor(context))
            }
            mediaId.startsWith("album:") -> {
                val task = Callable {
                    LibraryResult.ofItem(database.getAlbum(mediaId)!!, null)
                }
                Futures.submit(task, ContextCompat.getMainExecutor(context))

            }
            mediaId.startsWith("song:") -> {
                val task = Callable {
                    LibraryResult.ofItem(database.getSong(mediaId)!!, null)
                }
                Futures.submit(task, ContextCompat.getMainExecutor(context))

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
                val task = Callable {
                    LibraryResult.ofItemList(ImmutableList.copyOf(database.getBands()), null)
                }
                Futures.submit(task, ContextCompat.getMainExecutor(context))
            }
            parentId.startsWith("band:") -> {
                val task = Callable {
                    val children = database.getAlbumsForBand(parentId) + database.getLooseSongsForBand(parentId)
                    LibraryResult.ofItemList(ImmutableList.copyOf(children), null)
                }
                Futures.submit(task, ContextCompat.getMainExecutor(context))
            }
            parentId.startsWith("album:") -> {
                val task = Callable {
                    LibraryResult.ofItemList(ImmutableList.copyOf(database.getAlbumSongs(parentId)), null)
                }
                Futures.submit(task, ContextCompat.getMainExecutor(context))
            }
            else -> {
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), null))
            }
        }
    }
}