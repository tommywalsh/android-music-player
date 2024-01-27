package su.thepeople.musicplayer

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import su.thepeople.musicplayer.databinding.FragmentLibraryBinding

/**
 * This UI Wrapper exists solely to manage the setup of the library UI.  Setup is a bit complicated, because we have two asynchronous operations:
 *    1) UI widget/view creation and finalization
 *    2) Backend session initialization
 *
 * These two things can happen in either order. Some work can happen when a single one of these is complete. Other work needs to wait until both of
 * them are.  If we were to combine all of this work into the same class, we'd have to carefully keep track of which operations had completed, and
 * modify the behavior accordingly. This would be relatively hard to reason about.
 *
 * Adding additional complication: backend session initialization is actually a two-step process, both of which are asynchronous.
 *
 * Instead, of one complicated class, we divide the work into two classes.  This wrapper's job is simply to wait until BOTH of the two async
 * operations are done, and then we create an associated "UI Implementation" object. That object actually handles all of the UI logic. That logic can
 * now be much more straightforward, because it knows that, by the time it starts running, ALL of the necessary setup work has already been performed.
 */
class LibraryUI(private val context: Context, private val mainUI: MainActivity) : Fragment() {

    /*
     * These lateinit variables are set by async processes. We may activate the actual UI logic only when ALL of these are initialized.
     */
    private lateinit var binding: FragmentLibraryBinding
    private lateinit var inflater: LayoutInflater
    private lateinit var backendLibrary: MediaBrowser
    private lateinit var rootItem: MediaItem

    // For convenience, we'll track completeness with these booleans (although we could instead opt to check if the lateinits are all set up)
    private var uiInitialized: Boolean = false
    private var backendInitialized: Boolean = false

    // This is the object that actually does the UI work
    private lateinit var impl: LibraryUIImpl

    // We might get a navigation request BEFORE the UI has been finalized. In that case, we should remember the request until finalization.
    private var startupParentIds: List<String>? = null
    private var startupChildId: String? = null


    /**
     * This method's job is simply to "inflate" our UI widgets and grab handles to them. The entire view creation process might not be completed yet
     * when this is called.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLibraryBinding.inflate(inflater, container, false)
        this.inflater = inflater
        Log.d("LibraryUI", "View is being created")
        return binding.root
    }

    /**
     * This function gets called after the entire view creation process is complete. Here is where we do our initialization logic.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The UI creation is done. If the backend initialization is also complete, then it's time to activate the implementation
        Log.d("LibraryUI", "View creation is now finished, so UI is ready")
        uiInitialized = true
        if (backendInitialized) {
            Log.d("LibraryUI", "Activating, since backend is also ready")
            activate()
        }
    }

    fun setBackendLibrary(newLibrary: MediaBrowser) {
        // Once we have the handle to the backend object, we kick off an async process to get the library's root object.
        backendLibrary = newLibrary
        Log.d("LibraryUI", "Backend handle acquired. Now asking for root object")
        backendLibrary.getLibraryRoot(MediaLibraryService.LibraryParams.Builder().build()).onSuccess(context, this::onRootReceived)
    }

    private fun onRootReceived(result: LibraryResult<MediaItem>) {
        val newRoot = result.value
        if (newRoot != null) {
            rootItem = newRoot
            Log.d("LibraryUI", "Root object acquired, so backend is ready")

            // Our async backend initialization is complete.  If the UI creation is also complete, it's time to activate the implementation
            backendInitialized = true
            if (uiInitialized) {
                Log.d("LibraryUI", "Activating, since UI is also ready")
                activate()
            }
        }
    }

    private fun activate() {
        impl = LibraryUIImpl(backendLibrary, rootItem, binding, inflater, context, mainUI)
        startupParentIds?.let {parentIds ->
            startupChildId?.let {childId ->
                impl.navigateTo(parentIds, childId)
            }
        }
    }

    fun navigateTo(parentIds: List<String>, childId: String) {
        if (::impl.isInitialized) {
            impl.navigateTo(parentIds, childId)
        } else {
            startupParentIds = parentIds
            startupChildId = childId
        }
    }
}