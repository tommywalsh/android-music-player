package su.thepeople.musicplayer

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
 *   - We should distinguish between "browsable" and non-browsable objects, and only alloq focusing on browsable objects.
 *   - Users should be able to change what's playing by navigating to an item and selecting it.
 *   - There may even be multiple ways to "play" the same item (e.g. select a band to shuffle vs. all of their songs sequentially)
 */
class LibraryUI(private val mainActivity: MainActivity) : Fragment() {

    private var _binding: FragmentLibraryBinding? = null

    private val binding get() = _binding!!

    private val model = LibraryViewModel()
    private lateinit var childChooser: ItemChooser
    private lateinit var breadcrumbChooser: ItemChooser


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Helper function to associate a callback with a future without having to define a class at the callsite
     */
    private fun <T> ListenableFuture<T>.onSuccess(callback: (T) -> Unit) {
        Futures.addCallback(this, object: FutureCallback<T> {
            override fun onSuccess(value: T) {
                callback(value)
            }
            override fun onFailure(t: Throwable) {
                // TODO: what to do about errors?
            }
        }, ContextCompat.getMainExecutor(mainActivity))
    }

    private fun loadChildren(mediaId: String) {
        mainActivity.mediaBrowser!!.getChildren(mediaId, 0, Int.MAX_VALUE, null).onSuccess {
            model.childItems.clear()
            model.childItems.addAll(it.value!!)
            childChooser.refresh()
            breadcrumbChooser.refresh()
        }
    }

    private fun browseTo(item: MediaItem) {
        val id = item.mediaId
        Log.d("LibraryFragment","Browsing from ${model.currentItem?.mediaMetadata?.title} to ${item.mediaMetadata.title}")
        model.breadcrumbStack.addFirst(model.currentItem!!)
        model.currentItem = item
        loadChildren(id)
        Log.d("LibraryFragment", "Current item is now ${model.currentItem?.mediaMetadata?.title}")
        Log.d("LibraryFragment", "Breadcrumbs have ${model.breadcrumbStack.size} items")
        Log.d("LibraryFragment", "Top breadcrumb is ${model.breadcrumbStack.first().mediaMetadata.title}")
    }

    private fun backupTo(item: MediaItem) {
        Log.d("LibraryFragment","Backing up from ${model.currentItem?.mediaMetadata?.title} to ${item.mediaMetadata.title}")
        var lastPopped: MediaItem?
        do {
            Log.d("LibraryFragment","Removing breadcrumb for ${model.breadcrumbStack.first().mediaMetadata.title}")
            lastPopped = model.breadcrumbStack.removeFirstOrNull()
        } while (lastPopped != null && lastPopped.mediaId != item.mediaId)
        if (lastPopped == null) {
            jumpToRoot()
        } else {
            model.currentItem = lastPopped
            loadChildren(lastPopped.mediaId)
        }
        Log.d("LibraryFragment", "Current item is now ${model.currentItem?.mediaMetadata?.title}")
        Log.d("LibraryFragment", "Breadcrumbs have ${model.breadcrumbStack.size} items")
        Log.d("LibraryFragment", "Top breadcrumb is ${model.breadcrumbStack.firstOrNull()?.mediaMetadata?.title}")
    }

    private fun jumpToRoot() {
        loadChildren(rootItem.mediaId)
        model.currentItem = rootItem
        model.breadcrumbStack.clear()
    }

    private lateinit var rootItem: MediaItem
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity.mediaBrowser!!.getLibraryRoot(null).onSuccess {
            rootItem = it.value!!
            jumpToRoot()
        }
        childChooser = ItemChooser (
            model.childItems,
            binding.itemList,
            layoutInflater
        ) { browseTo(it) }
        breadcrumbChooser = ItemChooser (
            model.breadcrumbStack,
            binding.breadcrumbs,
            layoutInflater
        ) { backupTo(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}