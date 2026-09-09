package su.thepeople.musicplayer.tools

import android.content.Context
import android.util.Log
import org.json.JSONObject
import su.thepeople.musicplayer.data.Album
import su.thepeople.musicplayer.data.Band
import su.thepeople.musicplayer.data.BandLocationCrossRef
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.Location
import su.thepeople.musicplayer.data.NEW_OBJ_ID
import su.thepeople.musicplayer.data.Song
import su.thepeople.musicplayer.getStringArrayProp
import java.io.File
import java.nio.charset.StandardCharsets

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
                candidate.extension != "json" &&
                candidate.extension != "link"
    }

    private fun isMetadataFile(candidate: File): Boolean {
        return candidate.isFile && candidate.name == "metadata.json"
    }

    private fun isLinkFile(candidate: File): Boolean {
        return candidate.isFile && candidate.name.endsWith("forward.link")
    }

    private fun readMetadataFromFile(file: File): JSONObject? {
        return if (file.isFile) {
            JSONObject(file.readText(StandardCharsets.UTF_8))
        } else {
            null
        }
    }

    private fun readLinkFromFile(file: File): String? {
        return if (file.isFile) {
            file.readText(StandardCharsets.UTF_8)
        } else {
            null
        }
    }

    private fun processMetadataForBand(bandId: Long, metadata: JSONObject) {
        val locations = getStringArrayProp(metadata, "location")
        locations.forEach {
            val locationTrail = it.split("/")
            var priorLocationId: Long? = null
            locationTrail.forEach{ thisLocation ->
                val existingId = database.locationDao().getId(thisLocation, priorLocationId)
                if (existingId == null) {
                    val loc = Location(NEW_OBJ_ID, thisLocation, priorLocationId)
                    val newLocationId = database.locationDao().insert(loc)
                    priorLocationId = newLocationId
                } else {
                    priorLocationId = existingId
                }
            }
            priorLocationId?.let { lid ->
                val xref = BandLocationCrossRef(bandId=bandId, locationId = lid)
                database.locationDao().addBandLocation(xref)
            }
        }
    }

    private fun scanAlbumSongs(albumDir: File, bandId: Long, album: Album, albumId: Long): Boolean {
        var foundSong = false
        Log.d("Scanner", "Scanning album ${album.name}")

        // Song filename (sans extension) to database ID
        val fileToDB = HashMap<String, Long>()

        // Store map of file-linked-from -> file-linked-to
        val linksFound = HashMap<String, String>()

        albumDir.listFiles()?.forEach { childObj ->
            if (isSongFile(childObj)) {
                val thisID = scanAlbumSong(childObj, bandId, album, albumId)
                Log.d("Scanner", "Found album song ${childObj.name}")
                foundSong = true
                fileToDB[childObj.nameWithoutExtension] = thisID
            } else if (isLinkFile(childObj)) {
                readLinkFromFile(childObj)?.let {
                    val linkFromKey = linknameBase(childObj.name)
                    val linkToKey = it.substring(0, it.lastIndexOf("."))
                    linksFound[linkFromKey] = linkToKey
                    Log.d("Scanner", "Remembering link from $linkFromKey to $linkToKey, will process later")
                }

            }
        }

        /*
            We could likely be slightly more "efficient" by doing this linking while the
            objects are created. However, that would make the code more complex, and we will only
            ever have a handful of links, so this technique is fine.
         */
        for (entry in linksFound) {
            Log.d("Scanner", "Trying to process album song link ${entry.key} -> ${entry.value}...")
            fileToDB[entry.key]?.let { fromId ->
                fileToDB[entry.value]?.let { toId ->
                    Log.d("Scanner", "...Adding album song link ${entry.key} -> ${entry.value}")
                    addLinkToSong(fromId, toId)
                }
            }
        }

        return foundSong
    }

    private fun scanAlbumAndContents(albumDir: File, bandId: Long): Boolean {
        val matchResult = ALBUM_REGEX.matchEntire(albumDir.name)
        val album: Album = if (matchResult != null) {
            val year = matchResult.groups[2]!!.value
            val bandName = matchResult.groups[3]!!.value
            Album(NEW_OBJ_ID, bandName, albumDir.absolutePath, bandId, year)
        } else {
            Album(NEW_OBJ_ID, albumDir.name.substringBeforeLast("."), albumDir.absolutePath, bandId)
        }

        val albumId = database.albumDao().insert(album)
        val foundSong = scanAlbumSongs(albumDir, bandId, album, albumId)
        if (!foundSong) {
            Log.d("Scanner", "No songs found for ${album.name}, deleting")
            database.albumDao().delete(albumId)
        }
        return foundSong
    }

    private fun scanLooseSong(songFile: File, bandId: Long): Long {
        val matchResult = LOOSE_SONG_REGEX.matchEntire(songFile.name)
        val song: Song = if (matchResult != null) {
            val year = matchResult.groups[2]!!.value
            val songName = matchResult.groups[3]!!.value
            Song(NEW_OBJ_ID, songName, songFile.absolutePath, bandId, year=year)
        } else {
            Song(NEW_OBJ_ID, songFile.name.substringBeforeLast("."), songFile.absolutePath, bandId)
        }
        return database.songDao().insert(song)
    }

    private fun scanAlbumSong(songFile: File, bandId: Long, album: Album, albumId: Long): Long {
        val matchResult = ALBUM_SONG_REGEX.matchEntire(songFile.name)
        val song: Song = if (matchResult != null) {
            val trackNum = matchResult.groups[2]!!.value
            val songName = matchResult.groups[3]!!.value
            Song(NEW_OBJ_ID, songName, songFile.absolutePath, bandId, album.year, albumId, trackNum.toInt())
        } else {
            Song(NEW_OBJ_ID, songFile.name.substringBeforeLast("."), songFile.absolutePath, bandId, album.year, albumId)
        }
        return database.songDao().insert(song)
    }

    private fun addLinkToSong(fromId: Long, toId: Long) {
        val song = database.songDao().get(fromId)!!
        song.followingSongId = toId
        database.songDao().update(song)
    }

    private fun linknameBase(fullLinkname: String): String {
        return fullLinkname.removeSuffix(".forward.link")
    }

    private fun scanBandAndContents(bandDir: File) {
        val band = Band(NEW_OBJ_ID, bandDir.name, bandDir.absolutePath)
        val bandId = database.bandDao().insert(band)
        var foundSong = false
        Log.d("Scanner", "Scanning band ${bandDir.name}")

        // Song filename (sans extension) to database ID
        val fileToDB = HashMap<String, Long>()

        // Store map of file-linked-from -> file-linked-to
        val linksFound = HashMap<String, String>()

        bandDir.listFiles()?.forEach { childObj ->
            Log.d("Scanner", "Considering {$childObj.name}")
            if (isBandOrAlbumDir(childObj)) {
                val foundAlbumSong = scanAlbumAndContents(childObj, bandId)
                if (foundAlbumSong) {
                    Log.d("Scanner", "Last album had songs")
                    foundSong = true
                }
            } else if (isSongFile(childObj)) {
                val thisID = scanLooseSong(childObj, bandId)
                Log.d("Scanner", "Found loose song ${childObj.name}")
                foundSong = true
                fileToDB[childObj.nameWithoutExtension] = thisID
            } else if (isMetadataFile(childObj)) {
                readMetadataFromFile(childObj)?.let{ processMetadataForBand(bandId, it) }
            } else if (isLinkFile(childObj)) {
                readLinkFromFile(childObj)?.let {
                    val linkFromKey = linknameBase(childObj.name)
                    val linkToKey = it.replace("\\.[^\\.]+$", "")
                    linksFound[linkFromKey] = linkToKey
                    Log.d("Scanner", "Remembering link from $linkFromKey to $linkToKey")
                }
            }
        }
        if (!foundSong) {
            Log.d("Scanner", "No songs found for $band.name, deleting")
            database.bandDao().delete(bandId)
            return
        }

        /*
          We could likely be slightly more "efficient" by doing this linking while the
          objects are created. However, that would make the code more complex, and we will only
          ever have a handful of links, so this technique is fine.
         */
        for (entry in linksFound) {
            Log.d("Scanner", "Trying to process loose song link ${entry.key} ${entry.value}...")
            fileToDB[entry.key]?.let { fromId ->
                fileToDB[entry.value]?.let { toId ->
                    Log.d("Scanner", "...Adding loose song link ${entry.key}...")
                    addLinkToSong(fromId, toId)
                }
            }
        }
    }

    fun fullScan() {
        // TODO: Use non-deprecated API for accessing MCotP
        val mcotp = findMcotp(context.externalMediaDirs)
        val subfiles = mcotp?.listFiles()
        subfiles?.forEach { childObj ->
            if (isBandOrAlbumDir(childObj)) {
                scanBandAndContents(childObj)
            }
        }
    }
}