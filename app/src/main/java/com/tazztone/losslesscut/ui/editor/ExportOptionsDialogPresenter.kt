package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Builds and presents the export options dialog.
 */
class ExportOptionsDialogPresenter(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val onExport: (
        keepAudio: Boolean,
        keepVideo: Boolean,
        mergeSegments: Boolean,
        selectedTracks: List<Int>?
    ) -> Unit
) {

    fun show(state: VideoEditingUiState.Success) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val cbMergeSegments = dialogView.findViewById<CheckBox>(R.id.cbMergeSegments)
        
        setupMergeVisibility(cbMergeSegments, state)
        val selectedTracks = setupTrackList(dialogView, state)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.export_options))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.export)) { _, _ ->
                handleExportClick(cbMergeSegments, state, selectedTracks)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun setupMergeVisibility(cbMerge: CheckBox, state: VideoEditingUiState.Success) {
        val totalKeepSegments = state.clips.sumOf { clip -> 
            clip.segments.count { it.action == SegmentAction.KEEP } 
        }
        if (totalKeepSegments > 1 || state.clips.size > 1) {
            cbMerge.visibility = View.VISIBLE
        }
    }

    private fun setupTrackList(dialogView: View, state: VideoEditingUiState.Success): MutableSet<Int> {
        val tvTracksHeader = dialogView.findViewById<TextView>(R.id.tvTracksHeader)
        val tracksContainer = dialogView.findViewById<LinearLayout>(R.id.tracksContainer)
        
        // Sort: Video first, then Audio, then others
        val availableTracks = state.availableTracks.sortedWith(compareBy({ !it.isVideo }, { !it.isAudio }, { it.id }))
        val selectedTracks = mutableSetOf<Int>()
        
        if (availableTracks.isEmpty()) {
            tvTracksHeader.visibility = View.GONE
            tracksContainer.visibility = View.GONE
            return selectedTracks
        }
        
        tvTracksHeader.visibility = View.VISIBLE
        tracksContainer.visibility = View.VISIBLE
        
        availableTracks.forEach { track ->
            val emoji = when {
                track.isVideo -> "🎬"
                track.isAudio -> "🎵"
                else -> "📄"
            }
            val type = if (track.isVideo) "Video" else if (track.isAudio) "Audio" else "Other"
            val typeWithEmoji = "$emoji $type"
            
            val langInfo = if (!track.language.isNullOrBlank()) " — ${track.language}" else ""
            val titleInfo = if (!track.title.isNullOrBlank()) " (${track.title})" else ""
            
            val cb = CheckBox(context).apply {
                text = context.getString(R.string.track_item_format, track.id, typeWithEmoji, titleInfo, langInfo, track.mimeType)
                isChecked = true
                selectedTracks.add(track.id)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedTracks.add(track.id) else selectedTracks.remove(track.id)
                }
            }
            tracksContainer.addView(cb)
        }
        return selectedTracks
    }

    private fun handleExportClick(
        cbMerge: CheckBox,
        state: VideoEditingUiState.Success,
        selectedTracks: Set<Int>
    ) {
        if (selectedTracks.isEmpty() && state.availableTracks.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.select_track_export), Toast.LENGTH_SHORT).show()
            return
        }

        val trackList = if (selectedTracks.isNotEmpty()) selectedTracks.toList() else null
        
        // Derive keepVideo/keepAudio for file extension logic in ExportUseCase
        val keepVideo = if (trackList != null) {
            trackList.any { id -> state.availableTracks.find { it.id == id }?.isVideo == true }
        } else {
            true // default to true if no track info (safety)
        }
        
        val keepAudio = if (trackList != null) {
            trackList.any { id -> state.availableTracks.find { it.id == id }?.isAudio == true }
        } else {
            state.hasAudioTrack // default to existing
        }

        val mergeSegments = cbMerge.isChecked
        onExport(keepAudio, keepVideo, mergeSegments, trackList)
    }
}
