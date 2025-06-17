package su.thepeople.musicplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import su.thepeople.musicplayer.databinding.NoPermissionUiBinding

class NoPermissionUI: Fragment() {

    private var mainUI: MainUI? = null
    // UI-related objects are set up when view is created.
    private lateinit var binding: NoPermissionUiBinding
    private lateinit var grantButton: TextView

    fun connect(ui: MainUI) {
        mainUI = ui
    }

    /**
     * This method's job is simply to "inflate" our UI widgets and grab handles to them. The entire
     * view creation process might not be completed yet when this is called.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = NoPermissionUiBinding.inflate(inflater, container, false)
        grantButton = binding.grantPermissionsButton
        grantButton.setOnClickListener(permissionRequest)
        return binding.root
    }

    private val permissionRequest: OnClick = {
        mainUI?.let {
            // NOTE: There is no callback or return value from this request.
            // Instead, if the user grants permission, the app will restart.
            val uri = Uri.parse("package:${it.packageName}")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    uri
                )
            )
        }
    }
}
