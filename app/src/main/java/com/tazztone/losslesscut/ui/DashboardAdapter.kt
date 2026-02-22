package com.tazztone.losslesscut.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tazztone.losslesscut.databinding.ItemDashboardActionBinding

data class DashboardAction(
    val id: String,
    val title: String,
    val description: String,
    val iconResId: Int
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
        holder.binding.tvActionTitle.text = action.title
        holder.binding.tvActionDesc.text = action.description
        holder.binding.ivActionIcon.setImageResource(action.iconResId)
        holder.binding.root.setOnClickListener { onActionClick(action) }
    }

    override fun getItemCount() = actions.size
}
