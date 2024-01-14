package su.thepeople.musicplayer

import android.content.Context
import su.thepeople.musicplayer.data.Album
import su.thepeople.musicplayer.data.Band
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.NEW_OBJ_ID
import su.thepeople.musicplayer.data.Song
import java.io.File

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

    private fun scanAlbumSongs(albumDir: File, bandId: Int, albumId: Int) {
        albumDir.listFiles()?.forEach { songFile ->
            if (isSongFile(songFile)) {
                val song = Song(NEW_OBJ_ID, songFile.name, songFile.absolutePath, bandId, albumId)
                database.songDao().insert(song)
            }
        }
    }

    private fun scanAlbumAndContents(albumDir: File, bandId: Int) {
        val album = Album(NEW_OBJ_ID, albumDir.name, albumDir.absolutePath, bandId)
        val albumId = database.albumDao().insert(album).toInt()
        scanAlbumSongs(albumDir, bandId, albumId)
    }

    private fun scanLooseSong(songFile: File, bandId: Int) {
        val song = Song(NEW_OBJ_ID, songFile.name, songFile.absolutePath, bandId, null)
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
        if (!database.isScanned()) {
            mcotp?.listFiles()?.forEach { childObj ->
                if (isBandOrAlbumDir(childObj)) {
                    scanBandAndContents(childObj)
                }
            }
        }
    }
}