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
import com.tazztone.losslesscut.util.setupAutoRepeat
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
    private var btnToggleMode: MaterialButtonToggleGroup = root.findViewById(R.id.btnToggleMode)

    private var btnIntervalMinus: View? = root.findViewById(R.id.btnIntervalMinus)
    private var btnIntervalPlus: View? = root.findViewById(R.id.btnIntervalPlus)
    private var btnSensitivityMinus: View? = root.findViewById(R.id.btnSensitivityMinus)
    private var btnSensitivityPlus: View? = root.findViewById(R.id.btnSensitivityPlus)
    private var btnMinSegmentVisualMinus: View? = root.findViewById(R.id.btnMinSegmentVisualMinus)
    private var btnMinSegmentVisualPlus: View? = root.findViewById(R.id.btnMinSegmentVisualPlus)

    private var currentStrategy = VisualStrategy.SCENE_CHANGE
    private var currentMode = SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES
    private var stateJob: Job? = null
    private var progressJob: Job? = null
    private var filterJob: Job? = null
    private var lastAnalyzedInterval = 0f
    private var analysisStartTimeMs = 0L

    init {
        setupListeners()
        updateSelectionUI()
    }

    fun activate() {
        observeState()
        updateStrategyUI()
        tvDetectedStatus.text = context.getString(R.string.no_visual_detected)
    }

    fun deactivate() {
        stateJob?.cancel()
        progressJob?.cancel()
        viewModel.cancelVisualDetection()
        seeker.visualStrategy = null
    }

    private fun setupListeners() {
        setupStrategyListeners()
        setupModeAndSliderListeners()
        setupActionListeners()
    }

    private fun setupStrategyListeners() {
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
    }

    private fun setupModeAndSliderListeners() {
        btnToggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentMode = if (checkedId == R.id.btnModeKeep) {
                    SilenceDetectionUseCase.DetectionMode.KEEP_RANGES
                } else {
                    SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES
                }
                seeker.detectionMode = currentMode
                updateStatusText()
            }
        }

        sliderSensitivity.addOnChangeListener { _, value, _ ->
            updateValueText(tvSensitivityValue, value, getStrategyUnit())
            triggerFiltering()
        }
        sliderMinSegment.addOnChangeListener { _, value, _ ->
            updateValueText(tvMinSegmentValue, value / 1000f, "s")
            triggerFiltering()
        }
        sliderInterval.addOnChangeListener { _, value, _ ->
            updateIntervalText(value.toInt())
            if (value != lastAnalyzedInterval) {
                btnDetectAction.isEnabled = true
            }
        }

        btnIntervalMinus?.setupAutoRepeat { sliderInterval.value = stepSlider(sliderInterval, -1) }
        btnIntervalPlus?.setupAutoRepeat { sliderInterval.value = stepSlider(sliderInterval, 1) }
        btnSensitivityMinus?.setupAutoRepeat { sliderSensitivity.value = stepSlider(sliderSensitivity, -1) }
        btnSensitivityPlus?.setupAutoRepeat { sliderSensitivity.value = stepSlider(sliderSensitivity, 1) }
        btnMinSegmentVisualMinus?.setupAutoRepeat { sliderMinSegment.value = stepSlider(sliderMinSegment, -1) }
        btnMinSegmentVisualPlus?.setupAutoRepeat { sliderMinSegment.value = stepSlider(sliderMinSegment, 1) }
    }

    private fun setupActionListeners() {
        btnDetectAction.setOnClickListener {
            if (viewModel.visualDetectionProgress.value != null) {
                viewModel.cancelVisualDetection()
            } else {
                startDetection()
            }
        }
        btnCancelVisual.setOnClickListener { onDismiss() }
        btnApplyVisual.setOnClickListener {
            val mode = if (currentStrategy == VisualStrategy.SCENE_CHANGE) {
                SilenceDetectionUseCase.DetectionMode.SPLIT_AT_BOUNDARIES
            } else {
                currentMode
            }
            viewModel.applyDetection(mode)
            onDismiss()
        }
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

    private fun startDetection() {
        seeker.visualStrategy = currentStrategy
        seeker.detectionMode = currentMode
        lastAnalyzedInterval = sliderInterval.value
        analysisStartTimeMs = 0L
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
        sampleIntervalFrames = sliderInterval.value.toInt()
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

    private fun updateIntervalText(frameStep: Int) {
        val fps = (viewModel.uiState.value as? VideoEditingUiState.Success)?.videoFps ?: 30f
        val sec = if (fps > 0f) frameStep / fps else frameStep * 0.0333f
        tvIntervalValue.text = if (frameStep == 1) {
            String.format(Locale.getDefault(), "1 frame (%.2fs)", sec)
        } else {
            String.format(Locale.getDefault(), "%d frames (%.2fs)", frameStep, sec)
        }
    }

    private fun observeState() {
        stateJob?.cancel()
        stateJob = scope.launch {
            viewModel.uiState.collect {
                updateStatusText()
            }
        }

        progressJob?.cancel()
        progressJob = scope.launch {
            viewModel.visualDetectionProgress.collect { progress ->
                updateProgressUI(progress)
            }
        }
    }

    private fun updateProgressUI(progress: Pair<Int, Int>?) {
        val isAnalyzing = progress != null
        sliderSensitivity.isEnabled = !isAnalyzing
        sliderMinSegment.isEnabled = !isAnalyzing
        sliderInterval.isEnabled = !isAnalyzing
        btnIntervalMinus?.isEnabled = !isAnalyzing
        btnIntervalPlus?.isEnabled = !isAnalyzing
        btnSensitivityMinus?.isEnabled = !isAnalyzing
        btnSensitivityPlus?.isEnabled = !isAnalyzing
        btnMinSegmentVisualMinus?.isEnabled = !isAnalyzing
        btnMinSegmentVisualPlus?.isEnabled = !isAnalyzing
        
        btnSceneChange.isEnabled = !isAnalyzing
        btnBlackFrames.isEnabled = !isAnalyzing
        btnFreezeFrame.isEnabled = !isAnalyzing
        btnBlurQuality.isEnabled = !isAnalyzing
        btnToggleMode.isEnabled = !isAnalyzing

        if (isAnalyzing) {
            layoutProgress.visibility = View.VISIBLE
            btnDetectAction.text = context.getString(R.string.cancel)
            btnDetectAction.isEnabled = true
            if (analysisStartTimeMs == 0L) analysisStartTimeMs = System.currentTimeMillis()
            
            val (current, total) = progress!!
            if (total > 0) {
                progressIndicator.isIndeterminate = false
                progressIndicator.progress = (current * 100 / total).coerceIn(0, 100)
                val elapsedMs = System.currentTimeMillis() - analysisStartTimeMs
                if (current > 0 && total > current && elapsedMs > 300) {
                    val remainingFrames = total - current
                    val msPerFrame = elapsedMs.toDouble() / current
                    val etaMs = (remainingFrames * msPerFrame).toLong()
                    val etaStr = TimeUtils.formatDuration(etaMs)
                    tvProgressText.text = context.getString(R.string.analyzing_progress_eta, current, total, etaStr)
                } else {
                    tvProgressText.text = context.getString(R.string.analyzing_progress, current, total)
                }
            } else {
                progressIndicator.isIndeterminate = true
                tvProgressText.text = context.getString(R.string.analyzing_video)
            }
        } else {
            layoutProgress.visibility = View.GONE
            btnDetectAction.text = context.getString(R.string.detect)
            analysisStartTimeMs = 0L
            if (viewModel.hasCachedAnalysis() && sliderInterval.value == lastAnalyzedInterval) {
                btnDetectAction.isEnabled = false
            }
        }
    }

    private fun updateStatusText() {
        val state = viewModel.uiState.value
        if (state is VideoEditingUiState.Success) {
            val ranges = state.detectionPreviewRanges
            if (ranges.isNotEmpty()) {
                val totalMs = ranges.sumOf { it.last - it.first }
                val stringRes = if (currentMode == SilenceDetectionUseCase.DetectionMode.KEEP_RANGES) {
                    R.string.visual_detected_preview_keep
                } else {
                    R.string.visual_detected_preview_discard
                }
                tvDetectedStatus.text = context.getString(
                    stringRes,
                    ranges.size,
                    TimeUtils.formatDuration(totalMs)
                )
                btnApplyVisual.isEnabled = true
                return
            }
        }
        tvDetectedStatus.text = context.getString(R.string.no_visual_detected)
        btnApplyVisual.isEnabled = false
    }

    private fun updateSelectionUI() {
        btnSceneChange.isSelected = currentStrategy == VisualStrategy.SCENE_CHANGE
        btnBlackFrames.isSelected = currentStrategy == VisualStrategy.BLACK_FRAMES
        btnFreezeFrame.isSelected = currentStrategy == VisualStrategy.FREEZE_FRAME
        btnBlurQuality.isSelected = currentStrategy == VisualStrategy.BLUR_QUALITY
    }

    companion object {
        private const val FILTER_DEBOUNCE_DELAY_MS = 100L
        private val SCENE_CHANGE_CONFIG = StrategyConfig(3f, 30f, 12f, 1f)
        private val BLACK_FRAMES_CONFIG = StrategyConfig(5f, 50f, 20f, 1f)
        private val FREEZE_FRAME_CONFIG = StrategyConfig(1f, 30f, 5f, 0.5f)
        private val BLUR_QUALITY_CONFIG = StrategyConfig(50f, 25000f, 500f, 50f)
    }
}
