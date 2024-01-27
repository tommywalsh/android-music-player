package su.thepeople.musicplayer.data

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Database
import androidx.room.RoomDatabase
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Our database types all have integer IDs that are automatically assigned when the object is added to the database. That causes a chicken/egg
 * problem.  You have to create the in-memory object (with ID), before you can add it to the DB. But, you only get the ID AFTER you add to the DB.
 * To solve this, newly-created in-memory objects can use this NEW_OBJ_ID as a temporary placeholder until the database object is created.  Like so:
 *
 *   val newBand = Band(NEW_OBJ_ID, "Band Name", "/path/to/band/dir")
 *   val newBandId = bandDao.insert(newBand)
 */
const val NEW_OBJ_ID = 0

/**
 * Database holding information about the Music Collection of the People
 */
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
            .setIsPlayable(true)
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

    private fun dbIdBundle(song: Song): Bundle {
        val bundle = Bundle()
        bundle.putInt("band", song.bandId)
        if (song.albumId != null) {
            bundle.putInt("album", song.albumId)
        }
        bundle.putInt("song", song.id)
        return bundle
    }

    fun mediaItem(album: Album): MediaItem {
        val band = bandDao().get(album.bandId)
        val builder = MediaMetadata.Builder()
            .setIsBrowsable(true)
            .setIsPlayable(true)
            .setArtist(band.name)
            .setTitle(album.name)
            .setAlbumTitle(album.name)
            .setDisplayTitle(album.name)
        album.year?.let{builder.setReleaseYear(it.toInt())}
        return MediaItem.Builder()
            .setMediaId(album.externalId())
            .setMediaMetadata(builder.build())
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
            .setExtras(dbIdBundle(song))
            .setTrackNumber(song.albumTrackNum?:0)
        if (song.albumId != null) {
            val album = albumDao().get(song.albumId)
            builder.setAlbumTitle(album.name)
            album.year?.let{builder.setReleaseYear(it.toInt())}
        } else {
            song.year?.let{builder.setReleaseYear(it.toInt())}
        }
        builder.setTrackNumber(song.albumTrackNum?:0)
        return MediaItem.Builder()
            .setMediaId(song.externalId())
            .setMediaMetadata(builder.build())
            .setUri(song.path)
            .build()
    }

    // This must be a fast query as it is intended to be run on the main thread
    fun isScanned(): Boolean {
        val maybeBands = async {bandDao().getAny()}.get()
        return maybeBands.isNotEmpty()
    }
}
