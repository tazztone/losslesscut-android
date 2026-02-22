package com.tazztone.losslesscut.ui

import android.graphics.Bitmap
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.ItemMediaClipBinding
import com.tazztone.losslesscut.viewmodel.MediaClip
import kotlinx.coroutines.*
import java.util.Collections

class MediaClipAdapter(
    private val onClipSelected: (Int) -> Unit,
    private val onClipsReordered: (Int, Int) -> Unit,
    private val onClipLongPressed: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onAddClicked: () -> Unit
) : ListAdapter<MediaClip, RecyclerView.ViewHolder>(ClipDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_CLIP = 0
        private const val VIEW_TYPE_ADD = 1
    }

    private var selectedIndex: Int = 0

    fun updateSelection(newSelectedIndex: Int) {
        val oldIndex = selectedIndex
        selectedIndex = newSelectedIndex
        notifyItemChanged(oldIndex)
        notifyItemChanged(selectedIndex)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < currentList.size) VIEW_TYPE_CLIP else VIEW_TYPE_ADD
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
            holder.bind(getItem(position), position == selectedIndex, position + 1)
        } else if (holder is AddViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int = currentList.size + 1

    fun moveItem(from: Int, to: Int): Boolean {
        if (from == to) return true
        if (from >= currentList.size || to >= currentList.size) return false
        
        // Update selectedIndex if it's affected by the move
        if (selectedIndex == from) {
            selectedIndex = to
        } else if (from < selectedIndex && to >= selectedIndex) {
            selectedIndex--
        } else if (from > selectedIndex && to <= selectedIndex) {
            selectedIndex++
        }

        onClipsReordered(from, to)
        return true
    }

    inner class ClipViewHolder(private val binding: ItemMediaClipBinding) : RecyclerView.ViewHolder(binding.root) {
        private var thumbnailJob: Job? = null

        fun bind(clip: MediaClip, isSelected: Boolean, order: Int) {
            thumbnailJob?.cancel()
            binding.tvFileName.text = clip.fileName
            binding.tvOrder.text = order.toString()
            binding.vSelection.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            
            binding.ivThumbnail.setImageResource(R.drawable.ic_banner_foreground)
            
            thumbnailJob = CoroutineScope(Dispatchers.Main).launch {
                val thumbnail = withContext(Dispatchers.IO) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val size = Size(128, 128)
                            binding.root.context.contentResolver.loadThumbnail(clip.uri, size, null)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
                if (isActive) {
                    if (thumbnail != null) {
                        binding.ivThumbnail.setImageBitmap(thumbnail)
                    } else {
                        binding.ivThumbnail.setImageResource(R.drawable.ic_banner_foreground)
                    }
                }
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
            androidx.appcompat.widget.TooltipCompat.setTooltipText(itemView, itemView.context.getString(R.string.add_video))
            itemView.setOnClickListener { onAddClicked() }
        }
    }

    class ClipDiffCallback : DiffUtil.ItemCallback<MediaClip>() {
        override fun areItemsTheSame(oldItem: MediaClip, newItem: MediaClip): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MediaClip, newItem: MediaClip): Boolean = oldItem == newItem
    }
}
