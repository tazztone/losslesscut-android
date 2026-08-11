package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState

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
        selectedTracks: List<Int>?,
        deleteOriginalAfterExport: Boolean
    ) -> Unit,
    private val onRepackage: (VideoEditingUiState.Success) -> Unit,
    private val onEditRotation: (VideoEditingUiState.Success, Int?) -> Unit
) {

    fun show(state: VideoEditingUiState.Success) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_actions, null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.export_actions_title))
            .setView(dialogView)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnExportEdited).setOnClickListener {
            dialog.dismiss()
            onExport(state.hasAudioTrack, !state.isAudioOnly, true, null, false)
        }
        dialogView.findViewById<MaterialButton>(R.id.btnRepackage).setOnClickListener {
            dialog.dismiss()
            onRepackage(state)
        }
        dialogView.findViewById<MaterialButton>(R.id.btnEditRotation).setOnClickListener {
            dialog.dismiss()
            showRotationOptions(state)
        }
        dialogView.findViewById<MaterialButton>(R.id.btnAdvancedExport).setOnClickListener {
            dialog.dismiss()
            showAdvancedOptions(state)
        }
        dialog.show()
    }

    private fun showAdvancedOptions(state: VideoEditingUiState.Success) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val cbExportIndividualClips = dialogView.findViewById<CheckBox>(R.id.cbExportIndividualClips)
        val cbDeleteOriginalAfterExport = dialogView.findViewById<CheckBox>(R.id.cbDeleteOriginalAfterExport)
        
        setupMergeVisibility(cbExportIndividualClips, state)
        val selectedTracks = setupTrackList(dialogView, state)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.export_options))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.export)) { _, _ ->
                handleExportClick(cbExportIndividualClips, cbDeleteOriginalAfterExport, state, selectedTracks)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun showRotationOptions(state: VideoEditingUiState.Success) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_metadata_editor, null)
        val spinnerRotation = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerRotation)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.edit_rotation_metadata))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.apply)) { _, _ ->
                val rotation = when (spinnerRotation.selectedItemPosition) {
                    1 -> 0
                    2 -> 90
                    3 -> 180
                    4 -> 270
                    else -> null
                }
                onEditRotation(state, rotation)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun setupMergeVisibility(cbExportIndividualClips: CheckBox, state: VideoEditingUiState.Success) {
        val totalKeepSegments = state.clips.sumOf { clip -> 
            clip.segments.count { it.action == SegmentAction.KEEP } 
        }
        if (totalKeepSegments > 1 || state.clips.size > 1) {
            cbExportIndividualClips.visibility = View.VISIBLE
        }
    }

    private fun setupTrackList(dialogView: View, state: VideoEditingUiState.Success): MutableSet<Int> {
        val tvTracksHeader = dialogView.findViewById<TextView>(R.id.tvTracksHeader)
        val tracksContainer = dialogView.findViewById<LinearLayout>(R.id.tracksContainer)
        
        // Ordered tracks: Video, Audio, then others
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
        cbExportIndividualClips: CheckBox,
        cbDeleteOriginalAfterExport: CheckBox,
        state: VideoEditingUiState.Success,
        selectedTracks: Set<Int>
    ) {
        if (selectedTracks.isEmpty() && state.availableTracks.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.select_track_export), Toast.LENGTH_SHORT).show()
            return
        }

        val trackList = if (selectedTracks.isNotEmpty()) selectedTracks.toList() else null
        
        // Determine which track types to keep for file extension logic
        val availableTracksById = if (trackList != null) state.availableTracks.associateBy { it.id } else null

        val keepVideo = if (trackList != null && availableTracksById != null) {
            trackList.any { id -> availableTracksById[id]?.isVideo == true }
        } else {
            true // Fallback when track info is missing (safety)
        }
        
        val keepAudio = if (trackList != null && availableTracksById != null) {
            trackList.any { id -> availableTracksById[id]?.isAudio == true }
        } else {
            state.hasAudioTrack // Fallback to current audio state
        }

        val mergeSegments = !cbExportIndividualClips.isChecked
        val deleteOriginal = cbDeleteOriginalAfterExport.isChecked
        onExport(keepAudio, keepVideo, mergeSegments, trackList, deleteOriginal)
    }
}
