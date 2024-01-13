package su.thepeople.musicplayer

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import java.io.File

/**
 * Super-inefficient in-memory "database".
 * This serves as a hacky-but-working placeholder for a real database, which will come later
 *
 */
class MusicDatabase {

    class Band(val name: String, val path: File, var id: String = "") {
        fun toMediaItem(): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setArtist(name)
                .setTitle(name)
                .setDisplayTitle(name)
                .build()
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(metadata)
                .setUri(path.absolutePath)
                .build()
        }
    }

    class Album(val name: String, val path: File, val bandId: String, var id: String = "") {
        fun toMediaItem(): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setTitle(name)
                .setDisplayTitle(name)
                .build()
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(metadata)
                .setUri(path.absolutePath)
                .build()
        }
    }

    class Song(val name: String, val path: File, val bandId: String, val albumId: String?, var id: String = "") {
        fun toMediaItem(): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setTitle(name)
                .setDisplayTitle(name)
                .build()
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(metadata)
                .setUri(path.absolutePath)
                .build()
        }
    }

    private val allBands = HashMap<String, MediaItem>()
    private var albumsForBand = HashMap<String, ArrayList<MediaItem>>()
    private var looseSongsForBand = HashMap<String, ArrayList<MediaItem>>()
    private var songsForAlbum = HashMap<String, ArrayList<MediaItem>>()

    fun addBand(name: String, path: File): MediaItem {
        val newId = "band:" + (allBands.size + 1).toString()
        val band = Band(name, path, newId)
        val bandItem = band.toMediaItem()
        allBands[newId] = bandItem
        return bandItem
    }

    fun addAlbum(name: String, path: File, bandId: String): MediaItem {
        if (!albumsForBand.contains(bandId)) {
            albumsForBand[bandId] = ArrayList()
        }
        val newId = "album:" + bandId + "/" + (albumsForBand[bandId]!!.size + 1).toString()
        val album = Album(name, path, bandId, newId)
        val albumItem = album.toMediaItem()
        albumsForBand[bandId]!!.add(albumItem)
        return albumItem
    }

    fun addLooseSong(name: String, path: File, bandId: String): MediaItem {
        if (!looseSongsForBand.contains(bandId)) {
            looseSongsForBand[bandId] = ArrayList()
        }
        val newId = "song:" + bandId + "/" + (looseSongsForBand[bandId]!!.size + 1).toString()
        val song = Song(name, path, bandId, null, newId)
        val songItem = song.toMediaItem()
        looseSongsForBand[bandId]!!.add(songItem)
        return songItem
    }

    fun addAlbumSong(name: String, path: File, bandId: String, albumId: String): MediaItem {
        if (!songsForAlbum.contains(albumId)) {
            songsForAlbum[albumId] = ArrayList()
        }
        val newId = "song:" + albumId + "/" + (songsForAlbum[albumId]!!.size + 1).toString()
        val song = Song(name, path, bandId, albumId, newId)
        val songItem = song.toMediaItem()
        songsForAlbum[albumId]!!.add(songItem)
        return songItem
    }

    fun getBands(): List<MediaItem> {
        return Lists.newArrayList(allBands.values).sortedBy { it.mediaMetadata.displayTitle.toString() }
    }

    fun getBand(id: String): MediaItem? {
        return allBands[id]
    }

    fun getAlbumsForBand(bandId: String): List<MediaItem> {
        val albumList = albumsForBand[bandId] ?: ArrayList()
        return albumList.sortedBy {it.mediaMetadata.displayTitle.toString()}
    }

    private fun findIdInList(list: List<MediaItem>, id: String): MediaItem? {
        list.forEach {
            if (it.mediaId == id) return it
        }
        return null
    }

    fun getAlbum(albumId: String): MediaItem? {
        val bandId = albumId.substringAfter(":").substringBefore("/")
        val list = albumsForBand[bandId] ?: ImmutableList.of()
        return findIdInList(list, albumId)
    }

    private fun getLooseSong(songId: String): MediaItem? {
        val bandId = songId.substringAfter(":").substringBefore("/")
        val list = looseSongsForBand[bandId] ?: ImmutableList.of()
        return findIdInList(list, songId)
    }

    private fun getAlbumSong(songId: String): MediaItem? {
        val albumId = songId.substringAfter(":").substringBefore("/")
        val list = songsForAlbum[albumId] ?: ImmutableList.of()
        return findIdInList(list, songId)
    }

    fun getSong(songId: String): MediaItem? {
        return if (songId.startsWith("song:band")) {
            getLooseSong(songId)
        } else {
            getAlbumSong(songId)
        }
    }

    fun getLooseSongsForBand(bandId: String): List<MediaItem> {
        val songList = looseSongsForBand[bandId] ?: ArrayList()
        return songList.sortedBy {it.mediaMetadata.displayTitle.toString()}
    }

    fun getAlbumSongs(albumId: String): List<MediaItem> {
        val songList = songsForAlbum[albumId] ?: ArrayList()
        return songList.sortedBy {it.mediaMetadata.displayTitle.toString()}
    }
}