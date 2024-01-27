package su.thepeople.musicplayer

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionCommand
import su.thepeople.musicplayer.databinding.FragmentLibraryBinding

/**
 * Helper class to keep track of the current navigational state.
 */
class LibraryViewModel {
    val breadcrumbStack = ArrayDeque<MediaItem>()
    var currentItem: MediaItem? = null
    val childItems = ArrayList<MediaItem>()
}


/**
 * UI that allows users to browse the MCotP.  The UI is always focused on a single item (band, album, or other) and is composed of two parts:
 *  - A list of children. Selecting a child will cause the UI to change focus to that child, thus navigating "down".
 *  - A list of parents. Selecting a parent will also change focus. This navigates "back up". This is the "breadcrumb list"
 *
 * Future directions:
 *   - We should distinguish between "browsable" and non-browsable objects, and only allow focusing on browsable objects.
 *   - Users should be able to change what's playing by navigating to an item and selecting it.
 *   - There may even be multiple ways to "play" the same item (e.g. select a band to shuffle vs. all of their songs sequentially)
 */
class LibraryUIImpl(private val backendLibrary: MediaBrowser, private val rootItem: MediaItem, uiBinding: FragmentLibraryBinding, inflater: LayoutInflater, private val context: Context, private val mainUi: MainActivity) {

    // UI widgets
    private var childrenWidget = uiBinding.itemList
    private var breadcrumbWidget = uiBinding.breadcrumbs

    // Helpers to handle coordination between the on-screen lists and the state of the UI
    private val model = LibraryViewModel()
    private var childChooser = ItemChooser(model.childItems, childrenWidget, inflater, { browseTo(it) }, { requestSingleSong(it) })
    private var breadcrumbChooser = ItemChooser(model.breadcrumbStack, breadcrumbWidget, inflater, { backupTo(it) }, null)

    init {
        jumpToRoot()
    }

    private fun loadChildren(mediaId: String) {
        backendLibrary.getChildren(mediaId, 0, Int.MAX_VALUE, null).onSuccess(context) {
            model.childItems.clear()
            model.childItems.addAll(it.value!!)
            childChooser.refresh()
            breadcrumbChooser.refresh()
        }
    }

    private fun browseTo(item: MediaItem) {
        val id = item.mediaId
        Log.d("LibraryFragment", "Browsing from ${model.currentItem?.mediaMetadata?.title} to ${item.mediaMetadata.title}")
        model.breadcrumbStack.addFirst(model.currentItem!!)
        model.currentItem = item
        loadChildren(id)
        Log.d("LibraryUI", "Current item is now ${model.currentItem?.mediaMetadata?.title}")
        Log.d("LibraryUI", "Breadcrumbs have ${model.breadcrumbStack.size} items")
        Log.d("LibraryUI", "Top breadcrumb is ${model.breadcrumbStack.first().mediaMetadata.title}")
    }

    private fun backupTo(item: MediaItem) {
        Log.d("LibraryFragment", "Backing up from ${model.currentItem?.mediaMetadata?.title} to ${item.mediaMetadata.title}")
        var lastPopped: MediaItem?
        do {
            Log.d("LibraryFragment", "Removing breadcrumb for ${model.breadcrumbStack.first().mediaMetadata.title}")
            lastPopped = model.breadcrumbStack.removeFirstOrNull()
        } while (lastPopped != null && lastPopped.mediaId != item.mediaId)
        if (lastPopped == null) {
            jumpToRoot()
        } else {
            model.currentItem = lastPopped
            loadChildren(lastPopped.mediaId)
        }
        Log.d("LibraryUI", "Current item is now ${model.currentItem?.mediaMetadata?.title}")
        Log.d("LibraryUI", "Breadcrumbs have ${model.breadcrumbStack.size} items")
        Log.d("LibraryUI", "Top breadcrumb is ${model.breadcrumbStack.firstOrNull()?.mediaMetadata?.title}")
    }

    private fun requestSingleSong(item: MediaItem) {
        val id = item.mediaId
        val bundle = Bundle()
        bundle.putString("id", id)
        backendLibrary.sendCustomCommand(SessionCommand("play-item", bundle), bundle)
        mainUi.navigateToPlayer()
    }

    private fun jumpToRoot() {
        loadChildren(rootItem.mediaId)
        model.currentItem = rootItem
        model.breadcrumbStack.clear()
    }

    private fun navigateToChildAsync(childId: String, afterTask: (()->Unit)? = null) {
        Log.d("LibraryUIImpl", "About to dispatch async request for object $childId")
        backendLibrary.getItem(childId).onSuccess(context) {itemResult ->
            val newItem = itemResult.value!!
            Log.d("LibraryUIImpl", "Received async response for object $childId")
            Log.d("LibraryUIImpl", "About to dispatch async request for children of $childId")
            backendLibrary.getChildren(childId, 0, Int.MAX_VALUE, null).onSuccess(context) { childrenResult ->
                Log.d("LibraryUIImpl", "Received async response for children of $childId")
                model.currentItem?.let{id -> model.breadcrumbStack.add(0, id)}
                model.currentItem = newItem
                model.childItems.clear()
                model.childItems.addAll(childrenResult.value!!)
                if (afterTask != null) {
                        Log.d("LibraryUIImpl", "About to do aftertask for object $childId")
                        afterTask()
                }
            }
        }

    }

    fun navigateTo(parentIds: List<String>, childId: String) {
        val task = OfflineNavigationTask(parentIds, childId)
        task.kickoff()
    }

    inner class OfflineNavigationTask(parentIds: List<String>, childId: String) {
        //private val navigationTargets: ArrayList<String> = Lists.newArrayList<String>(ROOT_ID, BANDS_ID, bandId)
        private val navigationTargets = ArrayList<String>()
        private val focusTarget = childId
        init {
            navigationTargets.addAll(parentIds)
        }

        fun kickoff() {
            model.breadcrumbStack.clear()
            model.currentItem = null
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
                        Log.d("Library" +
                                "UIImpl", "Focus child $focusTarget found at position $i")
                        childrenWidget.scrollToPosition(i)
                        break
                    }
                }
            }
        }
    }
}