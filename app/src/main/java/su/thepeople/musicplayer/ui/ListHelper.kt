package su.thepeople.musicplayer.ui

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import su.thepeople.musicplayer.databinding.BreadcrumbChooserItemBinding
import su.thepeople.musicplayer.databinding.ChildChooserItemBinding

abstract class ListHelper<B: ViewBinding>(
    private val backingList: List<MediaItem>,
    private val listView: RecyclerView,
    private val layoutInflater: LayoutInflater,
    layoutInitializer: () -> Unit
    ) {

    /*
     * Subclasses are in charge of "inflating" the UI, because only the subclass knows the view type, which is needed to inflate.
     * Note that there is no need for subclasses to cache this inflated binding since it is stored in the lateinit var. Subclasses are guaranteed
     * that this binding object will always be valid during either of the other abstract functions.
     */
    abstract fun inflateBinding(inflater: LayoutInflater): B
    protected lateinit var binding: B

    /**
     * Do any one-time initialization of UI objects that does not depend on what item is being shown. Note that the "getItem" function will return
     * null if it is called during execution of `initializeItemUI`. However, the function may be stored and used later -- for example, it can be
     * part of a UI widget's callback.
     */
    abstract fun initializeItemUI(binding: B, getItem: (()->MediaItem?))

    // Do any required per-object customization of the UI objects
    abstract fun customizeUIForItem(binding: B, item: MediaItem)


    inner class Holder(val binding: B) : RecyclerView.ViewHolder(binding.root) {

        private var index: Int? = null

        fun associateWithItem(itemPosition: Int) {
            index = itemPosition
        }

        fun getItem(): MediaItem? {
            return index?.let{backingList[it]}
        }
    }

    private inner class Adapter: RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = inflateBinding(layoutInflater)
            val holder = Holder(binding)
            initializeItemUI(binding, holder::getItem)
            return holder
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.associateWithItem(position)
            customizeUIForItem(holder.binding, holder.getItem()!!)
        }

        override fun getItemCount(): Int {
            return backingList.size
        }
    }


    init {
        listView.setHasFixedSize(false)  // "Size" here refers to widget size, not length of item list
        layoutInitializer()
        listView.adapter = Adapter()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refresh() {
        listView.adapter?.notifyDataSetChanged()
        listView.requestLayout()
    }
}

class ChildListUI(
    model: LibraryViewModel,
    val listView: RecyclerView,
    private val layoutInflater: LayoutInflater,
    private val browseAction: (MediaItem) -> Unit,
    private val playAction: ((MediaItem) -> Unit)
) : ListHelper<ChildChooserItemBinding>(model.childItems, listView, layoutInflater, {
    listView.layoutManager = LinearLayoutManager(listView.context)
}) {


    private fun navigationRequest(item: MediaItem) {
        Log.d("LibraryUI", "Navigation request received")
        if (item.mediaMetadata.isBrowsable == true) {
            browseAction(item)
        }
    }

    private fun playRequest(item: MediaItem) {
        if (item.mediaMetadata.isPlayable == true) {
            playAction(item)
        }
    }

    override fun inflateBinding(inflater: LayoutInflater): ChildChooserItemBinding {
        return ChildChooserItemBinding.inflate(layoutInflater)
    }

    override fun initializeItemUI(binding: ChildChooserItemBinding, getItem: () -> MediaItem?) {
        binding.navigateButton.setOnClickListener {_ -> navigationRequest(getItem()!!)}
        binding.playButton.setOnClickListener{_ -> playRequest(getItem()!!)}
    }

    override fun customizeUIForItem(binding: ChildChooserItemBinding, item: MediaItem) {
        binding.navigateButton.text = item.mediaMetadata.title ?: "Unknown"
        binding.nonNavigateLabel.text = item.mediaMetadata.title ?: "Unknown"
        binding.playButton.visibility = if (item.mediaMetadata.isPlayable == true ) View.VISIBLE else View.INVISIBLE
        binding.navigateButton.visibility = if (item.mediaMetadata.isBrowsable == true ) View.VISIBLE else View.INVISIBLE
        binding.nonNavigateLabel.visibility = if (item.mediaMetadata.isBrowsable == true ) View.INVISIBLE else View.VISIBLE
    }
}

class BreadcrumbListUI(
    model: LibraryViewModel,
    listView: RecyclerView,
    private val layoutInflater: LayoutInflater,
    private val backAction: (MediaItem) -> Unit
) : ListHelper<BreadcrumbChooserItemBinding>(model.breadcrumbStack, listView, layoutInflater, {
    listView.layoutManager = LinearLayoutManager(listView.context, LinearLayoutManager.HORIZONTAL, true)
}) {


    override fun inflateBinding(inflater: LayoutInflater): BreadcrumbChooserItemBinding {
        return BreadcrumbChooserItemBinding.inflate(layoutInflater)
    }

    override fun initializeItemUI(binding: BreadcrumbChooserItemBinding, getItem: () -> MediaItem?) {
        binding.navigateButton.setOnClickListener {_ -> backAction(getItem()!!)}
    }

    override fun customizeUIForItem(binding: BreadcrumbChooserItemBinding, item: MediaItem) {
        binding.navigateButton.text = item.mediaMetadata.title ?: "Unknown"
    }
}