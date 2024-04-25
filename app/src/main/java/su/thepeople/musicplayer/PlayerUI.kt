package su.thepeople.musicplayer

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.session.MediaController
import su.thepeople.musicplayer.databinding.PlayerUiBinding

/**
 * This class wraps the UI for our music player.
 *
 * The actual logic is contained in PlayerUIImpl.  This class merely handles coordinating intial bootstrapping.
 *
 */
class PlayerUI() : Fragment() {

    /**
     * The binding and player objects are initialized by async processes that may finish in either order. We may set up and activate the actual
     * implementation only after BOTH binding and player objects are initialized.
     */
    private lateinit var binding: PlayerUiBinding
    private lateinit var player: MediaController
    private lateinit var impl: PlayerUIImpl
    private lateinit var mainUI: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = PlayerUiBinding.inflate(inflater, container, false)
        if (this::player.isInitialized) {
            activate()
        }
        return binding.root
    }

    fun connect(newPlayer: MediaController, ui: MainActivity) {
        if (!this::player.isInitialized || player != newPlayer) {
            Log.d("PlayerUI", "Accepting connection")
            player = newPlayer
            mainUI = ui
            if (this::binding.isInitialized) {
                activate()
            }
        }
    }

    private fun activate() {
        Log.d("PlayerUI", "Activating")
        impl = PlayerUIImpl(binding, player, mainUI)
    }
}