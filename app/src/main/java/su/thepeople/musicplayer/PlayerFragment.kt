package su.thepeople.musicplayer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import su.thepeople.musicplayer.databinding.FragmentPlayerBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class PlayerFragment(private val mainActivity: MainActivity) : Fragment() {

    private var playerView: PlayerView? = null

    var player: Player?
        get() { return playerView?.player }
        set(newPlayer) { playerView?.player = newPlayer }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val binding = FragmentPlayerBinding.inflate(inflater, container, false)
        playerView = binding.playerView
        if (mainActivity.mediaBrowser != null) binding.playerView.player = mainActivity.mediaBrowser

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /*binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }*/
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}