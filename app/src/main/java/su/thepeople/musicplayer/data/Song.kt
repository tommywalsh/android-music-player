package su.thepeople.musicplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String,
    val path: String,
    val bandId: Int,
    val albumId: Int?
)


@Dao
interface SongDao {
    @Query("SELECT * from song WHERE bandId = :bandId and albumId IS NULL")
    fun getLooseSongsForBand(bandId: Int): List<Song>

    @Query("SELECT * from song WHERE albumId = :albumId")
    fun getSongsForAlbum(albumId: Int): List<Song>

    @Query("SELECT * from song WHERE id = :songId")
    fun get(songId: Int): Song

    @Query("SELECT * from song ORDER BY random() LIMIT :numSongs")
    fun getRandom(numSongs: Int): List<Song>

    @Insert
    fun insert(song: Song): Long

    @Delete
    fun delete(song: Song)
}