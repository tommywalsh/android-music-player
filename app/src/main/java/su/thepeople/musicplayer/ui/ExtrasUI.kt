package su.thepeople.musicplayer.ui

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import su.thepeople.musicplayer.databinding.ExtrasUiBinding

/**
 * This class is the UI fragment for the settings/extras UI
 */
class ExtrasFragment (private val mainUI: MainUI): Fragment() {

    // UI-related objects are set up when view is created.
    private lateinit var binding: ExtrasUiBinding
    private lateinit var lockButton: TextView
    private lateinit var buttonStyler: ButtonStyler

    /**
     * This method's job is simply to "inflate" our UI widgets and grab handles to them. The entire
     * view creation process might not be completed yet when this is called.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("ExtrasUI", "View is being created")
        binding = ExtrasUiBinding.inflate(inflater, container, false)
        lockButton = binding.tempLockButton
        lockButton.setOnClickListener(toggleLock)
        buttonStyler = ButtonStyler(mainUI)
        return binding.root
    }

    private fun updateLockButton() {
        if(mainUI.isModal) {
            buttonStyler.styleLocked(lockButton)
        } else {
            buttonStyler.styleUnlocked(lockButton)
        }
    }

    private val toggleLock: OnClick = {
        mainUI.setModalFocusMode(!mainUI.isModal)
        updateLockButton()
    }

    override fun onResume() {
        super.onResume()
        updateLockButton()
    }
}
