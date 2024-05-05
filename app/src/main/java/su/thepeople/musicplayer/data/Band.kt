package su.thepeople.musicplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.PrimaryKey

/**
 * Database class defining a band
 */
@Entity
data class Band (
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String,
    val path: String)

@Dao
interface BandDao {
    @Query("SELECT * from band ORDER BY name")
    fun getAll(): List<Band>

    @Query("SELECT * from band WHERE id = :id")
    fun get(id: Int): Band

    @Query("SELECT * from band LIMIT 1")
    fun getAny(): List<Band>

    @Query("SELECT * from band ORDER BY random() LIMIT 1")
    fun getRandomBand(): Band

    @Query("SELECT distinct substr(name,1,1) AS letter FROM band ORDER BY letter")
    fun getAllInitialCharactersFromNames(): List<String>

    @Query("SELECT * FROM band WHERE name LIKE (:letter || '%') ORDER BY name")
    fun getBandsBeginningWithLetter(letter: String): List<Band>

    @Insert
    fun insert(band: Band): Long

    @Delete
    fun delete(band: Band)
}