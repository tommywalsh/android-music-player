package su.thepeople.musicplayer.ui

import android.os.Bundle
import android.util.ArraySet
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.ListenableFuture
import su.thepeople.musicplayer.databinding.LibraryUiBinding
import su.thepeople.musicplayer.onSuccess
import java.io.Closeable

/**
 * This class is the UI fragment for our music library browser.  It contains just enough logic to get itself hooked up to the rest of the system.
 */

class LibraryFragment : Fragment(), ConnectableUI {

    // Connections to the rest of the system. These are set some time after construction.
    private var mainUI : MainUI? = null
    private var backendLibrary: MediaBrowser? = null

    // UI-related objects are set up when view is created.
    private lateinit var binding: LibraryUiBinding
    private lateinit var inflater: LayoutInflater

    // This is the object that actually does the UI work.
    private var logic: LibraryLogic? = null

    // We might get a navigation request BEFORE the UI has been finalized. In that case, we should remember the request and fulfill it later.
    private var pendingNavigationRequest: LibraryNavigationRequest? = null

    // We have a transient state during startup where we might have a pending request for the backend's library root. We might need to cancel this.
    private var initialBackendRequest: ListenableFuture<LibraryResult<MediaItem>>? = null

    /**
     * This method's job is simply to "inflate" our UI widgets and grab handles to them. The entire view creation process might not be completed yet
     * when this is called.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("LibraryUI", "View is being created")
        binding = LibraryUiBinding.inflate(inflater, container, false)
        this.inflater = inflater
        return binding.root
    }

    override fun onStart() {
        Log.d("LibraryUI", "Starting")
        super.onStart()
        UIConnector.get().registerUI(this)
    }

    override fun onStop() {
        Log.d("LibraryUI", "Stopping")

        logic?.close()
        logic = null

        initialBackendRequest?.cancel(true)

        UIConnector.get().unregisterUI(this)

        super.onStop()
    }

    override fun connect(connectionData: UIConnectionData) {
        Log.d("LibraryUI", "Connected to backend")

        // When we get a new connection, any previous impl is no good (since it is working with an out-of-date connection)
        logic?.close()
        logic = null
        mainUI = connectionData.mainUI
        backendLibrary = connectionData.connection

        // The backend connection is now ready. The first thing we need to do is to retrieve the backend's root item. This is done async.
        Log.d("LibraryUI", "Requesting root item from backend")
        val req = connectionData.connection.getLibraryRoot(MediaLibraryService.LibraryParams.Builder().build())
        initialBackendRequest = req
        req.onSuccess(connectionData.mainUI) {
            initialBackendRequest = null
            onRootReceived(it)
        }
     }


    private fun onRootReceived(result: LibraryResult<MediaItem>) {
        Log.d("LibraryUI", "Root item received from backend. Setting up logic.")

        // We should never get here unless we already have the backend set up (and therefore the library and mainUI handles)
        result.value?.let {root->
            logic = LibraryLogic(backendLibrary!!, root, binding, inflater, mainUI!!)
            pendingNavigationRequest?.let{nav->navigateTo(nav)}
        }
        pendingNavigationRequest = null
    }

    fun navigateTo(request: LibraryNavigationRequest){
        pendingNavigationRequest = request
        logic?.let {
            it.navigateTo(request.parentIds, request.childId)
            pendingNavigationRequest = null
        }
    }
}

class LibraryNavigationRequest (val parentIds: List<String>, val childId: String)

class LibraryViewModel {
    val breadcrumbStack = ArrayDeque<MediaItem>()
    var currentItem: MediaItem? = null
    val childItems = ArrayList<MediaItem>()
}

class LibraryLogic(private val backendLibrary: MediaBrowser,
                   private val rootItem: MediaItem,
                   uiBinding: LibraryUiBinding,
                   inflater: LayoutInflater,
                   private val mainUI: MainUI
)
    : Closeable {

    override fun close() {
        // Here we just have to disconnect any callbacks or registrations we've done. We don't need to bother with "nulling out" any properties
        outstandingAsyncCalls.forEach {it.cancel(true)}
    }

    // UI widgets
    private var childrenWidget = uiBinding.itemList
    private var breadcrumbWidget = uiBinding.breadcrumbs
    private var titleWidget = uiBinding.title

    // Helpers to handle coordination between the on-screen lists and the state of the UI
    private val model = LibraryViewModel()
    private var childChooser = ChildListUI(model, childrenWidget, inflater, { browseTo(it) }, {sendRequestToPlayer(it)})
    private var breadcrumbChooser = BreadcrumbListUI(model, breadcrumbWidget, inflater) {backupTo(it)}

    // List of async calls that have not yet returned. These can be cancelled if no longer needed
    private val outstandingAsyncCalls = ArraySet<ListenableFuture<*>>()

    init {
        jumpToRoot()
    }

    private fun loadChildren(mediaId: String) {
        Log.d("LibraryUI", "Sending request for children of $mediaId")

        runAsync({backendLibrary.getChildren(mediaId, 0, Int.MAX_VALUE, null)}, {
            model.childItems.clear()
            model.childItems.addAll(it.value!!)
            childChooser.refresh()
            breadcrumbChooser.refresh()
        })
    }

    private fun setCurrent(item: MediaItem?) {
        model.currentItem = item
        val newText = item?.mediaMetadata?.displayTitle ?: ""
        titleWidget.text = newText
    }

    private fun browseTo(item: MediaItem) {
        val id = item.mediaId
        Log.d("LibraryUI", "Browsing from ${model.currentItem?.mediaMetadata?.title} to ${item.mediaMetadata.title}")
        model.breadcrumbStack.addFirst(model.currentItem!!)
        setCurrent(item)
        loadChildren(id)
        Log.d("LibraryUI", "Current item is now ${model.currentItem?.mediaMetadata?.title}")
        Log.d("LibraryUI", "Breadcrumbs have ${model.breadcrumbStack.size} items")
        Log.d("LibraryUI", "Top breadcrumb is ${model.breadcrumbStack.first().mediaMetadata.title}")
    }

    private fun backupTo(item: MediaItem) {
        Log.d("LibraryUI", "Backing up from ${model.currentItem?.mediaMetadata?.title} to ${item.mediaMetadata.title}")
        var lastPopped: MediaItem?
        do {
            Log.d("LibraryUI", "Removing breadcrumb for ${model.breadcrumbStack.first().mediaMetadata.title}")
            lastPopped = model.breadcrumbStack.removeFirstOrNull()
        } while (lastPopped != null && lastPopped.mediaId != item.mediaId)
        if (lastPopped == null) {
            jumpToRoot()
        } else {
            setCurrent(lastPopped)
            loadChildren(lastPopped.mediaId)
        }
        Log.d("LibraryUI", "Current item is now ${model.currentItem?.mediaMetadata?.title}")
        Log.d("LibraryUI", "Breadcrumbs have ${model.breadcrumbStack.size} items")
        Log.d("LibraryUI", "Top breadcrumb is ${model.breadcrumbStack.firstOrNull()?.mediaMetadata?.title}")
    }

    private fun sendRequestToPlayer(item: MediaItem) {
        Log.d("LibraryUI", "Sending request to play ${item.mediaId}")
        val id = item.mediaId
        val bundle = Bundle()
        bundle.putString("id", id)
        backendLibrary.sendCustomCommand(SessionCommand("play-item", bundle), bundle)
        mainUI.navigateToPlayer()
    }

    private fun jumpToRoot() {
        loadChildren(rootItem.mediaId)
        setCurrent(rootItem)
        model.breadcrumbStack.clear()
    }

    // Helper to run a backend async call and allows it to be cancelled if no longer needed.
    private fun<T> runAsync(asyncCall: ()-> ListenableFuture<T>, successCallback: (T)->Unit) {
        val item = asyncCall()
        outstandingAsyncCalls.add(item)
        item.onSuccess(mainUI) {
            outstandingAsyncCalls.remove(item)
            successCallback(it)
        }
    }

    private fun navigateToChildAsync(childId: String, afterTask: (()->Unit)? = null) {
        Log.d("LibraryUI", "About to dispatch async request for object $childId")
        runAsync({backendLibrary.getItem(childId)} ,{itemResult ->
            val newItem = itemResult.value!!
            Log.d("LibraryUI", "Received async response for object $childId")
            Log.d("LibraryUI", "About to dispatch async request for children of $childId")
            runAsync({backendLibrary.getChildren(childId, 0, Int.MAX_VALUE, null)}, { childrenResult ->
                Log.d("LibraryUI", "Received async response for children of $childId")
                model.currentItem?.let { id -> model.breadcrumbStack.add(0, id) }
                setCurrent(newItem)
                model.childItems.clear()
                model.childItems.addAll(childrenResult.value!!)
                if (afterTask != null) {
                    Log.d("LibraryUI", "About to do aftertask for object $childId")
                    afterTask()
                }
            })
        })
    }

    fun navigateTo(parentIds: List<String>, childId: String) {
        val task = OfflineNavigationTask(parentIds, childId)
        task.kickoff()
    }

    inner class OfflineNavigationTask(parentIds: List<String>, childId: String) {
        private val navigationTargets = ArrayList<String>()
        private val focusTarget = childId
        init {
            navigationTargets.addAll(parentIds)
        }

        fun kickoff() {
            model.breadcrumbStack.clear()
            setCurrent(null)
            model.childItems.clear()
            runNextTask()
        }

        private fun runNextTask() {
            if (navigationTargets.isNotEmpty()) {
                val navTarget = navigationTargets.removeFirst()
                navigateToChildAsync(navTarget) {
                    runNextTask()
                }
            } else {
                // Our final task is to refresh the UI and focus on the focus target
                childChooser.refresh()
                breadcrumbChooser.refresh()

                // Our child lists never get super large, so this inefficient linear search should not be a problem
                for (i in 0..model.childItems.size) {
                    if (model.childItems[i].mediaId == focusTarget) {
                        Log.d("LibraryUI", "Focus child $focusTarget found at position $i")
                        childrenWidget.scrollToPosition(i)
                        break
                    }
                }
            }
        }
    }
}