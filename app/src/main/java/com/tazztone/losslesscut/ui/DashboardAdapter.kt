package com.tazztone.losslesscut.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tazztone.losslesscut.databinding.ItemDashboardActionBinding

data class DashboardAction(
    val id: String,
    val title: String,
    val description: String,
    val iconResId: Int,
    val isPrimary: Boolean = false
)

class DashboardAdapter(
    private val actions: List<DashboardAction>,
    private val onActionClick: (DashboardAction) -> Unit
) : RecyclerView.Adapter<DashboardAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemDashboardActionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDashboardActionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = actions[position]
        val context = holder.itemView.context
        
        holder.binding.tvActionTitle.text = action.title
        holder.binding.tvActionDesc.text = action.description
        holder.binding.ivActionIcon.setImageResource(action.iconResId)
        
        if (action.isPrimary) {
            val primaryContainer = com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer)
            val colorOnPrimaryContainer = com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimaryContainer)
            
            holder.binding.cardAction.setCardBackgroundColor(primaryContainer)
            holder.binding.tvActionTitle.setTextColor(colorOnPrimaryContainer)
            holder.binding.tvActionDesc.setTextColor(colorOnPrimaryContainer)
            holder.binding.ivActionIcon.imageTintList = android.content.res.ColorStateList.valueOf(colorOnPrimaryContainer)
            holder.binding.ivArrow.imageTintList = android.content.res.ColorStateList.valueOf(colorOnPrimaryContainer)
        } else {
            val colorSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSurfaceVariant)
            val colorOnSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val colorOnSurface = com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
            val colorPrimary = com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)
            
            holder.binding.cardAction.setCardBackgroundColor(colorSurfaceVariant)
            holder.binding.tvActionTitle.setTextColor(colorOnSurface)
            holder.binding.tvActionDesc.setTextColor(colorOnSurfaceVariant)
            holder.binding.ivActionIcon.imageTintList = android.content.res.ColorStateList.valueOf(colorPrimary)
            holder.binding.ivArrow.imageTintList = android.content.res.ColorStateList.valueOf(colorOnSurfaceVariant)
        }
        
        holder.binding.root.setOnClickListener { onActionClick(action) }
    }

    override fun getItemCount() = actions.size
}
