package su.thepeople.musicplayer.ui

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import su.thepeople.musicplayer.backend.BANDS_ID
import su.thepeople.musicplayer.R
import su.thepeople.musicplayer.backend.ROOT_ID
import su.thepeople.musicplayer.databinding.PlayerUiBinding
import java.io.Closeable

/**
 * This class is the UI fragment for our music player.  It contains just enough logic to get itself hooked up to the rest of the system.
 */
class PlayerFragment : Fragment(), ConnectableUI {

    private lateinit var binding: PlayerUiBinding
    private var logic: PlayerUILogic? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("PlayerUI", "Inflating binding as view is being created")
        binding = PlayerUiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        Log.d("PlayerUI", "Starting")
        super.onStart()
        // notify the connector that this UI is ready to communicate
        UIConnector.get().registerUI(this)
    }

    override fun onStop() {
        Log.d("PlayerUI", "Stopping")
        super.onStop()
        logic?.close()
        logic = null
        UIConnector.get().unregisterUI(this)
    }

    // The centralized connector may call this method immediately when this object is registered, or some time later, or never.
    override fun connect(connectionData: UIConnectionData) {
        Log.d("PlayerUI", "Accepting connection")
        logic = PlayerUILogic(binding, connectionData.connection, connectionData.mainUI)
    }
}

typealias OnClick = (View)->Unit
typealias OnLongPress = (View)->Boolean

/**
 * This class handles all the logic of communicating with the backend.
 */
class PlayerUILogic(binding: PlayerUiBinding, private val player: MediaController, private val mainUI: MainUI) : Closeable {

    private val bandButton = binding.bandButton
    private val songLabel = binding.songLabel
    private val yearButton = binding.yearLabel
    private val albumButton = binding.albumLabel
    private val modeLabel = binding.modeLabel
    private val playPauseButton = binding.playPauseButton
    private val nextButton = binding.nextButton
    private val buttonStyler = ButtonStyler(mainUI)

    /**
     * Convenience function to allow easy inline definition of callbacks that interact with the player
     * Note that this protects against the case where the player is not yet available, so this function may always be called safely.
     */
    private fun withPlayer(task: MediaController.()->Unit): (View)->Unit {
        return {player.run(task)}
    }

    private fun sendSimpleCommand(command: String) {
        player.sendCustomCommand(SessionCommand(command, Bundle.EMPTY), Bundle.EMPTY)
    }

    private fun navigateToCurrentBand() {
        player.currentMediaItem?.mediaMetadata?.extras?.getInt("band")?.let {
            mainUI.navigateTo(Lists.newArrayList(ROOT_ID, BANDS_ID), "band:$it")
        }
    }

    private fun navigateToCurrentAlbum() {
        player.currentMediaItem?.mediaMetadata?.extras?.getInt("album")?.let {albumId ->
            player.currentMediaItem?.mediaMetadata?.extras?.getInt("band")?.let { bandId ->
                mainUI.navigateTo(Lists.newArrayList(ROOT_ID, BANDS_ID, "band:$bandId"), "album:$albumId")
            }
        }
    }

    private fun navigateToCurrentSong() {
        player.currentMediaItem?.mediaMetadata?.extras?.getInt("song")?.let {songId ->
            player.currentMediaItem?.mediaMetadata?.extras?.getInt("band")?.let { bandId ->
                val parentList = Lists.newArrayList(ROOT_ID, BANDS_ID, "band:$bandId")
                val maybeAlbumId = player.currentMediaItem?.mediaMetadata?.extras?.getInt("album")
                if (maybeAlbumId != null && maybeAlbumId > 0) {
                    parentList.add("album:$maybeAlbumId")
                }
                mainUI.navigateTo(parentList, "song:$songId")
            }
        }
    }

    private var togglePlayPause = withPlayer {if (isPlaying) {pause()} else {play()}}
    private var advanceSong: OnClick = {player.seekToNextMediaItem()}
    private var toggleBandLock: OnClick = {sendSimpleCommand("band")}
    private var toggleAlbumLock: OnClick = {sendSimpleCommand("album")}
    private var toggleYearLock: OnClick = {sendSimpleCommand("year")}
    private var advanceSubMode: OnLongPress = {sendSimpleCommand("submode"); true}
    private var showBand: OnLongPress = {navigateToCurrentBand(); true}
    private var showAlbum: OnLongPress = {navigateToCurrentAlbum(); true}
    private var showSong: OnLongPress = {navigateToCurrentSong(); true}

    private fun setButtonStylesForMediaType(mediaType: Int) {
        // Assume all buttons are unchecked until proven otherwise
        val uncheckedButtons = Sets.newHashSet<TextView>(bandButton, yearButton, albumButton)

        val buttonToCheck: TextView? = when (mediaType) {
            MediaMetadata.MEDIA_TYPE_ARTIST -> bandButton
            MediaMetadata.MEDIA_TYPE_ALBUM -> albumButton
            MediaMetadata.MEDIA_TYPE_YEAR -> yearButton
            else -> null
        }

        buttonToCheck?.let{
            uncheckedButtons.remove(it)
            buttonStyler.styleLocked(it)
        }

        uncheckedButtons.forEach {
            buttonStyler.styleUnlocked(it)
        }
    }

    // The backend player uses this interface to announce changes in state
    private val playbackStateListener = object: Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d("PlayerUI", "isPlaying changed to  $isPlaying")

            val imageId = if (isPlaying) {
                R.drawable.ic_pause_button
            } else {
                R.drawable.ic_play_button
            }
            playPauseButton.setImageResource(imageId)
        }

        // "playlist metadata" in our case means what sort of locking/filtering is in place: band shuffle? year lock? double-shot weekend?
        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
            Log.d("PlayerUI", "Playlist metadata changed, with media type ${mediaMetadata.mediaType}")
            setButtonStylesForMediaType(mediaMetadata.mediaType?:0)
            mediaMetadata.title?.let{modeLabel.text = it}
        }

        // Called when a new song is loaded into the player
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d("PlayerUI", "Playlist metadata tranitioning, with media type ${mediaItem}?.mediaType}")
            mediaItem?.let {updateSongInfo(it)}
        }
    }

    init {
        bandButton.setOnClickListener(toggleBandLock)
        bandButton.setOnLongClickListener(showBand)
        albumButton.setOnClickListener(toggleAlbumLock)
        albumButton.setOnLongClickListener(showAlbum)
        yearButton.setOnClickListener(toggleYearLock)
        playPauseButton.setOnClickListener(togglePlayPause)
        playPauseButton.isLongClickable = true
        playPauseButton.setOnLongClickListener(advanceSubMode)
        nextButton.setOnClickListener(advanceSong)

        songLabel.setOnLongClickListener(showSong)

        player.currentMediaItem?.let {updateSongInfo(it)}
        player.addListener(playbackStateListener)

        setButtonStylesForMediaType(player.playlistMetadata.mediaType?:0)
        player.playlistMetadata.title?.let{modeLabel.text = it}
    }

    override fun close() {
        /*
         * We know that this app will immediately dispose/disable the UI widgets after close() is called, and so we can
         * be a little more efficient by skipping the step of disconnecting all of the widgets' listeners.  We do, however,
         * need to remove ourselves from the player's listener list.
         */
        player.removeListener(playbackStateListener)
    }

    private fun updateSongInfo(songInfo: MediaItem) {
        Log.d("PlayerUI", "song info being updated")

        bandButton.text = songInfo.mediaMetadata.artist ?: "<unknown>"
        albumButton.text = songInfo.mediaMetadata.albumTitle ?: "<unknown>"
        yearButton.text = (songInfo.mediaMetadata.releaseYear?.toString()) ?: "<unknown>"
        songLabel.text = songInfo.mediaMetadata.displayTitle ?: "<unknown>"
    }
}