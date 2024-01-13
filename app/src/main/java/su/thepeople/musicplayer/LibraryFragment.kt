package su.thepeople.musicplayer

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import com.google.common.collect.Lists
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import su.thepeople.musicplayer.databinding.FragmentLibraryBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class LibraryFragment(private val mainActivity: MainActivity) : Fragment() {

    private var _binding: FragmentLibraryBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var itemChooser: ItemChooser

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

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
            itemChooser.refresh(Lists.newArrayList(it.value!!))
        }
    }

    private fun browseTo(item: MediaItem) {
        Log.d("LibraryFragment", "Got " + item.mediaId)
        loadChildren(item.mediaId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity.mediaBrowser!!.getLibraryRoot(null).onSuccess {
            browseTo(it.value!!)
        }
        itemChooser = ItemChooser(binding.itemRecycler, layoutInflater)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}