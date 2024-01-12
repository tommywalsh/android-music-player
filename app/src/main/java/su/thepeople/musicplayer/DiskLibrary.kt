package su.thepeople.musicplayer

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

const val BANDS_ID = "root:bands"

class DiskLibrary : MediaLibraryService.MediaLibrarySession.Callback {


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
        return Futures.immediateFuture(LibraryResult.ofItem(bandsItem, params))
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        return if (mediaId == BANDS_ID) {
            Futures.immediateFuture(LibraryResult.ofItem(bandsItem, null))
        } else {
            Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE))
        }
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), null))
    }
}