package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.databinding.FragmentEditorBinding
import com.tazztone.losslesscut.domain.model.TimeUtils
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
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
    private val viewModel: VideoEditingViewModel,
    private val onDismiss: () -> Unit
) {
    private var silencePreviewJob: Job? = null
    private var isPaddingLinked = true

    private var sliderThreshold: Slider? = null
    private var sliderDuration: Slider? = null
    private var sliderMinSegment: Slider? = null
    private var sliderPaddingPrefix: Slider? = null
    private var sliderPaddingPostfix: Slider? = null

    private var tvThresholdValue: TextView? = null
    private var tvDurationValue: TextView? = null
    private var tvMinSegmentValue: TextView? = null
    private var tvPaddingPrefixValue: TextView? = null
    private var tvPaddingPostfixValue: TextView? = null

    companion object {
        private const val PERCENT_SCALE = 100
        private const val MS_TO_SEC = 1000f
    }

    fun show() {
        showInsideSmartCut()
    }

    internal fun showInsideSmartCut() {
        val overlay = binding.smartCutOverlay?.root ?: return

        
        initializeViews(overlay)
        setupListeners(overlay)
        observeState(overlay)
        
        binding.customVideoSeeker.segmentsVisible = false
        binding.customVideoSeeker.playheadVisible = false
        updatePreview()
    }

    private fun initializeViews(overlay: View) {
        sliderThreshold = overlay.findViewById(R.id.sliderThreshold)
        sliderDuration = overlay.findViewById(R.id.sliderDuration)
        sliderMinSegment = overlay.findViewById(R.id.sliderMinSegment)
        sliderPaddingPrefix = overlay.findViewById(R.id.sliderPaddingPrefix)
        sliderPaddingPostfix = overlay.findViewById(R.id.sliderPaddingPostfix)
        
        tvThresholdValue = overlay.findViewById(R.id.tvThresholdValue)
        tvDurationValue = overlay.findViewById(R.id.tvDurationValue)
        tvMinSegmentValue = overlay.findViewById(R.id.tvMinSegmentValue)
        tvPaddingPrefixValue = overlay.findViewById(R.id.tvPaddingPrefixValue)
        tvPaddingPostfixValue = overlay.findViewById(R.id.tvPaddingPostfixValue)
    }

    private fun setupListeners(overlay: View) {
        val btnLinkPadding = overlay.findViewById<ImageButton>(R.id.btnLinkPadding)
        val btnCancel = overlay.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnApply = overlay.findViewById<android.widget.Button>(R.id.btnApply)

        val updateLinkIcon = {
            btnLinkPadding.setImageResource(
                if (isPaddingLinked) R.drawable.ic_link_24 else R.drawable.ic_link_off_24
            )
        }

        btnLinkPadding.setOnClickListener {
            isPaddingLinked = !isPaddingLinked
            updateLinkIcon()
            if (isPaddingLinked) {
                sliderPaddingPostfix?.value = sliderPaddingPrefix?.value ?: 0f
                updatePreview()
            }
        }

        sliderThreshold?.addOnChangeListener { _, value, _ ->
            val maxAmp = viewModel.waveformMaxAmplitude.value
            binding.customVideoSeeker.noiseThresholdPreview = if (maxAmp > 0f) value / maxAmp else value
            updatePreview()
        }

        sliderThreshold?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                val maxAmp = viewModel.waveformMaxAmplitude.value
                val scaledThreshold = if (maxAmp > 0f) slider.value / maxAmp else slider.value
                binding.customVideoSeeker.noiseThresholdPreview = scaledThreshold
            }

            override fun onStopTrackingTouch(slider: Slider) {
                binding.customVideoSeeker.noiseThresholdPreview = null
            }
        })

        sliderDuration?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                binding.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.MIN_SILENCE
            }
            override fun onStopTrackingTouch(slider: Slider) {
                binding.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.NONE
            }
        })

        sliderMinSegment?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                binding.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.MIN_SEGMENT
            }
            override fun onStopTrackingTouch(slider: Slider) {
                binding.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.NONE
            }
        })

        val paddingTouchListener = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                binding.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.PADDING
            }
            override fun onStopTrackingTouch(slider: Slider) {
                binding.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.NONE
            }
        }
        sliderPaddingPrefix?.addOnSliderTouchListener(paddingTouchListener)
        sliderPaddingPostfix?.addOnSliderTouchListener(paddingTouchListener)
        sliderDuration?.addOnChangeListener { _, _, _ -> updatePreview() }
        sliderMinSegment?.addOnChangeListener { _, _, _ -> updatePreview() }
        
        sliderPaddingPrefix?.addOnChangeListener { _, value, fromUser -> 
            if (fromUser && isPaddingLinked) sliderPaddingPostfix?.value = value
            updatePreview() 
        }
        
        sliderPaddingPostfix?.addOnChangeListener { _, value, fromUser -> 
            if (fromUser && isPaddingLinked) sliderPaddingPrefix?.value = value
            updatePreview() 
        }

        btnCancel.setOnClickListener { onDismiss() }
        btnApply.setOnClickListener {
            val minKeep = sliderMinSegment?.value?.toLong() ?: 10L
            viewModel.applyDetection(SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES, minKeep)
            onDismiss()
        }
        updateLinkIcon()
    }

    private fun updatePreview() {
        val threshold = sliderThreshold?.value ?: 0f
        val duration = sliderDuration?.value?.toLong() ?: 0L
        val minSegment = sliderMinSegment?.value?.toLong() ?: 0L
        val paddingPrefix = sliderPaddingPrefix?.value?.toLong() ?: 0L
        val paddingPostfix = sliderPaddingPostfix?.value?.toLong() ?: 0L
        
        tvThresholdValue?.text = String.format(Locale.getDefault(), "%.1f%%", threshold * PERCENT_SCALE)
        tvDurationValue?.text = String.format(Locale.getDefault(), "%.1fs", duration / MS_TO_SEC)
        tvMinSegmentValue?.text = String.format(Locale.getDefault(), "%.1fs", minSegment / MS_TO_SEC)
        tvPaddingPrefixValue?.text = String.format(Locale.getDefault(), "%.1fs", paddingPrefix / MS_TO_SEC)
        tvPaddingPostfixValue?.text = String.format(Locale.getDefault(), "%.1fs", paddingPostfix / MS_TO_SEC)
        
        viewModel.previewSilenceSegments(threshold, duration, paddingPrefix, paddingPostfix, minSegment)
    }

    private fun observeState(overlay: View) {
        val tvEstimatedCut = overlay.findViewById<TextView>(R.id.tvEstimatedCut)
        val btnApply = overlay.findViewById<android.widget.Button>(R.id.btnApply)

        silencePreviewJob?.cancel()
        silencePreviewJob = scope.launch {
            viewModel.uiState.collect { state ->
                handleUiStateUpdate(state, tvEstimatedCut, btnApply)
            }
        }
        scope.launch {
            viewModel.rawSilencePreviewRanges.collect { result ->
                binding.customVideoSeeker.rawSilenceResult = result
            }
        }
    }

    private fun handleUiStateUpdate(state: VideoEditingUiState, tvEst: TextView, btnApply: View) {
        if (state is VideoEditingUiState.Success) {
            val ranges = state.detectionPreviewRanges
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
        hideInsideSmartCut()
    }

    internal fun hideInsideSmartCut() {
        silencePreviewJob?.cancel()
        // Clear preview when hiding inside smart cut since detectionPreviewRanges is shared
        viewModel.clearSilencePreview()

        binding.customVideoSeeker.noiseThresholdPreview = null
    }
}
