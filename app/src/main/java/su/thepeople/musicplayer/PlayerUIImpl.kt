package su.thepeople.musicplayer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import su.thepeople.musicplayer.databinding.PlayerUiBinding

typealias OnClick = (View)->Unit
typealias OnLongPress = (View)->Boolean

/**
 * This class handles the actual logic of our custom player UI.  Initial setup bootstrapping is performed in the wrapper PlayerUI
 *
 *  Future directions:
 *   - Polish the UI
 */
class PlayerUIImpl(binding: PlayerUiBinding, private val player: MediaController, private val mainUI: MainActivity) {

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

    private fun sendCommand(command: String) {
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
    private var advanceSong: OnClick  = {player.seekToNextMediaItem()}
    private var toggleBandLock: OnClick = {sendCommand("band")}
    private var toggleAlbumLock: OnClick = {sendCommand("album")}
    private var toggleYearLock: OnClick = {sendCommand("year")}
    private var advanceSubMode: OnLongPress = {sendCommand("submode"); true}
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

    private val playbackStateListener = object: Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val imageId = if (isPlaying) {R.drawable.ic_pause_button} else {R.drawable.ic_play_button}
            playPauseButton.setImageResource(imageId)
        }

        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
            Log.d("PlayerUI", "Playlist metadata changed, with media type ${mediaMetadata.mediaType}")
            setButtonStylesForMediaType(mediaMetadata.mediaType?:0)
            mediaMetadata.title?.let{modeLabel.text = it}
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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

    }

    private fun updateSongInfo(songInfo: MediaItem) {
        bandButton.text = songInfo.mediaMetadata.artist ?: "<unknown>"
        albumButton.text = songInfo.mediaMetadata.albumTitle ?: "<unknown>"
        yearButton.text = (songInfo.mediaMetadata.releaseYear?.toString()) ?: "<unknown>"
        songLabel.text = songInfo.mediaMetadata.displayTitle ?: "<unknown>"
    }
}