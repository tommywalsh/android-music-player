package su.thepeople.musicplayer

import android.annotation.SuppressLint
import android.view.LayoutInflater
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
    ) {

    init {
        listView.setHasFixedSize(true)  // "Size" here refers to widget size, not length of item list
        listView.layoutManager = LinearLayoutManager(listView.context)
        listView.adapter = Adapter(::handleUserSelection, items)
    }

    // Our use cases either involve a very small list (breadcrumbs), or else a complete change of the entire
    // list (children). Therefore, notifyDataSetChanged is not inefficient, and we suppress this warning
    @SuppressLint("NotifyDataSetChanged")
    fun refresh() {
        listView.adapter?.notifyDataSetChanged()
    }

    private class ButtonViewHolder(private val button: Button, private val itemList: List<MediaItem>): RecyclerView.ViewHolder(button) {

        fun associateWithItem(itemPosition: Int) {
            button.tag = itemPosition
            button.text = itemList[itemPosition].mediaMetadata.title ?: "Unknown"
        }

        fun getItemIndex(): Int {
            return (button.tag as? Int)!!
        }
    }

    private inner class Adapter(val onClick: (Int) -> Unit, val itemList: List<MediaItem>): RecyclerView.Adapter<ButtonViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
            val binding = ChooserItemBinding.inflate(layoutInflater)
            val button = binding.root
            val holder = ButtonViewHolder(button, itemList)
            button.setOnClickListener{_ -> onClick(holder.getItemIndex())}
            return holder
        }

        override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
            holder.associateWithItem(position)
        }

        override fun getItemCount(): Int {
            return itemList.size
        }
    }

    private fun handleUserSelection(itemIndex:Int) {
        browseAction(items[itemIndex])
    }
}