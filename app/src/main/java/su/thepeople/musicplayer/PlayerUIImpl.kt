package su.thepeople.musicplayer

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.google.common.collect.Lists
import su.thepeople.musicplayer.databinding.FragmentCustomPlayerBinding

typealias OnClick = (View)->Unit
typealias OnLongPress = (View)->Boolean

/**
 * This class handles the actual logic of our custom player UI.  Initial setup bootstrapping is performed in the wrapper PlayerUI
 *
 *  Future directions:
 *   - This is still incomplete. Not all buttons work, not all fields update on new songs.
 *   - Still looks terrible. Polish the UI, and use a "dark mode" styling to save battery.
 */
class PlayerUIImpl(binding: FragmentCustomPlayerBinding, private val player: MediaController, private val mainUI: MainActivity) {

    private val bandButton = binding.bandButton
    private val songLabel = binding.songLabel
    private val yearButton = binding.yearLabel
    private val albumButton = binding.albumLabel
    private val modeLabel = binding.modeLabel
    private val playPauseButton = binding.playPauseButton
    private val nextButton = binding.nextButton

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
    private var advanceSubMode: OnLongPress = {sendCommand("submode"); true}
    private var showBand: OnLongPress = {navigateToCurrentBand(); true}
    private var showAlbum: OnLongPress = {navigateToCurrentAlbum(); true}
    private var showSong: OnLongPress = {navigateToCurrentSong(); true}

    private val playbackStateListener = object: Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playPauseButton.text = if (isPlaying) {"Pause"} else {"Play"}
        }

        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
            Log.d("PlayerUI", "Playlist metadata changed, with media type ${mediaMetadata.mediaType}")
            when(mediaMetadata.mediaType) {
                MediaMetadata.MEDIA_TYPE_ARTIST -> {bandButton.isChecked = true}
                else -> {bandButton.isChecked = false}
            }
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
        bandButton.textOn = songInfo.mediaMetadata.artist ?: "<unknown>"
        bandButton.textOff = songInfo.mediaMetadata.artist ?: "<unknown>"

        albumButton.text = songInfo.mediaMetadata.albumTitle ?: "<unknown>"
        albumButton.textOn = songInfo.mediaMetadata.albumTitle ?: "<unknown>"
        albumButton.textOff = songInfo.mediaMetadata.albumTitle ?: "<unknown>"

        yearButton.text = (songInfo.mediaMetadata.releaseYear?.toString()) ?: "<unknown>"
        songLabel.text = songInfo.mediaMetadata.displayTitle ?: "<unknown>"
    }
}