package su.thepeople.musicplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Database class defining a song.  Note that each song is associated with exactly one band. The song may or may not be associated with an album.
 *
 * Future directions:
 *   - The year of each song should be tracked. At first, probably album songs should be required to have the same year as albums... but a "loose"
 *     song would need to track its own year.
 *   - We need to model track numbers for songs on albums. The correct way to do that in a database is probably to introduce an additional database
 *     table to handle the cross-references. Each entry in the table would have (album_id, song_id, track_num).
 */
@Entity
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val path: String,
    val bandId: Long,
    val year: String? = null,
    val albumId: Long? = null,
    val albumTrackNum: Int? = null
)


@Dao
interface SongDao {
    @Query("SELECT * from song WHERE bandId = :bandId and albumId IS NULL ORDER BY year, name")
    fun getLooseSongsForBand(bandId: Long): List<Song>

    @Query("SELECT * from song WHERE albumId = :albumId ORDER BY albumTrackNum")
    fun getSongsForAlbum(albumId: Long): List<Song>

    @Query("SELECT * from song WHERE id = :songId")
    fun get(songId: Long): Song?

    @Query("SELECT * from song ORDER BY random() LIMIT 1")
    fun getRandomSong(): Song

    @Query("SELECT * from song ORDER BY random() LIMIT :maxSongs")
    fun getRandomSongs(maxSongs: Int): List<Song>

    @Query("SELECT * from song WHERE bandId = :bandId ORDER BY random() LIMIT :maxSongs")
    fun getRandomSongsForBand(bandId: Long, maxSongs: Int): List<Song>

    @Query("SELECT * from song WHERE bandId = :bandId ORDER BY random() LIMIT 1")
    fun getRandomSongForBand(bandId: Long): Song

    @Query("SELECT * from song WHERE albumId = :albumId ORDER BY random() LIMIT 1")
    fun getRandomSongForAlbum(albumId: Long): Song

    @Query("SELECT DISTINCT (year/10)*10 from Song WHERE year > 1900 ORDER BY year")
    fun getDecades(): List<Int>

    @Query("SELECT DISTINCT CAST(year AS integer) from Song WHERE year >= :decade AND year < :decade + 10 ORDER BY year")
    fun getYearsForDecade(decade: Int): List<Int>

    @Query("SELECT * from song WHERE year >= :startYear AND year <= :endYear ORDER BY random() LIMIT :maxSongs")
    fun getSongsForYearRange(startYear: Int, endYear: Int, maxSongs: Int): List<Song>

    @Insert
    fun insert(song: Song): Long

    @Delete
    fun delete(song: Song)
}