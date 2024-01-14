package su.thepeople.musicplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity
data class Album (
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String,
    val path: String,
    val bandId: Int)

@Dao
interface AlbumDao {
    @Query("SELECT * from album WHERE bandId = :bandId")
    fun getAllForBand(bandId: Int): List<Album>

    @Query("SELECT * from album WHERE id = :albumId")
    fun get(albumId: Int): Album

    @Insert
    fun insert(album: Album): Long

    @Delete
    fun delete(album: Album)
}