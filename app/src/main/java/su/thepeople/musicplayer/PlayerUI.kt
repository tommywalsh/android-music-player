package su.thepeople.musicplayer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.session.MediaController
import su.thepeople.musicplayer.databinding.FragmentCustomPlayerBinding

/**
 * This class wraps the UI for our music player.
 *
 * The actual logic is contained in PlayerUIImpl.  This class merely handles coordinating intial bootstrapping.
 *
 */
class PlayerUI(private val mainUI: MainActivity) : Fragment() {

    /**
     * The binding and player objects are initialized by async processes that may finish in either order. We may set up and activate the actual
     * implementation only after BOTH binding and player objects are initialized.
     */
    private lateinit var binding: FragmentCustomPlayerBinding
    private lateinit var player: MediaController
    private lateinit var impl: PlayerUIImpl

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCustomPlayerBinding.inflate(inflater, container, false)
        if (this::player.isInitialized) {
            activate()
        }
        return binding.root
    }

    fun setPlayer(newPlayer: MediaController) {
        player = newPlayer
        if (this::binding.isInitialized) {
            activate()
        }
    }

    private fun activate() {
        impl = PlayerUIImpl(binding, player, mainUI)
    }
}