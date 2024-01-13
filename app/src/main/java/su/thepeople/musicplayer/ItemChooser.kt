package su.thepeople.musicplayer

import android.view.LayoutInflater
import android.widget.Button
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import su.thepeople.musicplayer.databinding.ChooserItemBinding

class ItemChooser(
    private val libraryUI: LibraryFragment,
    private val listView: RecyclerView,
    private val breadcrumbView: RecyclerView,
    private val layoutInflater: LayoutInflater,
    private val browseAction: (MediaItem) -> Unit,  // TODO: Should this be MediaItem, or would String work fine?
    private val breadcrumbAction: (MediaItem) -> Unit) {

    private var items = ArrayList<MediaItem>()

    init {
        listView.setHasFixedSize(true)
        listView.layoutManager = LinearLayoutManager(listView.context)
        listView.adapter = Adapter(::handleUserSelection, items)
        breadcrumbView.setHasFixedSize(true)
        breadcrumbView.layoutManager = LinearLayoutManager(breadcrumbView.context)
        breadcrumbView.adapter = Adapter(::handleUserBackup, libraryUI.breadcrumbStack)
    }


    fun refresh(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        listView.adapter?.notifyDataSetChanged()
        breadcrumbView.adapter?.notifyDataSetChanged()
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

    private fun handleUserBackup(itemIndex: Int) {
        breadcrumbAction(libraryUI.breadcrumbStack[itemIndex])
    }
}