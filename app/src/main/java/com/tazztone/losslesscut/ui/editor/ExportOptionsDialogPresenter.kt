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
    companion object {
        private const val MIN_TRACKS_FOR_SELECTION = 2
        private const val DISABLED_ALPHA = 0.5f
    }

    fun show(state: VideoEditingUiState.Success) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val cbKeepVideo = dialogView.findViewById<CheckBox>(R.id.cbKeepVideo)
        val cbKeepAudio = dialogView.findViewById<CheckBox>(R.id.cbKeepAudio)
        val cbMergeSegments = dialogView.findViewById<CheckBox>(R.id.cbMergeSegments)
        
        setupMergeVisibility(cbMergeSegments, state)
        val selectedTracks = setupTrackList(dialogView, state)

        if (!state.hasAudioTrack) {
            disableVideoExclusion(cbKeepVideo)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.export_options))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.export)) { _, _ ->
                handleExportClick(cbKeepVideo, cbKeepAudio, cbMergeSegments, state, selectedTracks)
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
        val availableTracks = state.availableTracks
        val selectedTracks = mutableSetOf<Int>()
        
        if (availableTracks.size <= MIN_TRACKS_FOR_SELECTION) return selectedTracks
        
        tvTracksHeader.visibility = View.VISIBLE
        tracksContainer.visibility = View.VISIBLE
        
        availableTracks.forEach { track ->
            val type = if (track.isVideo) "Video" else if (track.isAudio) "Audio" else "Other"
            val cb = CheckBox(context).apply {
                text = context.getString(R.string.track_item_format, track.id, type, track.mimeType)
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

    private fun disableVideoExclusion(cbKeepVideo: CheckBox) {
        cbKeepVideo.isEnabled = false
        cbKeepVideo.alpha = DISABLED_ALPHA
        cbKeepVideo.setOnClickListener {
            Toast.makeText(context, context.getString(R.string.error_cannot_exclude_video), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleExportClick(
        cbVideo: CheckBox,
        cbAudio: CheckBox,
        cbMerge: CheckBox,
        state: VideoEditingUiState.Success,
        selectedTracks: Set<Int>
    ) {
        val keepVideo = cbVideo.isChecked
        val keepAudio = cbAudio.isChecked
        val mergeSegments = cbMerge.isChecked
        
        if (!keepVideo && !keepAudio && selectedTracks.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.select_track_export), Toast.LENGTH_SHORT).show()
            return
        }

        val trackList = if (selectedTracks.isNotEmpty() && state.availableTracks.size > MIN_TRACKS_FOR_SELECTION) {
            selectedTracks.toList()
        } else null
        onExport(keepAudio, keepVideo, mergeSegments, trackList)
    }
}
