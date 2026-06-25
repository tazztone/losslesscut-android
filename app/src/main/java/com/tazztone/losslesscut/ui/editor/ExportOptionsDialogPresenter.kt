package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private const val EXPORT_TYPE_VIDEO = "video"
private const val EXPORT_TYPE_LLC = "llc"
private const val DENSITY_MULTIPLIER_12 = 12
private const val DENSITY_MULTIPLIER_8 = 8

/**
 * Builds and presents the export options dialog.
 */
class ExportOptionsDialogPresenter(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val onExport: (
        exportType: String,
        keepAudio: Boolean,
        keepVideo: Boolean,
        mergeSegments: Boolean,
        selectedTracks: List<Int>?
    ) -> Unit
) {

    fun show(state: VideoEditingUiState.Success) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val cbMergeSegments = dialogView.findViewById<CheckBox>(R.id.cbMergeSegments)

        val radioGroup = buildExportTypeSelector(dialogView)
        setupMergeVisibility(cbMergeSegments, state)
        val (selectedTracks, tvTracksHeader, tracksContainer) = setupTrackList(dialogView, state)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isLlcMode = checkedId == radioGroup.getChildAt(1).id
            cbMergeSegments.visibility = if (isLlcMode) View.GONE else View.VISIBLE
            tvTracksHeader.visibility = if (isLlcMode) View.GONE else View.VISIBLE
            tracksContainer.visibility = if (isLlcMode) View.GONE else View.VISIBLE
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.export_options))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.export)) { _, _ ->
                val checkedId = radioGroup.checkedRadioButtonId
                val isLlcMode = checkedId == radioGroup.getChildAt(1).id
                if (isLlcMode) {
                    onExport(EXPORT_TYPE_LLC, true, true, false, null)
                } else {
                    handleExportClick(cbMergeSegments, state, selectedTracks)
                }
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun buildExportTypeSelector(dialogView: View): RadioGroup {
        val rootLayout = dialogView as? LinearLayout
            ?: LinearLayout(context).also {
                it.orientation = LinearLayout.VERTICAL
            }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bottomPadding =
                (context.resources.displayMetrics.density * DENSITY_MULTIPLIER_12).toInt()
            setPadding(0, 0, 0, bottomPadding)
        }

        val header = TextView(context).apply {
            text = "导出方式"
            textSize = 16f
            val bottomPadding =
                (context.resources.displayMetrics.density * DENSITY_MULTIPLIER_8).toInt()
            setPadding(0, 0, 0, bottomPadding)
        }

        val radioGroup = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
        }

        val rbVideo = RadioButton(context).apply {
            text = "导出视频"
            isChecked = true
        }

        val rbLlc = RadioButton(context).apply {
            text = "导出 .llc 分段文件"
        }

        radioGroup.addView(rbVideo)
        radioGroup.addView(rbLlc)
        container.addView(header)
        container.addView(radioGroup)

        val existingChildren = (0 until rootLayout.childCount)
            .map { rootLayout.getChildAt(it) }
            .toList()
        rootLayout.removeAllViews()
        rootLayout.addView(container)
        existingChildren.forEach { rootLayout.addView(it) }

        return radioGroup
    }

    private fun setupMergeVisibility(cbMerge: CheckBox, state: VideoEditingUiState.Success) {
        val totalKeepSegments = state.clips.sumOf { clip ->
            clip.segments.count { it.action == SegmentAction.KEEP }
        }
        if (totalKeepSegments > 1 || state.clips.size > 1) {
            cbMerge.visibility = View.VISIBLE
        }
    }

    private fun setupTrackList(
        dialogView: View,
        state: VideoEditingUiState.Success
    ): Triple<MutableSet<Int>, TextView, LinearLayout> {
        val tvTracksHeader = dialogView.findViewById<TextView>(R.id.tvTracksHeader)
        val tracksContainer = dialogView.findViewById<LinearLayout>(R.id.tracksContainer)

        val availableTracks = state.availableTracks.sortedWith(
            compareBy({ !it.isVideo }, { !it.isAudio }, { it.id })
        )
        val selectedTracks = mutableSetOf<Int>()

        if (availableTracks.isEmpty()) {
            tvTracksHeader.visibility = View.GONE
            tracksContainer.visibility = View.GONE
            return Triple(selectedTracks, tvTracksHeader, tracksContainer)
        }

        tvTracksHeader.visibility = View.VISIBLE
        tracksContainer.visibility = View.VISIBLE

        availableTracks.forEach { track ->
            val emoji = when {
                track.isVideo -> "\uD83C\uDFAC"
                track.isAudio -> "\uD83C\uDFB5"
                else -> "\uD83D\uDCC4"
            }
            val type = if (track.isVideo) "Video" else if (track.isAudio) "Audio" else "Other"
            val typeWithEmoji = "$emoji $type"

            val langInfo = if (!track.language.isNullOrBlank()) " — ${track.language}" else ""
            val titleInfo = if (!track.title.isNullOrBlank()) " (${track.title})" else ""

            val cb = CheckBox(context).apply {
                text = context.getString(
                    R.string.track_item_format,
                    track.id, typeWithEmoji, titleInfo, langInfo, track.mimeType
                )
                isChecked = true
                selectedTracks.add(track.id)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedTracks.add(track.id) else selectedTracks.remove(track.id)
                }
            }
            tracksContainer.addView(cb)
        }
        return Triple(selectedTracks, tvTracksHeader, tracksContainer)
    }

    private fun handleExportClick(
        cbMerge: CheckBox,
        state: VideoEditingUiState.Success,
        selectedTracks: Set<Int>
    ) {
        if (selectedTracks.isEmpty() && state.availableTracks.isNotEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.select_track_export),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val trackList = if (selectedTracks.isNotEmpty()) selectedTracks.toList() else null

        val availableTracksById = if (trackList != null) {
            state.availableTracks.associateBy { it.id }
        } else {
            null
        }

        val keepVideo = if (trackList != null && availableTracksById != null) {
            trackList.any { id -> availableTracksById[id]?.isVideo == true }
        } else {
            true
        }

        val keepAudio = if (trackList != null && availableTracksById != null) {
            trackList.any { id -> availableTracksById[id]?.isAudio == true }
        } else {
            state.hasAudioTrack
        }

        val mergeSegments = cbMerge.isChecked
        onExport(EXPORT_TYPE_VIDEO, keepVideo, keepAudio, mergeSegments, trackList)
    }
}