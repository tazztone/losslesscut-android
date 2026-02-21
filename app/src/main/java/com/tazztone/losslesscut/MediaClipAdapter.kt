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
    private val onClipLongPressed: (Int) -> Unit
) : RecyclerView.Adapter<MediaClipAdapter.ClipViewHolder>() {

    private var clips: MutableList<MediaClip> = mutableListOf()
    private var selectedIndex: Int = 0

    fun updateClips(newClips: List<MediaClip>, newSelectedIndex: Int) {
        clips = newClips.toMutableList()
        selectedIndex = newSelectedIndex
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
        val binding = ItemMediaClipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
        holder.bind(clips[position], position == selectedIndex, position + 1)
    }

    override fun getItemCount(): Int = clips.size

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        onClipsReordered(from, to)
    }

    inner class ClipViewHolder(private val binding: ItemMediaClipBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(clip: MediaClip, isSelected: Boolean, order: Int) {
            binding.tvFileName.text = clip.fileName
            binding.tvOrder.text = order.toString()
            binding.vSelection.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            
            // In a real app, use Glide or Coil to load the thumbnail asynchronously.
            // For now, we'll try to load it from MediaStore/ThumbnailUtils.
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val size = android.util.Size(512, 512)
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
        }
    }
}
