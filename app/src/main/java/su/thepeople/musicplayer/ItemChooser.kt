package su.thepeople.musicplayer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import su.thepeople.musicplayer.databinding.ChooserItemBinding

/**
 * This is a simple but generic class representing a graphical pick list of buttons, where each button represents a MediaItem
 * Whoever sets up the ItemChooser decides what exactly happens when a user presses one of the buttons.
 */
class ItemChooser(
    private val items: List<MediaItem>,  // TODO: Consider using livedata for this?
    private val listView: RecyclerView,
    private val layoutInflater: LayoutInflater,
    private val browseAction: (MediaItem) -> Unit,
    private val playAction: ((MediaItem) -> Unit)?,
    ) {

    init {
        listView.setHasFixedSize(true)  // "Size" here refers to widget size, not length of item list
        listView.layoutManager = LinearLayoutManager(listView.context)
        listView.adapter = Adapter(::handleNavRequest, ::handlePlayRequest, items)
    }

    // Our use cases either involve a very small list (breadcrumbs), or else a complete change of the entire
    // list (children). Therefore, notifyDataSetChanged is not inefficient, and we suppress this warning
    @SuppressLint("NotifyDataSetChanged")
    fun refresh() {
        listView.adapter?.notifyDataSetChanged()
    }

    private inner class ButtonViewHolder(private val root: View, private val navigateButton: Button, private val playButton: Button, private val itemList: List<MediaItem>): RecyclerView.ViewHolder(root) {

        fun associateWithItem(itemPosition: Int) {
            val item = itemList[itemPosition]
            root.tag = itemPosition
            navigateButton.text = item.mediaMetadata.title ?: "Unknown"
            navigateButton.isActivated = item.mediaMetadata.isBrowsable?:false
            playButton.visibility = if ((playAction != null) && (item.mediaMetadata.isPlayable == true )) View.VISIBLE else View.INVISIBLE
        }

        fun getItemIndex(): Int {
            return (root.tag as? Int)!!
        }
    }

    private inner class Adapter(val navClick: (Int) -> Unit, val playClick: (Int) -> Unit, val itemList: List<MediaItem>): RecyclerView.Adapter<ButtonViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
            val binding = ChooserItemBinding.inflate(layoutInflater)
            val holder = ButtonViewHolder(binding.root, binding.navigateButton, binding.playButton, itemList)
            binding.navigateButton.setOnClickListener{_ -> navClick(holder.getItemIndex())}
            binding.playButton.setOnClickListener{_ -> playClick(holder.getItemIndex())}
            return holder
        }

        override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
            holder.associateWithItem(position)
        }

        override fun getItemCount(): Int {
            return itemList.size
        }
    }

    private fun handleNavRequest(itemIndex:Int) {
        browseAction(items[itemIndex])
    }

    private fun handlePlayRequest(itemIndex: Int) {
        playAction?.invoke(items[itemIndex])
    }
}