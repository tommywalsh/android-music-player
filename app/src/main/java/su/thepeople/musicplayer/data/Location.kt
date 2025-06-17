package su.thepeople.musicplayer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity
data class Location (
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val parentId: Long? = null
)

@Entity(primaryKeys = ["bandId", "locationId"])
data class BandLocationCrossRef(
    val bandId: Long,
    val locationId: Long
)

@Dao
interface LocationDao {
    @Insert
    fun insert(location: Location): Long

    @Insert
    fun addBandLocation(blxr: BandLocationCrossRef): Long

    @Query("SELECT * FROM location WHERE id = :locationId")
    fun get(locationId: Long): Location

    @Query("SELECT id FROM location WHERE parentId = :parentId")
    fun getImmediateChildIds(parentId: Long): List<Long>

    @Query("SELECT id FROM location WHERE parentId IN (:parentIds)")
    fun getImmediateChildIds(parentIds: List<Long>): List<Long>

    @Query("SELECT * FROM location WHERE parentId = :parentId")
    fun getImmediateChildren(parentId: Long): List<Location>

    fun getAllDescendentIds(parentId: Long): List<Long> {
        val allDescendants = ArrayList<Long>()
        allDescendants.add(parentId)
        var childList = getImmediateChildIds(parentId)
        while (childList.isNotEmpty()) {
            allDescendants.addAll(childList)
            childList = getImmediateChildIds(childList)
        }
        return allDescendants
    }

    fun getFullLocationString(locationId: Long): String {
        var location = get(locationId)
        var locationString = location.name
        while (location.parentId != null) {
            location = get(location.parentId!!)
            locationString = location.name + "/" + locationString
        }
        return locationString
    }

    @Query("SELECT * FROM location where parentId IS NULL ORDER BY name")
    fun getTopLevelLocations(): List<Location>

    @Query("SELECT id FROM location WHERE name = :name AND parentId IS NULL")
    fun getUnparentedId(name: String): Long?

    @Query("SELECT id from location WHERE name = :name AND parentId = :parentId")
    fun getParentedId(name: String, parentId: Long): Long?

    fun getId(name: String, parentId: Long? = null): Long? {
        return if (parentId == null) {
            getUnparentedId(name)
        } else {
            getParentedId(name, parentId)
        }
    }
}