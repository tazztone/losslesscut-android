package com.tazztone.losslesscut.ui

import android.graphics.Bitmap
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.ItemMediaClipBinding
import com.tazztone.losslesscut.domain.model.MediaClip
import kotlinx.coroutines.*
import java.util.Collections
import java.util.UUID

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
    private var selectedClipId: UUID? = null
    var isDragging = false
        private set
    private var dragStartIndex: Int = -1

    fun submitList(newList: List<MediaClip>?) {
        if (isDragging) return
        val list = newList ?: emptyList()
        val oldList = clips.toList()
        clips = list.toMutableList()
        
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size + 1
            override fun getNewListSize() = clips.size + 1
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                if (o == oldList.size || n == clips.size) return o == oldList.size && n == clips.size
                return oldList[o].id == clips[n].id
            }
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                if (o == oldList.size || n == clips.size) return true
                return oldList[o] == clips[n]
            }
        })
        diff.dispatchUpdatesTo(this)
    }

    fun startDrag(from: Int) {
        dragStartIndex = from
        isDragging = true
    }

    fun updateSelection(newSelectedId: UUID?) {
        if (selectedClipId == newSelectedId) return
        
        val oldId = selectedClipId
        selectedClipId = newSelectedId
        
        // Find indices to notify
        clips.forEachIndexed { index, clip ->
            if (clip.id == oldId || clip.id == newSelectedId) {
                notifyItemChanged(index)
            }
        }
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
            val clip = clips[position]
            holder.bind(clip, clip.id == selectedClipId, position + 1)
        } else if (holder is AddViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int = clips.size + 1

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? ClipViewHolder)?.cancelThumbnail()
    }

    fun moveItemVisual(from: Int, to: Int) {
        if (from == to) return
        if (from >= clips.size || to >= clips.size) return
        
        val item = clips.removeAt(from)
        clips.add(to, item)
        
        notifyItemMoved(from, to)
    }

    fun commitPendingMove(finalTo: Int) {
        if (isDragging && dragStartIndex != -1 && dragStartIndex != finalTo) {
            onClipsReordered(dragStartIndex, finalTo)
        }
        isDragging = false
        dragStartIndex = -1
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

        fun cancelThumbnail() {
            thumbnailJob?.cancel()
            thumbnailJob = null
        }
    }

    inner class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            androidx.appcompat.widget.TooltipCompat.setTooltipText(itemView, itemView.context.getString(R.string.add_video))
            itemView.setOnClickListener { onAddClicked() }
        }
    }
}
