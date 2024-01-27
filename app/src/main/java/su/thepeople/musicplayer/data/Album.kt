package su.thepeople.musicplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Database class defining an album.  Note that, at least for now, each album is associated with exactly one band.
 *
 * Future directions:
 *     - Separate year from album title
 *     - support album ordering apart from year (some bands have 2+ albums in the same year)
 *     - Maybe add support for compilations? (albums with no single band)
 */
@Entity
data class Album (
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String,
    val path: String,
    val bandId: Int,
    val year: String? = null
    )

@Dao
interface AlbumDao {
    @Query("SELECT * from album WHERE bandId = :bandId ORDER BY year, name")
    fun getAllForBand(bandId: Int): List<Album>

    @Query("SELECT * from album WHERE id = :albumId")
    fun get(albumId: Int): Album

    @Query("SELECT * from album ORDER BY random() LIMIT 1")
    fun getRandomAlbum(): Album

    @Insert
    fun insert(album: Album): Long

    @Delete
    fun delete(album: Album)
}