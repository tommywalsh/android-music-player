package su.thepeople.musicplayer.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONObject
import su.thepeople.musicplayer.data.Database
import su.thepeople.musicplayer.databinding.ActivityMainBinding
import su.thepeople.musicplayer.tools.Scanner
import su.thepeople.musicplayer.tools.runInBackground
import java.io.File
import java.nio.charset.StandardCharsets


class NormalModeAdapter(private val mainUI: MainUI): FragmentStateAdapter(mainUI) {
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> mainUI.extrasUI
            1 -> mainUI.playerUI
            else -> mainUI.libraryUI
        }
    }
    override fun getItemCount() = 3
}

class SimpleAdapter(mainUI: MainUI, private val fragment: Fragment): FragmentStateAdapter(mainUI) {
    override fun createFragment(position: Int): Fragment {
        return fragment
    }
    override fun getItemCount() = 1
}



/**
 * Main application UI.  This class largely handles setup and teardown. Most of the app logic lives in other classes.
 *
 * Full setup of the UI happens like this:
 *   1) in onCreate, UI objects are created, but are not yet functional
 *   2) in onStart, we kick off the background process of creating a connection to the backend
 *   3) All of the remaining UI and communication setup is done elsewhere (see UIConnector, and each of the component UIs for more)
 */
class MainUI : FragmentActivity() {

    lateinit var playerUI: PlayerFragment
    lateinit var libraryUI: LibraryFragment
    private val noPermissionUI = NoPermissionUI()
    private val scanningUI = ScanningUI()
    val extrasUI = ExtrasFragment(this)
    private lateinit var viewPager: ViewPager2
    private lateinit var contentView: View
    private lateinit var btsl: BluetoothModalController

    var isModal: Boolean = false
        private set
    var isWakeup: Boolean = false
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

    fun setWakeupMode(newWakeup: Boolean = true) {
        isWakeup = newWakeup
        // TODO: actually toggle wakeup mode
    }

    fun onSongChange() {
        if (isModal and this::btsl.isInitialized) {
            btsl.renewScreenLock()
        }
    }

    private fun getInternalState(): JSONObject {
        val state = JSONObject()
        state.put("isModal", isModal)
        state.put("isWakeup", isWakeup)
        return state
    }

    private fun loadSavedState() {
        val stateFile = File(applicationContext.filesDir, "ui_state.json")
        if (stateFile.isFile) {
            val savedState = JSONObject(stateFile.readText(StandardCharsets.UTF_8))
            setModalFocusMode(savedState.getBoolean("isModal"))
            setWakeupMode(savedState.getBoolean("isWakeup"))
        }
    }

    private fun persistState() {
        val state = getInternalState()
        val stateFile = File(applicationContext.filesDir, "ui_state.json")
        stateFile.writeText(state.toString(2), StandardCharsets.UTF_8)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the main UI
        val binding = ActivityMainBinding.inflate(layoutInflater)
        contentView = binding.root
        setContentView(binding.root)

        // Initialize the helper UI fragments
        playerUI = PlayerFragment()
        libraryUI = LibraryFragment()

        viewPager = binding.viewPager
    }

    override fun onStart() {
        super.onStart()

        // We have three possible startup situations:
        if (isDatabaseInitialized()) {
            // Possibility 1: We've already scanned the music collection, and therefore we can begin normal operation immediately
            beginNormalOperation()
        } else {
            // In Version R and above, we have to be a "manager" in order to read a JSON file from the SD card.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
                // Possibility 2: We have not scanned the collection yet, but we do have permission to do so, so do it now.
                beginScanningOperation()
            } else {
                // Possibility 3: We have no permission to scan the collection. So ask for it. (If granted, the app will restart)
                beginNoPermissionOperation()
            }
        }
    }

    private fun beginNormalOperation() {
        btsl = BluetoothModalController(this, contentView)

        if (isModal) {
            btsl.activate()
        } else {
//            btsl.deactivate()
        }

        viewPager.adapter = NormalModeAdapter(this)
        viewPager.currentItem = 1

        loadSavedState()

        UIConnector.get().requestConnection(this, this)
    }

    private fun beginNoPermissionOperation() {
        noPermissionUI.connect(this)
        viewPager.adapter = SimpleAdapter(this, noPermissionUI)
    }

    private fun beginScanningOperation() {

        viewPager.adapter = SimpleAdapter(this, scanningUI)

        // The scan will begin automatically once the connection is made
        // UIConnector.get().requestConnection(this, this)

        runInBackground(this, {doScan()}, {onScanComplete()})
    }

    // This function must not be run on the main UI thread
    private fun doScan() {
        val database = Room.databaseBuilder(applicationContext, Database::class.java, "mcotp-database").build()
        val scanner = Scanner(applicationContext, database)
        scanner.fullScan()
        val stateFile = File(applicationContext.filesDir, "DB_INITIALIZED")
        stateFile.writeText("Completed", StandardCharsets.UTF_8)
    }

    private fun onScanComplete() {
        beginNormalOperation()
    }

    private fun isDatabaseInitialized(): Boolean {
        val stateFile = File(applicationContext.filesDir, "DB_INITIALIZED")
        return stateFile.isFile
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 666 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            btsl.registerForBluetoothNotificationsNow()
        }
    }
}