package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.databinding.FragmentEditorBinding
import com.tazztone.losslesscut.domain.model.TimeUtils
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.tazztone.losslesscut.util.setupAutoRepeat
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
    private var currentMode = SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES

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
        val overlay = binding.smartCutOverlay.root

        initializeViews(overlay)
        setupListeners(overlay)
        observeState(overlay)
        
        binding.seekerContainer.customVideoSeeker.segmentsVisible = false
        binding.seekerContainer.customVideoSeeker.playheadVisible = false
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
        val btnToggleModeSilence = overlay.findViewById<MaterialButtonToggleGroup>(R.id.btnToggleModeSilence)

        setupModeListener(overlay, btnToggleModeSilence)
        setupPaddingLinkListener(btnLinkPadding)
        setupSliderListeners()
        setupSliderStepButtons(overlay)
        setupActionListeners(btnCancel, btnApply)
    }

    private fun stepSlider(slider: Slider, direction: Int): Float {
        val step = if (slider.stepSize > 0f) slider.stepSize else 1f
        val rawValue = slider.value + (direction * step)
        val clamped = rawValue.coerceIn(slider.valueFrom, slider.valueTo)
        if (slider.stepSize > 0f) {
            val stepsFromMin = Math.round((clamped - slider.valueFrom) / slider.stepSize)
            val stepped = slider.valueFrom + (stepsFromMin * slider.stepSize)
            return stepped.coerceIn(slider.valueFrom, slider.valueTo)
        }
        return clamped
    }

    private fun setupSliderStepButtons(overlay: View) {
        bindSimpleStep(overlay, R.id.btnThresholdMinus, R.id.btnThresholdPlus, sliderThreshold)
        bindSimpleStep(overlay, R.id.btnMinSegmentMinus, R.id.btnMinSegmentPlus, sliderMinSegment)
        bindSimpleStep(overlay, R.id.btnDurationMinus, R.id.btnDurationPlus, sliderDuration)
        bindPaddingStep(overlay)
    }

    private fun bindSimpleStep(overlay: View, minusId: Int, plusId: Int, slider: Slider?) {
        overlay.findViewById<View>(minusId)?.setupAutoRepeat { slider?.let { s -> s.value = stepSlider(s, -1) } }
        overlay.findViewById<View>(plusId)?.setupAutoRepeat { slider?.let { s -> s.value = stepSlider(s, 1) } }
    }

    private fun bindPaddingStep(overlay: View) {
        overlay.findViewById<View>(R.id.btnPaddingPrefixMinus)?.setupAutoRepeat {
            sliderPaddingPrefix?.let { updatePadding(stepSlider(it, -1), isPrefix = true) }
        }
        overlay.findViewById<View>(R.id.btnPaddingPrefixPlus)?.setupAutoRepeat {
            sliderPaddingPrefix?.let { updatePadding(stepSlider(it, 1), isPrefix = true) }
        }
        overlay.findViewById<View>(R.id.btnPaddingPostfixMinus)?.setupAutoRepeat {
            sliderPaddingPostfix?.let { updatePadding(stepSlider(it, -1), isPrefix = false) }
        }
        overlay.findViewById<View>(R.id.btnPaddingPostfixPlus)?.setupAutoRepeat {
            sliderPaddingPostfix?.let { updatePadding(stepSlider(it, 1), isPrefix = false) }
        }
    }

    private fun updatePadding(value: Float, isPrefix: Boolean) {
        if (isPrefix) {
            sliderPaddingPrefix?.value = value
            if (isPaddingLinked) sliderPaddingPostfix?.value = value
        } else {
            sliderPaddingPostfix?.value = value
            if (isPaddingLinked) sliderPaddingPrefix?.value = value
        }
    }

    private fun setupModeListener(
        overlay: View,
        toggleGroup: MaterialButtonToggleGroup?
    ) {
        toggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentMode = if (checkedId == R.id.btnModeKeepSilence) {
                    SilenceDetectionUseCase.DetectionMode.KEEP_RANGES
                } else {
                    SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES
                }
                binding.seekerContainer.customVideoSeeker.detectionMode = currentMode
                updateStatusText(overlay)
            }
        }
    }

    private fun setupPaddingLinkListener(btnLinkPadding: ImageButton?) {
        btnLinkPadding?.setOnClickListener {
            isPaddingLinked = !isPaddingLinked
            btnLinkPadding.alpha = if (isPaddingLinked) 1.0f else 0.4f
            if (isPaddingLinked) {
                val prefixVal = sliderPaddingPrefix?.value ?: 0f
                sliderPaddingPostfix?.value = prefixVal
            }
        }
    }

    private fun setupSliderListeners() {
        sliderThreshold?.addOnChangeListener { _, _, _ -> updatePreview() }
        sliderThreshold?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                val maxAmp = viewModel.waveformMaxAmplitude.value
                val scaledThreshold = if (maxAmp > 0f) slider.value / maxAmp else slider.value
                binding.seekerContainer.customVideoSeeker.noiseThresholdPreview = scaledThreshold
            }

            override fun onStopTrackingTouch(slider: Slider) {
                binding.seekerContainer.customVideoSeeker.noiseThresholdPreview = null
            }
        })

        sliderDuration?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                binding.seekerContainer.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.MIN_SILENCE
            }
            override fun onStopTrackingTouch(slider: Slider) {
                binding.seekerContainer.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.NONE
            }
        })

        sliderMinSegment?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                binding.seekerContainer.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.MIN_SEGMENT
            }
            override fun onStopTrackingTouch(slider: Slider) {
                binding.seekerContainer.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.NONE
            }
        })

        val paddingTouchListener = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                binding.seekerContainer.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.PADDING
            }
            override fun onStopTrackingTouch(slider: Slider) {
                binding.seekerContainer.customVideoSeeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.NONE
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
    }

    private fun setupActionListeners(
        btnCancel: android.widget.Button,
        btnApply: android.widget.Button
    ) {
        btnCancel.setOnClickListener { onDismiss() }
        btnApply.setOnClickListener {
            val minKeep = sliderMinSegment?.value?.toLong() ?: 10L
            viewModel.applyDetection(currentMode, minKeep)
            onDismiss()
        }
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
        silencePreviewJob?.cancel()
        silencePreviewJob = scope.launch {
            viewModel.uiState.collect {
                updateStatusText(overlay)
            }
        }
        scope.launch {
            viewModel.rawSilencePreviewRanges.collect { result ->
                binding.seekerContainer.customVideoSeeker.rawSilenceResult = result
            }
        }
    }

    private fun updateStatusText(overlay: View) {
        val tvEstimatedCut = overlay.findViewById<TextView>(R.id.tvEstimatedCut)
        val btnApply = overlay.findViewById<android.widget.Button>(R.id.btnApply)
        val state = viewModel.uiState.value

        if (state is VideoEditingUiState.Success) {
            val ranges = state.detectionPreviewRanges
            if (ranges.isNotEmpty()) {
                val totalSilenceMs = ranges.sumOf { it.last - it.first }
                tvEstimatedCut.text = context.getString(
                    R.string.silence_detected_preview,
                    TimeUtils.formatDuration(totalSilenceMs),
                    ranges.size
                )
                btnApply.isEnabled = true
                return
            }
        }
        tvEstimatedCut.text = context.getString(R.string.no_silence_detected)
        btnApply.isEnabled = false
    }

    fun hide() {
        hideInsideSmartCut()
    }

    internal fun hideInsideSmartCut() {
        silencePreviewJob?.cancel()
        viewModel.clearSilencePreview()
        binding.seekerContainer.customVideoSeeker.noiseThresholdPreview = null
    }
}
