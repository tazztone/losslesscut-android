package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.TimeUtils
import com.tazztone.losslesscut.domain.model.VisualDetectionConfig
import com.tazztone.losslesscut.domain.model.VisualStrategy
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class VisualDetectionOverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val viewModel: VideoEditingViewModel,
    private val seeker: com.tazztone.losslesscut.customviews.CustomVideoSeeker,
    private val root: View,
    private val onDismiss: () -> Unit
) {
    private var sliderSensitivity: Slider = root.findViewById(R.id.sliderSensitivity)
    private var sliderMinSegment: Slider = root.findViewById(R.id.sliderMinSegment)
    private var sliderInterval: Slider = root.findViewById(R.id.sliderInterval)
    
    private var tvSensitivityLabel: TextView = root.findViewById(R.id.tvSensitivityLabel)
    private var tvSensitivityValue: TextView = root.findViewById(R.id.tvSensitivityValue)
    private var tvMinSegmentValue: TextView = root.findViewById(R.id.tvMinSegmentValue)
    private var tvIntervalValue: TextView = root.findViewById(R.id.tvIntervalValue)
    
    private val btnSceneChange: View = root.findViewById(R.id.btnSceneChange)
    private val btnBlackFrames: View = root.findViewById(R.id.btnBlackFrames)
    private val btnFreezeFrame: View = root.findViewById(R.id.btnFreezeFrame)
    private val btnBlurQuality: View = root.findViewById(R.id.btnBlurQuality)

    private var layoutProgress: View = root.findViewById(R.id.layoutProgress)
    private var progressIndicator: LinearProgressIndicator = root.findViewById(R.id.progressIndicator)
    private var tvProgressText: TextView = root.findViewById(R.id.tvProgressText)
    private var tvDetectedStatus: TextView = root.findViewById(R.id.tvDetectedStatus)
    
    private var btnDetectAction: MaterialButton = root.findViewById(R.id.btnDetectAction)
    private var btnCancelVisual: MaterialButton = root.findViewById(R.id.btnCancelVisual)
    private var btnApplyVisual: MaterialButton = root.findViewById(R.id.btnApplyVisual)

    private var currentStrategy = VisualStrategy.SCENE_CHANGE
    private var stateJob: Job? = null
    private var progressJob: Job? = null
    private var filterJob: Job? = null
    private var lastAnalyzedInterval = 0f

    init {
        setupListeners()
        updateSelectionUI()
    }

    fun activate() {
        observeState()
        updateStrategyUI()
        tvDetectedStatus.text = context.getString(R.string.no_silence_detected)
    }

    fun deactivate() {
        stateJob?.cancel()
        progressJob?.cancel()
        viewModel.cancelVisualDetection()
        seeker.visualStrategy = null
    }

    private fun setupListeners() {
        val onStrategyClick = View.OnClickListener { v ->
            val newStrategy = when (v.id) {
                R.id.btnSceneChange -> VisualStrategy.SCENE_CHANGE
                R.id.btnBlackFrames -> VisualStrategy.BLACK_FRAMES
                R.id.btnFreezeFrame -> VisualStrategy.FREEZE_FRAME
                R.id.btnBlurQuality -> VisualStrategy.BLUR_QUALITY
                else -> currentStrategy
            }
            if (newStrategy != currentStrategy) {
                currentStrategy = newStrategy
                seeker.visualStrategy = newStrategy
                updateSelectionUI()
                updateStrategyUI()
                btnDetectAction.isEnabled = true
            }
        }

        btnSceneChange.setOnClickListener(onStrategyClick)
        btnBlackFrames.setOnClickListener(onStrategyClick)
        btnFreezeFrame.setOnClickListener(onStrategyClick)
        btnBlurQuality.setOnClickListener(onStrategyClick)

        sliderSensitivity.addOnChangeListener { _, value, _ ->
            updateValueText(tvSensitivityValue, value, getStrategyUnit())
            triggerFiltering()
        }
        sliderMinSegment.addOnChangeListener { _, value, _ ->
            updateValueText(tvMinSegmentValue, value / 1000f, "s")
            triggerFiltering()
        }
        sliderInterval.addOnChangeListener { _, value, _ ->
            updateValueText(tvIntervalValue, value / 1000f, "s")
            // Re-enable detect button if interval changes from last analysis
            if (value != lastAnalyzedInterval) {
                btnDetectAction.isEnabled = true
            }
        }

        btnDetectAction.setOnClickListener {
            val progress = viewModel.visualDetectionProgress.value
            if (progress != null) {
                viewModel.cancelVisualDetection()
            } else {
                startDetection()
            }
        }

        btnCancelVisual.setOnClickListener {
            onDismiss()
        }

        btnApplyVisual.setOnClickListener {
            val mode = if (currentStrategy == VisualStrategy.SCENE_CHANGE) {
                SilenceDetectionUseCase.DetectionMode.SPLIT_AT_BOUNDARIES
            } else {
                SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES
            }
            viewModel.applyDetection(mode)
            onDismiss()
        }
    }

    private fun startDetection() {
        seeker.visualStrategy = currentStrategy
        lastAnalyzedInterval = sliderInterval.value
        val config = getVisualConfig()
        viewModel.previewVisualSegments(config)
    }

    private fun triggerFiltering() {
        filterJob?.cancel()
        filterJob = scope.launch {
            delay(FILTER_DEBOUNCE_DELAY_MS)
            viewModel.filterVisualSegments(getVisualConfig())
        }
    }

    private fun getVisualConfig() = VisualDetectionConfig(
        strategy = currentStrategy,
        sensitivityThreshold = sliderSensitivity.value,
        minSegmentDurationMs = sliderMinSegment.value.toLong(),
        sampleIntervalMs = sliderInterval.value.toLong()
    )

    private fun updateStrategyUI() {
        when (currentStrategy) {
            VisualStrategy.SCENE_CHANGE -> {
                tvSensitivityLabel.text = context.getString(R.string.sensitivity)
                applyConfig(SCENE_CHANGE_CONFIG)
            }
            VisualStrategy.BLACK_FRAMES -> {
                tvSensitivityLabel.text = context.getString(R.string.luma_threshold)
                applyConfig(BLACK_FRAMES_CONFIG)
            }
            VisualStrategy.FREEZE_FRAME -> {
                tvSensitivityLabel.text = context.getString(R.string.diff_threshold)
                applyConfig(FREEZE_FRAME_CONFIG)
            }
            VisualStrategy.BLUR_QUALITY -> {
                tvSensitivityLabel.text = context.getString(R.string.blur_threshold)
                applyConfig(BLUR_QUALITY_CONFIG)
            }
        }
        updateValueText(tvSensitivityValue, sliderSensitivity.value, getStrategyUnit())
        triggerFiltering()
    }

    private fun applyConfig(config: StrategyConfig) {
        sliderSensitivity.valueFrom = config.min
        sliderSensitivity.valueTo = config.max
        sliderSensitivity.stepSize = config.step
        sliderSensitivity.value = config.default.coerceIn(config.min, config.max)
    }

    private fun getStrategyUnit(): String = when (currentStrategy) {
        VisualStrategy.SCENE_CHANGE -> "bits"
        VisualStrategy.BLACK_FRAMES -> "luma"
        VisualStrategy.FREEZE_FRAME -> "diff"
        VisualStrategy.BLUR_QUALITY -> "var"
    }

    private fun updateValueText(tv: TextView, value: Float, unit: String) {
        tv.text = String.format(Locale.getDefault(), "%.1f %s", value, unit)
    }

    private fun observeState() {
        stateJob?.cancel()
        stateJob = scope.launch {
            viewModel.uiState.collect { state ->
                if (state is VideoEditingUiState.Success) {
                    val ranges = state.detectionPreviewRanges
                    if (ranges.isNotEmpty()) {
                        val totalMs = ranges.sumOf { it.last - it.first }
                        tvDetectedStatus.text = context.getString(
                            R.string.visual_detected_preview,
                            ranges.size,
                            TimeUtils.formatDuration(totalMs)
                        )
                        btnApplyVisual.isEnabled = true
                    } else {
                        tvDetectedStatus.text = context.getString(R.string.no_silence_detected)
                        btnApplyVisual.isEnabled = false
                    }
                }
            }
        }

        progressJob?.cancel()
        progressJob = scope.launch {
            viewModel.visualDetectionProgress.collect { progress ->
                val isAnalyzing = progress != null
                sliderSensitivity.isEnabled = !isAnalyzing
                sliderMinSegment.isEnabled = !isAnalyzing
                sliderInterval.isEnabled = !isAnalyzing
                
                btnSceneChange.isEnabled = !isAnalyzing
                btnBlackFrames.isEnabled = !isAnalyzing
                btnFreezeFrame.isEnabled = !isAnalyzing
                btnBlurQuality.isEnabled = !isAnalyzing

                if (isAnalyzing) {
                    layoutProgress.visibility = View.VISIBLE
                    btnDetectAction.text = context.getString(R.string.cancel)
                    btnDetectAction.isEnabled = true
                    val (current, total) = progress!!
                    if (total > 0) {
                        progressIndicator.isIndeterminate = false
                        progressIndicator.progress = (current * 100 / total).coerceIn(0, 100)
                        tvProgressText.text = context.getString(R.string.analyzing_progress, current, total)
                    } else {
                        progressIndicator.isIndeterminate = true
                        tvProgressText.text = context.getString(R.string.analyzing_video)
                    }
                } else {
                    layoutProgress.visibility = View.GONE
                    btnDetectAction.text = context.getString(R.string.detect)
                    
                    // If we just finished successful analysis, disable detect button
                    val hasCached = viewModel.hasCachedAnalysis()

                    if (hasCached && sliderInterval.value == lastAnalyzedInterval) {
                        btnDetectAction.isEnabled = false
                    }
                }
            }
        }
    }
    private fun updateSelectionUI() {
        btnSceneChange.isSelected = currentStrategy == VisualStrategy.SCENE_CHANGE
        btnBlackFrames.isSelected = currentStrategy == VisualStrategy.BLACK_FRAMES
        btnFreezeFrame.isSelected = currentStrategy == VisualStrategy.FREEZE_FRAME
        btnBlurQuality.isSelected = currentStrategy == VisualStrategy.BLUR_QUALITY
    }

    private data class StrategyConfig(val min: Float, val max: Float, val default: Float, val step: Float)

    companion object {
        private const val FILTER_DEBOUNCE_DELAY_MS = 100L
        private val SCENE_CHANGE_CONFIG = StrategyConfig(3f, 30f, 12f, 1f)
        private val BLACK_FRAMES_CONFIG = StrategyConfig(5f, 50f, 20f, 1f)
        private val FREEZE_FRAME_CONFIG = StrategyConfig(1f, 15f, 3f, 0.5f)
        private val BLUR_QUALITY_CONFIG = StrategyConfig(50f, 5000f, 500f, 10f)
    }
}
