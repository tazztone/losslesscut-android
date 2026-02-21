package com.tazztone.losslesscut

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tazztone.losslesscut.databinding.ItemMediaClipBinding
import java.util.Collections

class MediaClipAdapter(
    private val onClipSelected: (Int) -> Unit,
    private val onClipsReordered: (Int, Int) -> Unit,
    private val onClipLongPressed: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onAddClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_CLIP = 0
        private const val VIEW_TYPE_ADD = 1
    }

    private var clips: MutableList<MediaClip> = mutableListOf()
    private var selectedIndex: Int = 0

    fun updateClips(newClips: List<MediaClip>, newSelectedIndex: Int) {
        clips = newClips.toMutableList()
        selectedIndex = newSelectedIndex
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < clips.size) VIEW_TYPE_CLIP else VIEW_TYPE_ADD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_CLIP) {
            val binding = ItemMediaClipBinding.inflate(inflater, parent, false)
            ClipViewHolder(binding)
        } else {
            val view = inflater.inflate(R.layout.item_playlist_add, parent, false)
            AddViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ClipViewHolder) {
            holder.bind(clips[position], position == selectedIndex, position + 1)
        } else if (holder is AddViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int = clips.size + 1

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        // Don't allow moving the "Add" placeholder
        if (from >= clips.size || to >= clips.size) return
        
        Collections.swap(clips, from, to)
        notifyItemMoved(from, to)
        onClipsReordered(from, to)
    }

    inner class ClipViewHolder(private val binding: ItemMediaClipBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(clip: MediaClip, isSelected: Boolean, order: Int) {
            binding.tvFileName.text = clip.fileName
            binding.tvOrder.text = order.toString()
            binding.vSelection.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val size = android.util.Size(128, 128)
                    val thumbnail = binding.root.context.contentResolver.loadThumbnail(clip.uri, size, null)
                    binding.ivThumbnail.setImageBitmap(thumbnail)
                }
            } catch (e: Exception) {
                binding.ivThumbnail.setImageResource(R.drawable.ic_banner_foreground)
            }

            binding.root.setOnClickListener { onClipSelected(bindingAdapterPosition) }
            binding.root.setOnLongClickListener { 
                onClipLongPressed(bindingAdapterPosition)
                true
            }
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }
    }

    inner class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            itemView.setOnClickListener { onAddClicked() }
        }
    }
}
