package su.thepeople.musicplayer.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONObject
import su.thepeople.musicplayer.databinding.ActivityMainBinding
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Main application UI.  This class largely handles setup and teardown. Most of the app logic lives in other classes.
 *
 * Full setup of the UI happens like this:
 *   1) in onCreate, UI objects are created, but are not yet functional
 *   2) in onStart, we kick off the background process of creating a connection to the backend
 *   3) All of the remaining UI and communication setup is done elsewhere (see UIConnector, and each of the component UIs for more)
 */
class MainUI : FragmentActivity() {

    private lateinit var playerUI: PlayerFragment
    private lateinit var libraryUI: LibraryFragment
    private lateinit var viewPager: ViewPager2
    private lateinit var contentView: View
    private lateinit var btsl: BluetoothModalController

    private val extrasUI = ExtrasFragment(this)

    var isModal: Boolean = false
        private set

    fun setModalFocusMode(newModal: Boolean = true) {
        isModal = newModal

        if (this::btsl.isInitialized) {
            if (isModal) {
                btsl.activate()
            } else {
                btsl.deactivate()
            }
        }
    }

    fun onSongChange() {
        if (isModal and this::btsl.isInitialized) {
            btsl.renewScreenLock()
        }
    }

    private fun getInternalState(): JSONObject {
        val state = JSONObject()
        state.put("isModal", isModal)
        return state
    }

    private fun loadSavedState() {
        val stateFile = File(applicationContext.filesDir, "ui_state.json")
        if (stateFile.isFile) {
            val savedState = JSONObject(stateFile.readText(StandardCharsets.UTF_8))
            setModalFocusMode(savedState.getBoolean("isModal"))
        }
    }

    private fun persistState() {
        val state = getInternalState()
        val stateFile = File(applicationContext.filesDir, "ui_state.json")
        stateFile.writeText(state.toString(2), StandardCharsets.UTF_8)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSavedState()

        // Initialize the main UI
        val binding = ActivityMainBinding.inflate(layoutInflater)
        contentView = binding.root
        setContentView(binding.root)

        btsl = BluetoothModalController(this, contentView)

        if (isModal) {
            btsl.activate()
        } else {
            btsl.deactivate()
        }

        // Initialize the helper UI fragments
        playerUI = PlayerFragment()
        libraryUI = LibraryFragment()

        // Set up the pager that allows swiping between the UI fragments
        viewPager = binding.viewPager
        viewPager.adapter = object: FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> extrasUI
                    1 -> playerUI
                    else -> libraryUI
                }
            }
            override fun getItemCount() = 3
        }
        viewPager.currentItem = 1
    }

    override fun onStart() {
        super.onStart()
        UIConnector.get().requestConnection(this, this)
    }

    override fun onStop() {
        UIConnector.get().abandonConnection()
        super.onStop()
    }

    override fun onDestroy() {
        persistState()
        super.onDestroy()
    }

    fun navigateToPlayer() {
        viewPager.currentItem = 1
    }

    fun navigateTo(parentIds: List<String>, childId: String) {
        viewPager.currentItem = 1
        libraryUI.navigateTo(LibraryNavigationRequest(parentIds, childId))
    }
}