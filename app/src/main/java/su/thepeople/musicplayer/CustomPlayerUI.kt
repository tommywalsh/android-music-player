package su.thepeople.musicplayer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.ToggleButton
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import su.thepeople.musicplayer.databinding.FragmentCustomPlayerBinding

/**
 * This class implements the UI for our music player.
 *
 * Future directions:
 *   - This is still incomplete. Not all buttons work, not all fields update on new songs.
 *   - Still looks terrible. Polish the UI, and use a "dark mode" styling to save battery.
 */
class CustomPlayerUI(private val mainActivity: MainActivity) : Fragment() {

    private lateinit var bandButton: ToggleButton
    private lateinit var songLabel: TextView
    private lateinit var yearButton: ToggleButton
    private lateinit var albumButton: ToggleButton
    private lateinit var modeLabel: TextView
    private lateinit var playPauseButton: Button
    private lateinit var nextButton: Button

    var player: Player? = null
        set(newVal) {
            field?.removeListener(playbackStateListener)
            field = newVal
            newVal?.addListener(playbackStateListener)
        }

    private val playbackStateListener = object: Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playPauseButton.text = if (isPlaying) {"Pause"} else {"Play"}
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            bandButton.text = mediaItem?.mediaMetadata?.artist ?: "<unknown>"
            bandButton.textOn = mediaItem?.mediaMetadata?.artist ?: "<unknown>"
            bandButton.textOff = mediaItem?.mediaMetadata?.artist ?: "<unknown>"

            albumButton.text = mediaItem?.mediaMetadata?.albumTitle ?: "<unknown>"
            albumButton.textOn = mediaItem?.mediaMetadata?.albumTitle ?: "<unknown>"
            albumButton.textOff = mediaItem?.mediaMetadata?.albumTitle ?: "<unknown>"

            yearButton.text = (mediaItem?.mediaMetadata?.releaseYear?.toString()) ?: "<unknown>"
            songLabel.text = mediaItem?.mediaMetadata?.displayTitle ?: "<unknown>"
        }
    }


    private fun withPlayer(task: Player.()->Unit): (View)->Unit {
        return {player?.run(task)}
    }

    private var togglePlayPause = withPlayer {if (isPlaying) {pause()} else {play()}}
    private var advanceSong = withPlayer {seekToNextMediaItem()}


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val binding = FragmentCustomPlayerBinding.inflate(inflater, container, false)
        bandButton = binding.bandButton
        songLabel = binding.songLabel
        yearButton = binding.yearLabel
        albumButton = binding.albumLabel
        modeLabel = binding.modeLabel
        playPauseButton = binding.playPauseButton
        nextButton = binding.nextButton

        playPauseButton.setOnClickListener(togglePlayPause)
        nextButton.setOnClickListener(advanceSong)

        return binding.root
    }
}