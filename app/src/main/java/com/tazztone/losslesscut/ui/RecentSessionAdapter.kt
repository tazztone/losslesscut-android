package com.tazztone.losslesscut.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.ItemRecentSessionBinding
import com.tazztone.losslesscut.domain.model.SessionSummary

class RecentSessionAdapter(
    private val onResume: (SessionSummary) -> Unit,
    private val onRemove: (SessionSummary) -> Unit
) : RecyclerView.Adapter<RecentSessionAdapter.ViewHolder>() {

    private var sessions: List<SessionSummary> = emptyList()

    fun submitList(newSessions: List<SessionSummary>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemRecentSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        with(holder.binding) {
            tvSessionName.text = session.fileName
            tvSessionMeta.text = holder.itemView.context.resources.getQuantityString(
                R.plurals.recent_session_meta,
                session.clipCount,
                session.clipCount,
                DateUtils.getRelativeTimeSpanString(
                    session.updatedAtEpochMs,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            )
            cardSession.contentDescription = holder.itemView.context.getString(
                R.string.resume_session_description,
                session.fileName
            )
            cardSession.setOnClickListener { onResume(session) }
            btnRemove.setOnClickListener { onRemove(session) }
        }
    }

    override fun getItemCount(): Int = sessions.size

    class ViewHolder(val binding: ItemRecentSessionBinding) : RecyclerView.ViewHolder(binding.root)
}
