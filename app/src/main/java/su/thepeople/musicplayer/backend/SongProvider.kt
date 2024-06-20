package su.thepeople.musicplayer.backend

import android.util.Log
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_YEAR
import com.google.common.collect.Lists
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.Song
import kotlin.random.Random

class SongProviderState(val providerClass : ProviderClass, val optionalParams : List<Int>? = null) {
    enum class ProviderClass {
        CATALOG_SHUFFLE,
        BAND_SHUFFLE,
        YEAR_SHUFFLE,
        ALBUM_SEQUENTIAL,
        DOUBLE_SHOT,
        BLOCK_PARTY
    }
}

abstract class SongProvider {
    // Subclasses must implement this function.
    // TODO: should there be a way for an implementation to signal that there are no more songs?
    abstract fun getNextBatch(database: Database): List<Song>

    abstract fun getRestartConfig(): SongProviderState

    abstract val mode: MajorMode
    abstract val mediaType: Int // From Android's MediaMetadata.MEDIA_TYPE_XXX definitions
    open val subTypeLabel = ""
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

    override fun getNextBatch(database: Database): List<Song> {
        return (0..PREFERRED_BATCH_SIZE).map {
            when (Random.nextInt(0,3)) {
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

    override fun getRestartConfig(): SongProviderState {
        return SongProviderState(SongProviderState.ProviderClass.CATALOG_SHUFFLE)
    }
}

class BandShuffleProvider(private val bandId: Long): SongProvider() {
    override val mode = MajorMode.BAND
    override val mediaType = MEDIA_TYPE_ARTIST

    override fun getNextBatch(database: Database): List<Song> {
        return database.songDao().getRandomSongsForBand(bandId, PREFERRED_BATCH_SIZE)
    }

    override fun getRestartConfig(): SongProviderState {
        return SongProviderState(SongProviderState.ProviderClass.BAND_SHUFFLE, Lists.newArrayList(bandId.toInt()))
    }
}

open class YearRangeShuffleProvider(private val startYear: Int, private val endYear: Int): SongProvider() {
    override val mode = MajorMode.YEAR
    override val mediaType = MEDIA_TYPE_YEAR
    override fun getNextBatch(database: Database): List<Song> {
        return database.songDao().getSongsForYearRange(startYear, endYear, PREFERRED_BATCH_SIZE)
    }

    override fun getRestartConfig(): SongProviderState {
        return SongProviderState(SongProviderState.ProviderClass.YEAR_SHUFFLE, Lists.newArrayList(startYear, endYear))
    }
}

class DecadeShuffleProvider(startYear: Int): YearRangeShuffleProvider(startYear, startYear + 9)
class YearShuffleProvider(year: Int): YearRangeShuffleProvider(year, year)

class AlbumSequentialProvider(private val albumId: Long, private var currentSongId: Long? = null): SongProvider() {
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

    override fun getNextBatch(database: Database): List<Song> {
        return if (isCompleted) {
            Log.d("SongProvider", "Album provider has already completed.")
            ArrayList()
        } else {
            isCompleted = true
            Log.d("SongProvider", "Album provider starting.")
            currentSongId?.let{id->getPartitionedAlbum(database, id)} ?: getFullAlbum(database)
        }
    }

    override fun getRestartConfig(): SongProviderState {
        return SongProviderState(SongProviderState.ProviderClass.ALBUM_SEQUENTIAL, Lists.newArrayList(albumId.toInt()))
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


    override fun getNextBatch(database: Database): List<Song> {
        val band = database.bandDao().getRandomBand()
        Log.d("SongProvider", "Requesting 2 songs for band ${band.id} ${band.name}")
        return database.songDao().getRandomSongsForBand(band.id, 2)
    }

    override fun getRestartConfig(): SongProviderState {
        return SongProviderState(SongProviderState.ProviderClass.DOUBLE_SHOT)
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

    override fun getNextBatch(database: Database): List<Song> {
        val songs = if (doBlockNext) {
            val band = database.bandDao().getRandomBand()
            database.songDao().getRandomSongsForBand(band.id, blockSize)
        } else {
            database.songDao().getRandomSongs(blockSize)
        }
        doBlockNext = !doBlockNext
        return songs
    }

    override fun getRestartConfig(): SongProviderState {
        return SongProviderState(SongProviderState.ProviderClass.BLOCK_PARTY)
    }
}
