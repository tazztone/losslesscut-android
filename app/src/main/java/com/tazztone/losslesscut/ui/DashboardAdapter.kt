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

    class ViewHolder(val binding: ItemDashboardActionBinding) : RecyclerView.ViewHolder(binding.root) {
        val primaryContainer: Int = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorPrimaryContainer
        )
        val colorOnPrimaryContainer: Int = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnPrimaryContainer
        )
        val colorSurfaceVariant: Int = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorSurfaceVariant
        )
        val colorOnSurfaceVariant: Int = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val colorOnSurface: Int = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnSurface
        )
        val colorPrimary: Int = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorPrimary
        )

        val colorStateListOnPrimaryContainer = android.content.res.ColorStateList.valueOf(colorOnPrimaryContainer)
        val colorStateListPrimary = android.content.res.ColorStateList.valueOf(colorPrimary)
        val colorStateListOnSurfaceVariant = android.content.res.ColorStateList.valueOf(colorOnSurfaceVariant)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDashboardActionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = actions[position]
        
        holder.binding.tvActionTitle.text = action.title
        holder.binding.tvActionDesc.text = action.description
        holder.binding.ivActionIcon.setImageResource(action.iconResId)
        
        if (action.isPrimary) {
            holder.binding.cardAction.setCardBackgroundColor(holder.primaryContainer)
            holder.binding.tvActionTitle.setTextColor(holder.colorOnPrimaryContainer)
            holder.binding.tvActionDesc.setTextColor(holder.colorOnPrimaryContainer)
            holder.binding.ivActionIcon.imageTintList = holder.colorStateListOnPrimaryContainer
            holder.binding.ivArrow.imageTintList = holder.colorStateListOnPrimaryContainer
        } else {
            holder.binding.cardAction.setCardBackgroundColor(holder.colorSurfaceVariant)
            holder.binding.tvActionTitle.setTextColor(holder.colorOnSurface)
            holder.binding.tvActionDesc.setTextColor(holder.colorOnSurfaceVariant)
            holder.binding.ivActionIcon.imageTintList = holder.colorStateListPrimary
            holder.binding.ivArrow.imageTintList = holder.colorStateListOnSurfaceVariant
        }
        
        holder.binding.root.setOnClickListener { onActionClick(action) }
    }

    override fun getItemCount() = actions.size
}
