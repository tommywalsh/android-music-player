package su.thepeople.musicplayer

import android.content.Context
import su.thepeople.musicplayer.data.Album
import su.thepeople.musicplayer.data.Band
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.NEW_OBJ_ID
import su.thepeople.musicplayer.data.Song
import java.io.File

val ALBUM_REGEX = Regex("^((\\d\\d\\d\\d).? - )(.+)$")
val LOOSE_SONG_REGEX = Regex("^((\\d\\d\\d\\d).? - )(.+)\\.[^.]+$")
val ALBUM_SONG_REGEX = Regex("^((\\d+).? - )(.+)\\.[^.]+$")
/**
 * This class knows how to find the MCotP on disk, and scan it into the database.
 *
 * Future directions:
 *   - Currently we have a crude "already scanned" check, so we will never scan the disk a second time.  That means any on-disk changes require a
 *     full wipe of this app's storage.  It would be better if we could automatically update the database. This would involve scanning regularly, and
 *     being smart about adding/updating/deleting as necessary (for now, the scanner only knows how to add, not update/delete).
 */
class Scanner(private val context: Context, private val database: Database) {

    private fun isCollectionDirectory(candidate: File): Boolean {
        return candidate.isDirectory && candidate.name == "mcotp"
    }

    private fun findMcotpViaAncestors(startChild: File): File? {
        var current: File? = startChild
        while (current != null) {
            if (isCollectionDirectory(current)) {
                return current
            } else {
                val child = File(current, "mcotp")
                if (isCollectionDirectory(child)) {
                    return child
                }
            }
            current = current.parentFile
        }
        return null
    }


    private fun findMcotp(mediaDirs: Array<File>): File? {
        // TODO: Hack to allow working with emulated device, which is not appearing in externalMediaDirs for some reason
        val dirs = ArrayList<File>()
        dirs.addAll(mediaDirs)
        dirs.add(File("/storage/7F62-69AB"))

        dirs.forEach {
            val maybeMcotp = findMcotpViaAncestors(it)
            if (maybeMcotp != null) return maybeMcotp
        }
        return null
    }

    private fun isBandOrAlbumDir(candidate: File): Boolean {
        return candidate.isDirectory &&
                candidate.name != ".." &&
                candidate.name != "." &&
                !candidate.name.startsWith('[')
    }

    private fun isSongFile(candidate: File): Boolean {
        return candidate.isFile &&
                !candidate.name.startsWith("[") &&
                candidate.extension != "json"
    }

    private fun scanAlbumSongs(albumDir: File, bandId: Int, album: Album, albumId: Int) {
        albumDir.listFiles()?.forEach { songFile ->
            if (isSongFile(songFile)) {
                scanAlbumSong(songFile, bandId, album, albumId)
            }
        }
    }

    private fun scanAlbumAndContents(albumDir: File, bandId: Int) {
        val matchResult = ALBUM_REGEX.matchEntire(albumDir.name)
        val album: Album = if (matchResult != null) {
            val year = matchResult.groups[2]!!.value
            val bandName = matchResult.groups[3]!!.value
            Album(NEW_OBJ_ID, bandName, albumDir.absolutePath, bandId, year)
        } else {
            Album(NEW_OBJ_ID, albumDir.name.substringBeforeLast("."), albumDir.absolutePath, bandId)
        }

        val albumId = database.albumDao().insert(album).toInt()
        scanAlbumSongs(albumDir, bandId, album, albumId)
    }

    private fun scanLooseSong(songFile: File, bandId: Int) {
        val matchResult = LOOSE_SONG_REGEX.matchEntire(songFile.name)
        val song: Song = if (matchResult != null) {
            val year = matchResult.groups[2]!!.value
            val songName = matchResult.groups[3]!!.value
            Song(NEW_OBJ_ID, songName, songFile.absolutePath, bandId, year=year)
        } else {
            Song(NEW_OBJ_ID, songFile.name.substringBeforeLast("."), songFile.absolutePath, bandId)
        }
        database.songDao().insert(song)
    }

    private fun scanAlbumSong(songFile: File, bandId: Int, album: Album, albumId: Int) {
        val matchResult = ALBUM_SONG_REGEX.matchEntire(songFile.name)
        val song: Song = if (matchResult != null) {
            val trackNum = matchResult.groups[2]!!.value  // TODO: implement track numbers, needed for sequential play mode
            val songName = matchResult.groups[3]!!.value
            Song(NEW_OBJ_ID, songName, songFile.absolutePath, bandId, albumId, album.year)
        } else {
            Song(NEW_OBJ_ID, songFile.name.substringBeforeLast("."), songFile.absolutePath, bandId, albumId, album.year)
        }
        database.songDao().insert(song)
    }

    private fun scanBandAndContents(bandDir: File) {
        val band = Band(NEW_OBJ_ID, bandDir.name, bandDir.absolutePath)
        val bandId = database.bandDao().insert(band).toInt()
        bandDir.listFiles()?.forEach { childObj ->
            if (isBandOrAlbumDir(childObj)) {
                scanAlbumAndContents(childObj, bandId)
            } else if (isSongFile(childObj)) {
                scanLooseSong(childObj, bandId)
            }
        }
    }

    fun fullScan() {
        // TODO: Use non-deprecated API for accessing MCotP
        val mcotp = findMcotp(context.externalMediaDirs)
        mcotp?.listFiles()?.forEach { childObj ->
            if (isBandOrAlbumDir(childObj)) {
                scanBandAndContents(childObj)
            }
        }
    }
}