package su.thepeople.musicplayer.backend

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.LibraryResult.RESULT_ERROR_BAD_VALUE
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.room.Room
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import su.thepeople.musicplayer.data.ALBUM_PREFIX
import su.thepeople.musicplayer.data.BAND_PREFIX
import su.thepeople.musicplayer.data.DECADE_PREFIX
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.data.GROUP_PREFIX
import su.thepeople.musicplayer.data.LOCATION_PREFIX
import su.thepeople.musicplayer.data.Location
import su.thepeople.musicplayer.data.SONG_PREFIX
import su.thepeople.musicplayer.data.internalIntId
import su.thepeople.musicplayer.data.internalLongId
import su.thepeople.musicplayer.data.internalStringId

const val ROOT_ID = "root"
const val BANDS_ID = "root:bands"
const val GROUPED_BANDS_ID = "root:grouped_bands"
const val YEARS_ID = "root:years"
const val LOCATIONS_ID = "root:locations"

/**
 * This class represents a "MediaLibrarySession.Callback". Despite that name, this class serves as a navigation API.  All bands/albums/songs are
 * represented as nodes in a tree, through which the user can navigate.
 *
 * This app's code is split between the "activity" (frontend), and "service" (backend).  This class is on the service side, and it handles
 * navigation requests from the activity side. This class's job includes translates from the generic language of Android's "media" API to the
 * specific language of MCotP:
 *
 * onGetItem ->  Get information about a particular band, album, or song.
 * onGetChildren -> "Get all of this band's albums", "Get all of this album's songs", etc.
 *
 * Future directions:
 *   - Allow for navigation via date, genre, geography, style, etc.
 *   - Allow for searching?
 */
class McotpLibrarySession(val context: Context, private val player: CustomPlayer) : MediaLibraryService.MediaLibrarySession.Callback {

    private val database = Room.databaseBuilder(context, Database::class.java, "mcotp-database").build()

    /**
     * Our top-level navigation object contains an entry for each way to navigate through the collection. For now, there's only 1 option, and that
     * is via a list of Bands.  In future, we may add entries for navigating by year/decade, location, genre, style, etc.
     */
    private val rootItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(MediaMetadata.Builder()
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setDisplayTitle("Library")
            .setTitle("Library")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build())
        .build()

    init {
        Log.d("Library", "Initializing library")
        Log.d("Library", "Root item is $rootItem")
    }

    /**
     * This object represents each of the root library items. Each shows a different view into the library.
     */
    private val bandsItem = MediaItem.Builder()
        .setMediaId(BANDS_ID)
        .setMediaMetadata(MediaMetadata.Builder()
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setDisplayTitle("All Bands")
            .setTitle("Bands")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build())
        .build()


    private val groupedBandsItem = MediaItem.Builder()
        .setMediaId(GROUPED_BANDS_ID)
        .setMediaMetadata(MediaMetadata.Builder()
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setDisplayTitle("Grouped Bands")
            .setTitle("Grouped Bands")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build())
        .build()

    private val yearsItem = MediaItem.Builder()
        .setMediaId(YEARS_ID)
        .setMediaMetadata(MediaMetadata.Builder()
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setDisplayTitle("Years")
            .setTitle("Years")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build())
        .build()

    private val locationsItem = MediaItem.Builder()
        .setMediaId(LOCATIONS_ID)
        .setMediaMetadata(MediaMetadata.Builder()
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setDisplayTitle("Locations")
            .setTitle("Locations")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build())
        .build()

    @OptIn(UnstableApi::class)
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): ConnectionResult {
        // TODO: Audit these default commands and see if they really all apply
        val sessionCommands = ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(SessionCommand("band", Bundle.EMPTY))
            .add(SessionCommand("album", Bundle.EMPTY))
            .add(SessionCommand("year", Bundle.EMPTY))
            .add(SessionCommand("location", Bundle.EMPTY))
            .add(SessionCommand("submode", Bundle.EMPTY))
            .add(SessionCommand("play-item", Bundle.EMPTY))
            .build()
        val playerCommands = ConnectionResult.DEFAULT_PLAYER_COMMANDS
        // TODO: Can we use the "customlayout" here to notify about locking?
        return AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(playerCommands)
            .setAvailableSessionCommands(sessionCommands)
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<MediaItem>>
    {
        Log.d("Library", "Returning root object $rootItem")
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        Log.d("Library", "Servicing request to get item")
        return when {
            mediaId == ROOT_ID -> {
                Futures.immediateFuture(LibraryResult.ofItem(rootItem, null))
            }
            mediaId == BANDS_ID -> {
                Futures.immediateFuture(LibraryResult.ofItem(bandsItem, null))
            }
            mediaId == GROUPED_BANDS_ID -> {
                Futures.immediateFuture(LibraryResult.ofItem(groupedBandsItem, null))
            }
            mediaId == YEARS_ID -> {
                Futures.immediateFuture(LibraryResult.ofItem(yearsItem, null))
            }
            mediaId == LOCATIONS_ID -> {
                Futures.immediateFuture(LibraryResult.ofItem(locationsItem, null))
            }
            mediaId.startsWith(DECADE_PREFIX) -> {
                Futures.immediateFuture(LibraryResult.ofItem(decadeItem(internalIntId(mediaId)), null))
            }
            mediaId.startsWith(GROUP_PREFIX) -> {
                Futures.immediateFuture(LibraryResult.ofItem(bandGroupItem(internalStringId(mediaId)), null))
            }
            mediaId.startsWith(BAND_PREFIX) -> {
                val bandId = internalIntId(mediaId).toLong()
                return database.async {
                    val band = database.bandDao().get(bandId)
                    val item = database.mediaItem(band)
                    LibraryResult.ofItem(item, null)
                }
            }
            mediaId.startsWith(ALBUM_PREFIX) -> {
                val albumId = internalIntId(mediaId).toLong()
                return database.async {
                    val album = database.albumDao().get(albumId)
                    val item = database.mediaItem(album)
                    LibraryResult.ofItem(item, null)
                }
            }
            mediaId.startsWith(SONG_PREFIX) -> {
                val songId = internalIntId(mediaId).toLong()
                return database.async {
                    val item = database.songDao().get(songId)?.let {song->
                        LibraryResult.ofItem(database.mediaItem(song), null)
                    }
                    item ?: LibraryResult.ofError(RESULT_ERROR_BAD_VALUE)
                }
            }
            else -> {
                Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE))
            }
        }
    }

    private fun decadeItem(decade: Int): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setIsBrowsable(true)
            .setIsPlayable(true)
            .setTitle("${decade}s")
            .setDisplayTitle("${decade}s")
            .build()
        return MediaItem.Builder()
            .setMediaId("$DECADE_PREFIX$decade")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun bandGroupItem(letter: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setTitle(letter)
            .setDisplayTitle(letter)
            .build()
        return MediaItem.Builder()
            .setMediaId("$GROUP_PREFIX$letter")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun yearItem(year: Int): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setTitle("$year")
            .setDisplayTitle("$year")
            .build()
        return MediaItem.Builder()
            .setMediaId("year:$year")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun getLocationChildren(locationId: Long): List<MediaItem> {
        val (l, b) = getDisplaySubLocationsAndBands(locationId)
        return l + b
    }

    private fun getTopLevelLocations(): List<MediaItem> {
        return getDisplaySubLocationsAndBands(null).first
    }

    private fun getDisplaySubLocationsAndBands(locationId: Long?): Pair<List<MediaItem>, List<MediaItem>> {
        /*
         * There are two types of children for each location
         *   - Other locations ("sublocations")
         *   - Bands
         *
         * A sublocation is only shown under two conditions:
         *   1) There are at least two bands from that location
         *   2) There is at least one other sublocation with at least two bands
         * If a sublocation does not meet both of these criteria, then bands from that sublocation
         * (or any sub-sublocation) are "hoisted" up and shows directly as children of the parent.
         */

        // We'll collect band and location IDs as we go
        data class LocationWithBands(val location: Location, val bandIds: List<Long>)
        val bandIds = ArrayList<Long>()
        val displayLocationsWithBands = ArrayList<LocationWithBands>()

        // Generate two sublocation lists: those with only 1 band, and those with more than 1 band
        val allSublocations = if (locationId == null) {
            database.locationDao().getTopLevelLocations()
        } else {
            database.locationDao().getImmediateChildren(locationId)
        }

        val sublocBandList = allSublocations.map { thisSublocation ->
            val descendantLocationIds = database.locationDao().getAllDescendentIds(thisSublocation.id)
            LocationWithBands(thisSublocation, database.bandDao().getBandIdsFromLocations(descendantLocationIds))
        }
        val (full, empty) = sublocBandList.partition { it.bandIds.size > 1 }

        // Decide on bands and sublocations to display
        if (full.size < 2) {
            // Don't display any sublocations!
            bandIds.addAll(full.flatMap{it.bandIds})
            bandIds.addAll(empty.flatMap{it.bandIds})
        } else {
            displayLocationsWithBands.addAll(full)
            bandIds.addAll(empty.flatMap{it.bandIds})
        }

        // Don't forget about any bands from this particular location (not in any sublocation)
        locationId?.let{bandIds.addAll(database.bandDao().getBandIdsFromSpecificLocation(it))}

        // Finally, create media items for everything
        val locationItems = displayLocationsWithBands.sortedBy{it.location.name}.map{database.mediaItem(it.location, "${it.location.name} (${it.bandIds.size})")}
        val bandItems = database.bandDao().get(bandIds).sortedBy{it.name}.map{database.mediaItem(it)}
        return Pair(locationItems, bandItems)
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Log.d("Library", "Servicing request to get children")
        val result = when {
            parentId == ROOT_ID -> {
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(bandsItem, groupedBandsItem, yearsItem, locationsItem), null))
            }
            parentId == BANDS_ID -> {
                return database.async {
                    val bands = database.bandDao().getAll()
                    val items = bands.map {database.mediaItem(it)}
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
                }
            }
            parentId == GROUPED_BANDS_ID -> {
                return database.async {
                    val bands = database.bandDao().getAllInitialCharactersFromNames()
                    val items = bands.map(::bandGroupItem)
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
                }
            }
            parentId == YEARS_ID -> {
                return database.async {
                    val decades = database.songDao().getDecades()
                    val items = decades.map(::decadeItem)
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
                }
            }
            parentId == LOCATIONS_ID -> {
                return database.async {
                    LibraryResult.ofItemList(getTopLevelLocations(), null)
                }
            }
            parentId.startsWith(DECADE_PREFIX) -> {
                return database.async {
                    val years = database.songDao().getYearsForDecade(internalIntId(parentId))
                    val items = years.map(::yearItem)
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
                }
            }
            parentId.startsWith(LOCATION_PREFIX) -> {
                return database.async {
                    val locationId = internalLongId(parentId)
                    val children = getLocationChildren(locationId)
                    LibraryResult.ofItemList(ImmutableList.copyOf(children), null)
                }
            }
            parentId.startsWith(GROUP_PREFIX) -> {
                return database.async {
                    val bands = database.bandDao().getBandsBeginningWithLetter(internalStringId(parentId))
                    val items = bands.map {database.mediaItem(it)}
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
                }
            }
            parentId.startsWith(BAND_PREFIX) -> {
                val bandId = internalIntId(parentId).toLong()
                return database.async {
                    val albums = database.albumDao().getAllForBand(bandId)
                    val albumItems = albums.map { database.mediaItem(it) }
                    val looseSongs = database.songDao().getLooseSongsForBand(bandId)
                    val songItems = looseSongs.map {database.mediaItem(it)}
                    LibraryResult.ofItemList(ImmutableList.copyOf(albumItems + songItems), null)
                }
            }
            parentId.startsWith(ALBUM_PREFIX) -> {
                val albumId = internalIntId(parentId).toLong()
                return database.async {
                    val songs = database.songDao().getSongsForAlbum(albumId)
                    val items = songs.map {database.mediaItem(it)}
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
                }
            }
            else -> {
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), null))
            }
        }
        return result
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            "band" -> {
                player.changeBandLock()
            }
            "album" -> {
                player.changeAlbumLock()
            }
            "year" -> {
                player.changeYearLock()
            }
            "submode" -> {
                player.changeSubMode()
            }
            "play-item" -> {
                args.getString("id")?.let {
                    player.forcePlayItem(it)
                }
            }
        }
        Log.d("Session", "Got command ${customCommand.customAction}")
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    @UnstableApi
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(ArrayList(), 0, 0))
    }
}