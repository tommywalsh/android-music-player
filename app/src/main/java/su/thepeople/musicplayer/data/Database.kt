package su.thepeople.musicplayer.data

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Database
import androidx.room.RoomDatabase
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executors

const val NEW_OBJ_ID = 0

@Database(entities = [Band::class, Album::class, Song::class], version = 1)
abstract class Database : RoomDatabase() {
    private var executor = Executors.newSingleThreadExecutor()
    abstract fun bandDao(): BandDao
    abstract fun albumDao(): AlbumDao
    abstract fun songDao(): SongDao

    fun <T> async (task: ()->T): ListenableFuture<T> {
        return Futures.submit(Callable{task()}, executor)
    }

    fun mediaItem(band: Band): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtist(band.name)
            .setTitle(band.name)
            .setDisplayTitle(band.name)
            .build()
        return MediaItem.Builder()
            .setMediaId(band.externalId())
            .setMediaMetadata(metadata)
            .setUri(band.path)
            .build()
    }

    fun mediaItem(album: Album): MediaItem {
        val band = bandDao().get(album.bandId)
        val metadata = MediaMetadata.Builder()
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtist(band.name)
            .setTitle(album.name)
            .setAlbumTitle(album.name)
            .setDisplayTitle(album.name)
            .build()
        return MediaItem.Builder()
            .setMediaId(album.externalId())
            .setMediaMetadata(metadata)
            .setUri(album.path)
            .build()
    }

    fun mediaItem(song: Song): MediaItem {
        val band = bandDao().get(song.bandId)
        val builder = MediaMetadata.Builder()
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtist(band.name)
            .setTitle(song.name)
            .setDisplayTitle(song.name)
        if (song.albumId != null) {
            val album = albumDao().get(song.albumId)
            builder.setAlbumTitle(album.name)
        }
        val metadata = builder.build()
        return MediaItem.Builder()
            .setMediaId(song.externalId())
            .setMediaMetadata(metadata)
            .setUri(song.path)
            .build()
    }

    // This must be a fast query as it is intended to be run on the main thread
    fun isScanned(): Boolean {
        val maybeBands = async {bandDao().getAny()}.get()
        return maybeBands.isNotEmpty()
    }
}
