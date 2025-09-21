package su.thepeople.musicplayer.backend

import android.util.Log
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_YEAR
import org.json.JSONObject
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.Song
import kotlin.random.Random

enum class ProviderClass {
    CATALOG_SHUFFLE,
    BAND_SHUFFLE,
    BAND_SEQUENTIAL,
    YEAR_SHUFFLE,
    ALBUM_SEQUENTIAL,
    DOUBLE_SHOT,
    BLOCK_PARTY,
    LOCATION_SHUFFLE
}
abstract class SongProvider(initialSongId: Long? = null) {

    private var forcedSongId = initialSongId

    // Subclasses must implement this function.
    // TODO: should there be a way for an implementation to signal that there are no more songs?
    protected abstract fun getNextBatchImpl(database: Database): List<Song>

    fun getNextBatch(database: Database): List<Song> {
        val batch = forcedSongId?.let {
            getSingleSong(database, it)
        }?: getNextBatchImpl(database)
        forcedSongId = null
        return batch
    }

    abstract fun getInternalState(): JSONObject

    abstract val mode: MajorMode
    abstract val mediaType: Int // From Android's MediaMetadata.MEDIA_TYPE_XXX definitions
    open val subTypeLabel = ""

    private fun getSingleSong(database:Database, songId: Long): List<Song> {
        return listOf(database.songDao().get(songId)!!)
    }

    companion object {
        fun fromSavedState(savedState: JSONObject): SongProvider {
            return when(savedState.optInt("type", ProviderClass.CATALOG_SHUFFLE.ordinal)) {
                ProviderClass.CATALOG_SHUFFLE.ordinal -> ShuffleProvider.reconstruct()
                ProviderClass.BAND_SHUFFLE.ordinal -> BandShuffleProvider.reconstruct(savedState)
                ProviderClass.BAND_SEQUENTIAL.ordinal -> BandSequentialProvider.reconstruct(savedState)
                ProviderClass.YEAR_SHUFFLE.ordinal -> YearRangeShuffleProvider.reconstruct(savedState)
                ProviderClass.LOCATION_SHUFFLE.ordinal -> LocationShuffleProvider.reconstruct(savedState)
                ProviderClass.BLOCK_PARTY.ordinal -> BlockPartyProvider()
                ProviderClass.DOUBLE_SHOT.ordinal -> DoubleShotProvider()
                ProviderClass.ALBUM_SEQUENTIAL.ordinal -> AlbumSequentialProvider.reconstruct(savedState)
                else -> ShuffleProvider.reconstruct()
            }
        }
    }
}

const val PREFERRED_BATCH_SIZE = 10

class ShuffleProvider: SongProvider() {
    override val mode = MajorMode.COLLECTION
    override val mediaType = MEDIA_TYPE_MIXED

    /**
     * There are three techniques for selecting a random song:
     * 1) "unweighted". Put all the songs into a box and choose one at random.
     *     This is biased towards bands that have lots of short songs
     * 2) "band-weighted". Choose a band at random, then choose a song by that band.
     *     This is biased towards songs by bands for whom we only have a few songs.
     * 3) "album-weighted".  Choose an album at random, then choose a song off of that album.
     *     This is biased towards bands with lots of albums in the collection
     */
    private fun getUnweightedSong(database: Database): Song {
        return database.songDao().getRandomSong()
    }

    private fun getBandWeightedSong(database: Database): Song {
        val band = database.bandDao().getRandomBand()
        return database.songDao().getRandomSongForBand(band.id)
    }

    private fun getAlbumWeightedSong(database: Database): Song {
        val album = database.albumDao().getRandomAlbum()
        return database.songDao().getRandomSongForAlbum(album.id)
    }

    override fun getNextBatchImpl(database: Database): List<Song> {
        return (0..PREFERRED_BATCH_SIZE).map {
            // Use unweighted strategy appx. twice as often as the other two
            when (Random.nextInt(0,4)) {
                0 -> {
                    getAlbumWeightedSong(database)
                }
                1 -> {
                    getBandWeightedSong(database)
                }
                else -> {
                    getUnweightedSong(database)
                }
            }
        }
    }

    override fun getInternalState(): JSONObject {
        return JSONObject().put("type", ProviderClass.CATALOG_SHUFFLE.ordinal)
    }

    companion object {
        fun reconstruct(): ShuffleProvider {
            return ShuffleProvider()
        }
    }
}

class BandShuffleProvider(private val bandId: Long): SongProvider() {
    override val mode = MajorMode.BAND
    override val mediaType = MEDIA_TYPE_ARTIST

    override fun getNextBatchImpl(database: Database): List<Song> {
        return database.songDao().getRandomSongsForBand(bandId, PREFERRED_BATCH_SIZE)
    }

    override fun getInternalState(): JSONObject {
        return JSONObject()
            .put("type", ProviderClass.BAND_SHUFFLE.ordinal)
            .put("bandId", bandId)
    }

    companion object {
        fun reconstruct(savedState: JSONObject): BandShuffleProvider {
            return BandShuffleProvider(savedState.getLong("bandId"))
        }
    }
}

class BandSequentialProvider(private val bandId: Long, private val forcedStartSongId: Long? = null): SongProvider() {
    override val mode = MajorMode.BAND
    override val mediaType = MEDIA_TYPE_ARTIST

    override val subTypeLabel: String
        get() {return subType }

    /**
     * This provider has two modes:
     * - Play all the bands songs in chronological order, starting from the earliest and ending at the latest.
     * - Play in chronological order, starting from the given song to the latest, then loop back and play from the earliest, to just before the given song. Then terminate.
     */

    private var isCompleted = false

    private fun getFullList(database: Database): List<Song> {
        return database.songDao().getSequentialSongsForBand(bandId)
    }

    private fun getPartitionedSongs(database: Database, partitionId: Long): List<Song> {
        val fullList = getFullList(database)
        val earlyList = ArrayList<Song>()
        val lateList = ArrayList<Song>()
        var workingList = earlyList
        fullList.forEach{ song ->
            if (song.id == partitionId) {
                // We've completed the early part of the album. Next song will be on the late part.
                workingList = lateList
            } else {
                workingList.add(song)
            }
        }
        return lateList + earlyList
    }

    override fun getNextBatchImpl(database: Database): List<Song> {
        return if (isCompleted) {
            Log.d("SongProvider", "Sequential provider has already completed.")
            ArrayList()
        } else {
            isCompleted = true
            Log.d("SongProvider", "Sequential provider starting.")
            forcedStartSongId?.let{id->getPartitionedSongs(database, id)} ?: getFullList(database)
        }
    }

    override fun getInternalState(): JSONObject {
        val state = JSONObject()
            .put("type", ProviderClass.BAND_SEQUENTIAL.ordinal)
            .put("bandId", bandId)
            .put("isCompleted", isCompleted)
        forcedStartSongId?.let{state.put("forcedStartSongId", it)}
        return state
    }

    companion object {
        const val subType = "Sequential Mode"
        fun reconstruct(savedState: JSONObject): BandSequentialProvider {
            val songId = savedState.optLong("forcedStartSongId", -1L)
            val songParam = if (songId == -1L) null else songId
            val provider = BandSequentialProvider(savedState.getLong("bandId"), songParam)
            provider.isCompleted = savedState.getBoolean("isCompleted")
            return provider
        }
    }

}

open class YearRangeShuffleProvider(private val startYear: Int, private val endYear: Int): SongProvider() {
    override val mode = MajorMode.YEAR
    override val mediaType = MEDIA_TYPE_YEAR
    override fun getNextBatchImpl(database: Database): List<Song> {
        return database.songDao().getSongsForYearRange(startYear, endYear, PREFERRED_BATCH_SIZE)
    }

    override fun getInternalState(): JSONObject {
        return JSONObject()
            .put("type", ProviderClass.YEAR_SHUFFLE.ordinal)
            .put("startYear", startYear)
            .put("endYear", endYear)
    }

    companion object {
        fun reconstruct(savedState: JSONObject): YearRangeShuffleProvider {
            return YearRangeShuffleProvider(savedState.getInt("startYear"), savedState.getInt("endYear"))
        }
    }
}


open class LocationShuffleProvider(private val locationId: Long): SongProvider() {
    override val mode = MajorMode.LOCATION
    override val mediaType = MEDIA_TYPE_FOLDER_MIXED
    data class LookupData(val bandIds: List<Long>, val locationLabel: String)
    private var lookupData: LookupData? = null

    override fun getNextBatchImpl(database: Database): List<Song> {
        if (lookupData == null) {
            val locationIds = database.locationDao().getAllDescendentIds(locationId)
            val bandIds = database.bandDao().getBandIdsFromLocations(locationIds)
            val locString = database.locationDao().getFullLocationString(locationId)
            lookupData = LookupData(bandIds, locString)
        }
        return database.songDao().getRandomSongsForBands(lookupData!!.bandIds, PREFERRED_BATCH_SIZE)
    }

    override val subTypeLabel: String
        get() = lookupData?.locationLabel?:"Location Lock"

    override fun getInternalState(): JSONObject {
        return JSONObject()
            .put("type", ProviderClass.LOCATION_SHUFFLE.ordinal)
            .put("locationId", locationId)
    }

    companion object {
        fun reconstruct(savedState: JSONObject): LocationShuffleProvider {
            return LocationShuffleProvider(savedState.getLong("locationId"))
        }
    }
}


class DecadeShuffleProvider(startYear: Int): YearRangeShuffleProvider(startYear, startYear + 9)
class YearShuffleProvider(year: Int): YearRangeShuffleProvider(year, year)

class AlbumSequentialProvider(private val albumId: Long, private var currentSongId: Long? = null):
    SongProvider()
{
    override val mode = MajorMode.ALBUM
    override val mediaType = MEDIA_TYPE_ALBUM

    /**
     * This provider has two modes:
     * - Play the album all the way through, from the start, then terminate
     * - Play the end of the album, after the given starting song. Then, play the beginning of the album, before the starting song. Then terminate.
     */

    private var isCompleted = false

    private fun getFullAlbum(database: Database): List<Song> {
        return database.songDao().getSongsForAlbum(albumId)
    }

    private fun getPartitionedAlbum(database: Database, partitionId: Long): List<Song> {
        val fullAlbum = getFullAlbum(database)
        val earlyAlbum = ArrayList<Song>()
        val lateAlbum = ArrayList<Song>()
        var workingList = earlyAlbum
        fullAlbum.forEach{ song ->
            if (song.id == partitionId) {
                // We've completed the early part of the album. Next song will be on the late part.
                workingList = lateAlbum
            } else {
                workingList.add(song)
            }
        }
        return lateAlbum + earlyAlbum
    }

    override fun getNextBatchImpl(database: Database): List<Song> {
        return if (isCompleted) {
            Log.d("SongProvider", "Album provider has already completed.")
            ArrayList()
        } else {
            isCompleted = true
            Log.d("SongProvider", "Album provider starting.")
            currentSongId?.let{id->getPartitionedAlbum(database, id)} ?: getFullAlbum(database)
        }
    }

    override fun getInternalState(): JSONObject {
        val state = JSONObject()
            .put("type", ProviderClass.ALBUM_SEQUENTIAL.ordinal)
            .put("albumId", albumId)
            .put("isCompleted", isCompleted)
        currentSongId?.let{state.put("partitionId", it)}
        return state
    }

    companion object {
        fun reconstruct(savedState: JSONObject): AlbumSequentialProvider {
            val provider = AlbumSequentialProvider(savedState.getLong("albumId"))
            val partition = savedState.optLong("partitionId", -1L)
            if (partition != -1L) {
                provider.currentSongId = partition
            }
            provider.isCompleted = savedState.getBoolean("isCompleted")
            return provider
        }

    }
}

class DoubleShotProvider: SongProvider() {
    override val mode = MajorMode.COLLECTION
    override val mediaType = MEDIA_TYPE_MIXED
    companion object {
        const val subType = "Double-Shot Weekend"
    }
    override val subTypeLabel: String
        get() {return subType }


    override fun getNextBatchImpl(database: Database): List<Song> {
        val band = database.bandDao().getRandomBand()
        Log.d("SongProvider", "Requesting 2 songs for band ${band.id} ${band.name}")
        return database.songDao().getRandomSongsForBand(band.id, 2)
    }

    override fun getInternalState(): JSONObject {
        return JSONObject()
            .put("type", ProviderClass.DOUBLE_SHOT.ordinal)
    }
}

class BlockPartyProvider: SongProvider() {
    override val mode = MajorMode.COLLECTION
    override val mediaType = MEDIA_TYPE_MIXED
    companion object {
        const val subType = "Block Party Weekend"
        private const val blockSize = 5
    }

    override val subTypeLabel: String
        get() {return subType }

    private var doBlockNext = true

    private fun getAnyBlock(database:Database): List<Song> {
        val band = database.bandDao().getRandomBand()
        return database.songDao().getRandomSongsForBand(band.id, blockSize)
    }

    private fun getFullBlock(database: Database): List<Song> {
        // Try to find a band with enough songs to fill the block count, but only try a few times
        for (i in 1..5) {
            val list = getAnyBlock(database)
            if (list.size >= blockSize) {
                return list
            }
        }
        // So far, no success for a large-enough block. Try once more and accept whatever we find
        return getAnyBlock(database)
    }

    override fun getNextBatchImpl(database: Database): List<Song> {
        val songs = if (doBlockNext) {
            getFullBlock(database)
        } else {
            database.songDao().getRandomSongs(blockSize)
        }
        doBlockNext = !doBlockNext
        return songs
    }

    override fun getInternalState(): JSONObject {
        return JSONObject()
            .put("type", ProviderClass.BLOCK_PARTY.ordinal)
    }
}
