package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.MediaTrack
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.viewmodel.ExportSettings
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import java.util.LinkedHashSet
import kotlin.math.roundToInt

/** Builds and presents the single-surface export modal. */
class ExportOptionsDialogPresenter(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val onExport: (ExportSettings) -> Unit
) {

    fun show(state: VideoEditingUiState.Success, initialRotation: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_media, null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        val closeButton = dialogView.findViewById<View>(R.id.closeExport)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.cancelExport)
        val exportButton = dialogView.findViewById<MaterialButton>(R.id.confirmExport)
        val summary = dialogView.findViewById<TextView>(R.id.exportSummary)
        val footerSummary = dialogView.findViewById<TextView>(R.id.exportFooterSummary)
        val combinedCard = dialogView.findViewById<MaterialCardView>(R.id.combinedOutputCard)
        val separateCard = dialogView.findViewById<MaterialCardView>(R.id.separateOutputCard)
        val combinedRadio = dialogView.findViewById<RadioButton>(R.id.combinedOutputRadio)
        val separateRadio = dialogView.findViewById<RadioButton>(R.id.separateOutputRadio)
        val rotationSection = dialogView.findViewById<View>(R.id.rotationSection)
        val rotationSpinner = dialogView.findViewById<Spinner>(R.id.rotationSpinner)
        val deleteOriginalCard = dialogView.findViewById<View>(R.id.deleteOriginalCard)
        val deleteOriginal = dialogView.findViewById<CheckBox>(R.id.deleteOriginalAfterExport)
        val tracksContainer = dialogView.findViewById<LinearLayout>(R.id.tracksContainer)

        deleteOriginalCard?.setOnClickListener { deleteOriginal.toggle() }

        summary.text = context.getString(R.string.export_media_summary, state.clips.size, keepRangeCount(state))
        rotationSection.visibility = if (state.isAudioOnly) View.GONE else View.VISIBLE
        rotationSpinner.setSelection(rotationSelection(initialRotation))

        val availableTracks = state.availableTracks.sortedWith(
            compareBy({ !it.isVideo }, { !it.isAudio }, { it.id })
        )
        val selectedTracks = setupTrackRows(tracksContainer, state, availableTracks)
        dialogView.findViewById<View>(R.id.tracksSection).visibility =
            if (availableTracks.isEmpty()) View.GONE else View.VISIBLE

        var mergeSegments = true

        fun refreshSummary() {
            updateSummary(state, mergeSegments, selectedTracks, footerSummary, exportButton)
        }

        fun selectOutput(combined: Boolean) {
            mergeSegments = combined
            setOutputMode(combined, combinedRadio, separateRadio, combinedCard, separateCard)
            refreshSummary()
        }

        combinedCard.setOnClickListener { selectOutput(true) }
        combinedRadio.setOnClickListener { selectOutput(true) }
        separateCard.setOnClickListener { selectOutput(false) }
        separateRadio.setOnClickListener { selectOutput(false) }

        setupTrackListeners(tracksContainer, selectedTracks) {
            refreshSummary()
        }

        closeButton.setOnClickListener { dialog.dismiss() }
        cancelButton.setOnClickListener { dialog.dismiss() }
        exportButton.setOnClickListener {
            if (availableTracks.isNotEmpty() && selectedTracks.isEmpty()) return@setOnClickListener
            dialog.dismiss()
            onExport(
                createExportSettings(
                    state,
                    mergeSegments,
                    selectedTracks,
                    rotationSpinner,
                    deleteOriginal.isChecked
                )
            )
        }

        setOutputMode(mergeSegments, combinedRadio, separateRadio, combinedCard, separateCard)
        refreshSummary()
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun setupTrackRows(
        tracksContainer: LinearLayout,
        state: VideoEditingUiState.Success,
        availableTracks: List<MediaTrack>
    ): LinkedHashSet<Int> {
        val selectedTracks = LinkedHashSet<Int>().apply { addAll(availableTracks.map { it.id }) }
        availableTracks.forEach { track -> tracksContainer.addView(createTrackRow(track, state)) }
        return selectedTracks
    }

    private fun setupTrackListeners(
        tracksContainer: LinearLayout,
        selectedTracks: MutableSet<Int>,
        onChanged: () -> Unit
    ) {
        selectedTracks.toList().forEach { trackId ->
            tracksContainer.findViewWithTag<MaterialCheckBox>(trackId)?.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedTracks.add(trackId) else selectedTracks.remove(trackId)
                onChanged()
            }
        }
    }

    private fun setOutputMode(
        mergeSegments: Boolean,
        combinedRadio: RadioButton,
        separateRadio: RadioButton,
        combinedCard: MaterialCardView,
        separateCard: MaterialCardView
    ) {
        combinedRadio.isChecked = mergeSegments
        separateRadio.isChecked = !mergeSegments
        combinedCard.strokeWidth = if (mergeSegments) dp(2) else 0
        separateCard.strokeWidth = if (mergeSegments) 0 else dp(2)
    }

    private fun updateSummary(
        state: VideoEditingUiState.Success,
        mergeSegments: Boolean,
        selectedTracks: Set<Int>,
        footerSummary: TextView,
        exportButton: MaterialButton
    ) {
        val availableTracks = state.availableTracks
        val tracksById = availableTracks.associateBy { it.id }
        val keepVideo = if (availableTracks.isEmpty()) {
            !state.isAudioOnly
        } else {
            selectedTracks.any { tracksById[it]?.isVideo == true }
        }
        val fileCount = if (mergeSegments) 1 else {
            state.clips.getOrNull(state.selectedClipIndex)
                ?.segments
                ?.count { it.action == SegmentAction.KEEP }
                ?: 0
        }
        footerSummary.text = context.getString(
            R.string.export_summary,
            if (keepVideo) context.getString(R.string.export_format_mp4)
            else context.getString(R.string.export_format_m4a),
            fileCount
        )
        exportButton.isEnabled = availableTracks.isEmpty() || selectedTracks.isNotEmpty()
    }

    private fun createExportSettings(
        state: VideoEditingUiState.Success,
        mergeSegments: Boolean,
        selectedTracks: Set<Int>,
        rotationSpinner: Spinner,
        deleteOriginalAfterExport: Boolean
    ): ExportSettings {
        val availableTracks = state.availableTracks
        val tracksById = availableTracks.associateBy { it.id }
        val selectedTrackIds = availableTracks.takeIf { it.isNotEmpty() }?.let { selectedTracks.toList() }
        val keepVideo = selectedTrackIds?.any { tracksById[it]?.isVideo == true } ?: !state.isAudioOnly
        val keepAudio = selectedTrackIds?.any { tracksById[it]?.isAudio == true } ?: state.hasAudioTrack
        val rotationOverride = when (rotationSpinner.selectedItemPosition) {
            1 -> 0
            2 -> 90
            3 -> 180
            4 -> 270
            else -> null
        }
        return ExportSettings(
            keepAudio = keepAudio,
            keepVideo = keepVideo,
            rotationOverride = rotationOverride,
            mergeSegments = mergeSegments,
            selectedTracks = selectedTrackIds,
            deleteOriginalAfterExport = deleteOriginalAfterExport
        )
    }

    private fun keepRangeCount(state: VideoEditingUiState.Success): Int =
        state.clips.sumOf { clip -> clip.segments.count { it.action == SegmentAction.KEEP } }

    private fun createTrackRow(
        track: MediaTrack,
        state: VideoEditingUiState.Success
    ): View {
        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            radius = dp(10).toFloat()
            setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    Color.TRANSPARENT
                )
            )
            isClickable = true
            isFocusable = true
            contentDescription = trackDescription(track, state)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(10), dp(2))
        }
        val checkbox = MaterialCheckBox(context).apply {
            tag = track.id
            isChecked = true
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            contentDescription = trackDescription(track, state)
        }
        val details = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(8)
            }
            setImageResource(trackIcon(track))
            imageTintList = context.getColorStateList(R.color.colorAccent)
            contentDescription = null
        }
        val title = TextView(context).apply {
            text = trackTitle(track, state)
            setTextColor(context.getColorStateList(com.tazztone.losslesscut.R.color.colorOnSurface))
            textSize = 14f
        }
        val subtitle = TextView(context).apply {
            text = trackDescription(track, state, includeType = false)
            setTextColor(context.getColorStateList(com.tazztone.losslesscut.R.color.colorOnSurfaceVariant))
            textSize = 12f
        }
        details.addView(title)
        details.addView(subtitle)
        row.addView(checkbox)
        row.addView(icon)
        row.addView(details)
        card.addView(row)
        card.setOnClickListener { checkbox.toggle() }
        return card
    }

    private fun trackIcon(track: MediaTrack): Int = when {
        track.isAudio -> R.drawable.ic_audio_24
        track.isVideo -> R.drawable.ic_camera_24
        else -> R.drawable.ic_metadata_24
    }

    private fun trackTitle(track: MediaTrack, state: VideoEditingUiState.Success): String {
        track.title?.takeIf { it.isNotBlank() }?.let { return it }
        return when {
            track.isVideo -> context.getString(R.string.track_video)
            track.isAudio -> {
                val audioTracks = state.availableTracks.filter { it.isAudio }
                if (audioTracks.size > 1) {
                    val index = audioTracks.indexOfFirst { it.id == track.id } + 1
                    context.getString(R.string.track_audio_numbered, index)
                } else {
                    context.getString(R.string.track_audio)
                }
            }
            else -> context.getString(R.string.track_other)
        }
    }

    private fun trackDescription(
        track: MediaTrack,
        state: VideoEditingUiState.Success,
        includeType: Boolean = true
    ): String {
        val selectedClip = state.clips.getOrNull(state.selectedClipIndex)
        val values = mutableListOf<String>()
        if (includeType) values += trackTitle(track, state)
        values += codecName(track.mimeType)
        if (track.isVideo) {
            selectedClip?.takeIf { it.width > 0 && it.height > 0 }?.let {
                values += "${it.width}×${it.height}"
            }
        }
        if (track.isAudio) {
            when (track.channelCount) {
                1 -> values += context.getString(R.string.track_audio_mono)
                2 -> values += context.getString(R.string.track_audio_stereo)
                in 3..Int.MAX_VALUE -> values += context.getString(
                    R.string.track_audio_channels,
                    track.channelCount
                )
            }
            if (track.sampleRate > 0) {
                values += formatSampleRate(track.sampleRate)
            }
        }
        track.language?.takeIf { it.isNotBlank() }?.let { values += it }
        return values.joinToString(" • ")
    }

    private fun formatSampleRate(sampleRate: Int): String {
        return if (sampleRate % 1000 == 0) {
            "${sampleRate / 1000} kHz"
        } else {
            val khz = sampleRate / 1000.0
            String.format(java.util.Locale.US, "%.1f kHz", khz)
        }
    }

    private fun codecName(mimeType: String): String = when {
        mimeType.contains("avc", ignoreCase = true) -> "H.264"
        mimeType.contains("hevc", ignoreCase = true) -> "H.265"
        mimeType.contains("mp4a", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "AAC"
        else -> mimeType.substringAfter('/', mimeType).uppercase()
    }

    private fun rotationSelection(rotation: Int): Int = when (((rotation % 360) + 360) % 360) {
        90 -> 2
        180 -> 3
        270 -> 4
        else -> 0
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
