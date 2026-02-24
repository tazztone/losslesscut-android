package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.View
import android.widget.TextView
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.FragmentEditorBinding
import com.tazztone.losslesscut.domain.model.TimeUtils
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Encapsulates the silence detection overlay view wiring and preview logic.
 */
class SilenceDetectionOverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val binding: FragmentEditorBinding,
    private val viewModel: VideoEditingViewModel
) {
    private var silencePreviewJob: Job? = null

    companion object {
        private const val PERCENT_SCALE = 100
        private const val MS_TO_SEC = 1000f
    }

    fun show() {
        val overlay = binding.silenceDetectionContainer?.root ?: return
        overlay.visibility = View.VISIBLE
        
        val sliderThreshold = overlay.findViewById<Slider>(R.id.sliderThreshold)
        val sliderDuration = overlay.findViewById<Slider>(R.id.sliderDuration)
        val sliderMinSegment = overlay.findViewById<Slider>(R.id.sliderMinSegment)
        val sliderPadding = overlay.findViewById<Slider>(R.id.sliderPadding)
        
        val tvThresholdValue = overlay.findViewById<TextView>(R.id.tvThresholdValue)
        val tvDurationValue = overlay.findViewById<TextView>(R.id.tvDurationValue)
        val tvMinSegmentValue = overlay.findViewById<TextView>(R.id.tvMinSegmentValue)
        val tvPaddingValue = overlay.findViewById<TextView>(R.id.tvPaddingValue)
        
        val tvEstimatedCut = overlay.findViewById<TextView>(R.id.tvEstimatedCut)
        val btnCancel = overlay.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnApply = overlay.findViewById<android.widget.Button>(R.id.btnApply)

        val updatePreview = {
            val threshold = sliderThreshold.value
            val duration = sliderDuration.value.toLong()
            val minSegment = sliderMinSegment.value.toLong()
            val padding = sliderPadding.value.toLong()
            
            tvThresholdValue.text = String.format(Locale.getDefault(), "%.1f%%", threshold * PERCENT_SCALE)
            tvDurationValue.text = String.format(Locale.getDefault(), "%.1fs", duration / MS_TO_SEC)
            tvMinSegmentValue.text = String.format(Locale.getDefault(), "%.1fs", minSegment / MS_TO_SEC)
            tvPaddingValue.text = String.format(Locale.getDefault(), "%.1fs", padding / MS_TO_SEC)
            
            viewModel.previewSilenceSegments(threshold, duration, padding, minSegment)
        }

        sliderThreshold.addOnChangeListener { _, _, _ -> updatePreview() }
        sliderDuration.addOnChangeListener { _, _, _ -> updatePreview() }
        sliderMinSegment.addOnChangeListener { _, _, _ -> updatePreview() }
        sliderPadding.addOnChangeListener { _, _, _ -> updatePreview() }

        silencePreviewJob?.cancel()
        silencePreviewJob = scope.launch {
            viewModel.uiState.collect { state ->
                handleUiStateUpdate(state, tvEstimatedCut, btnApply)
            }
        }

        btnCancel.setOnClickListener { hide() }
        btnApply.setOnClickListener {
            viewModel.applySilenceDetection()
            hide()
        }

        updatePreview()
    }

    private fun handleUiStateUpdate(state: VideoEditingUiState, tvEst: TextView, btnApply: View) {
        if (state is VideoEditingUiState.Success) {
            val ranges = state.silencePreviewRanges
            if (ranges.isNotEmpty()) {
                val totalSilenceMs = ranges.sumOf { it.last - it.first }
                tvEst.text = context.getString(
                    R.string.silence_detected_preview,
                    TimeUtils.formatDuration(totalSilenceMs),
                    ranges.size
                )
                btnApply.isEnabled = true
            } else {
                tvEst.text = context.getString(R.string.no_silence_detected)
                btnApply.isEnabled = false
            }
        }
    }

    fun hide() {
        silencePreviewJob?.cancel()
        viewModel.clearSilencePreview()
        binding.silenceDetectionContainer?.root?.visibility = View.GONE
    }
}
