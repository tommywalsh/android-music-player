package su.thepeople.musicplayer

import android.util.Log
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.Song
import kotlin.random.Random

fun interface SongProvider {
    // Subclasses must implement this function.
    // TODO: should there be a way for an implementation to signal that there are no more songs?
    fun getNextBatch(database: Database): List<Song>
}

const val PREFERRED_BATCH_SIZE = 10

class ShuffleProvider: SongProvider {
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
}

class BandShuffleProvider(private val bandId: Int): SongProvider {
    override fun getNextBatch(database: Database): List<Song> {
        return database.songDao().getRandomSongsForBand(bandId, PREFERRED_BATCH_SIZE)
    }
}

class AlbumSequentialProvider(private val albumId: Int, private var currentSongId: Int? = null): SongProvider {
    override fun getNextBatch(database: Database): List<Song> {
        val unfilteredSongs = database.songDao().getSongsForAlbum(albumId)
        // TODO: pick up playing album after current song, if it was given
        if (currentSongId != null) {
            val filteredSongs = ArrayList<Song>()
            var foundCurrent = false
            unfilteredSongs.forEach {
                if (foundCurrent) {
                    filteredSongs.add(it)
                } else {
                    if (it.id == currentSongId) {
                        foundCurrent = true
                    }
                }
            }
            // Only filter the first time through!
            currentSongId = null
            return if (filteredSongs.isEmpty()) unfilteredSongs else filteredSongs
        } else {
            return unfilteredSongs
        }
    }
}

class DoubleShotProvider: SongProvider {
    override fun getNextBatch(database: Database): List<Song> {
        val band = database.bandDao().getRandomBand()
        Log.d("SongProvider", "Requesting 2 songs for band ${band.id} ${band.name}")
        return database.songDao().getRandomSongsForBand(band.id, 2)
    }
}
