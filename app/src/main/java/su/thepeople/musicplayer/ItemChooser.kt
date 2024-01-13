package su.thepeople.musicplayer

import android.view.LayoutInflater
import android.widget.Button
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import su.thepeople.musicplayer.databinding.ChooserItemBinding

class ItemChooser(private val listView: RecyclerView, private val layoutInflater: LayoutInflater) {

    init {
        listView.setHasFixedSize(true)
        listView.layoutManager = LinearLayoutManager(listView.context)
        listView.adapter = Adapter()
    }

    private var items: List<MediaItem> = ArrayList()


    fun refresh(newItems: List<MediaItem>) {
        items = newItems
        listView.adapter?.notifyDataSetChanged()
    }

    private inner class ButtonViewHolder(private val button: Button): RecyclerView.ViewHolder(button) {

        fun associateWithItem(itemPosition: Int) {
            button.tag = itemPosition
            button.text = items[itemPosition].mediaMetadata.title ?: "Unknown"
        }

        fun getItemIndex(): Int {
            return (button.tag as? Int)!!
        }
    }

    private inner class Adapter: RecyclerView.Adapter<ItemChooser.ButtonViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
            val binding = ChooserItemBinding.inflate(layoutInflater)
            val button = binding.root
            val holder = ButtonViewHolder(button)
            button.setOnClickListener{_ -> handleUserSelection(holder.getItemIndex())}
            return holder
        }

        override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
            holder.associateWithItem(position)
        }

        override fun getItemCount(): Int {
            return items.size
        }
    }

    private fun handleUserSelection(itemIndex:Int) {
        // TODO
    }
}