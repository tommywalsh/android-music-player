package su.thepeople.musicplayer.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import su.thepeople.musicplayer.databinding.ActivityMainBinding

// Each variant of this application must provide a global variable CUSTOMIZER that implements this:
abstract class MainUICustomizer {
    abstract fun onCreateActivity(ui: Activity, mainView: View)
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

    private lateinit var playerUI: PlayerFragment
    private lateinit var libraryUI: LibraryFragment
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the main UI
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CUSTOMIZER.onCreateActivity(this, binding.root)

        // Initialize the helper UI fragments
        playerUI = PlayerFragment()
        libraryUI = LibraryFragment()

        // Set up the pager that allows swiping between the UI fragments
        viewPager = binding.viewPager
        viewPager.adapter = object: FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> playerUI
                    else -> libraryUI
                }
            }
            override fun getItemCount() = 2
        }
    }

    override fun onStart() {
        super.onStart()
        UIConnector.get().requestConnection(this, this)
    }

    override fun onStop() {
        UIConnector.get().abandonConnection()
        super.onStop()
    }

    fun navigateToPlayer() {
        viewPager.currentItem = 0
    }

    fun navigateTo(parentIds: List<String>, childId: String) {
        viewPager.currentItem = 1
        libraryUI.navigateTo(LibraryNavigationRequest(parentIds, childId))
    }
}