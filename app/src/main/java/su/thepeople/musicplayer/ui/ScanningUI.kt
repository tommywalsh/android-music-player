package su.thepeople.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import su.thepeople.musicplayer.databinding.ScanningBinding

class ScanningUI: Fragment() {

    /**
     * This method's job is simply to "inflate" our UI widgets and grab handles to them. The entire
     * view creation process might not be completed yet when this is called.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = ScanningBinding.inflate(inflater, container, false)
        return binding.root
    }
}
