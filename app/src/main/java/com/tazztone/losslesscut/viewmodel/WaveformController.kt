package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.HashUtils
import com.tazztone.losslesscut.domain.model.DetectionUtils
import com.tazztone.losslesscut.domain.model.WaveformResult
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import com.tazztone.losslesscut.domain.engine.AudioWaveformProcessor
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Encapsulates waveform extraction and silence detection logic.
 */
class WaveformController @Inject constructor(
    private val repository: IVideoEditingRepository,
    private val silenceDetectionUseCase: SilenceDetectionUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    data class SilenceDetectionParams(
        val threshold: Float,
        val minSilenceMs: Long,
        val paddingStartMs: Long,
        val paddingEndMs: Long,
        val minSegmentMs: Long,
        val clip: MediaClip
    )

    private val _waveformData = MutableStateFlow<FloatArray?>(null)
    val waveformData: StateFlow<FloatArray?> = _waveformData.asStateFlow()

    private val _silencePreviewRanges = MutableStateFlow<List<LongRange>>(emptyList())
    val silencePreviewRanges: StateFlow<List<LongRange>> = _silencePreviewRanges.asStateFlow()

    private var waveformJob: Job? = null
    private var silencePreviewJob: Job? = null

    // Store raw result for silence detection
    @Volatile
    private var rawWaveformResult: WaveformResult? = null

    fun extractWaveform(scope: CoroutineScope, clip: MediaClip) {
        waveformJob?.cancel()
        _silencePreviewRanges.value = emptyList()
        waveformJob = scope.launch(ioDispatcher) {
            _waveformData.value = null
            rawWaveformResult = null
            
            // SHA-256 caching with duration/dims to detect content changes (best effort)
            // Use v3 suffix for new fixed 100Hz WaveformResult format
            val cacheKeyInput = "${clip.uri}_${clip.durationMs}_${clip.width}x${clip.height}"
            val cacheKey = "waveform_${HashUtils.sha256(cacheKeyInput)}.v3.bin"
            
            val cached = repository.loadWaveformFromCache(cacheKey)
            if (cached != null) {
                rawWaveformResult = cached
                updateUiWaveform(cached, clip.durationMs)
                return@launch
            }
            
            repository.extractWaveform(clip.uri) { progressResult ->
                updateUiWaveform(progressResult, clip.durationMs)
            }?.let { finalResult ->
                rawWaveformResult = finalResult
                updateUiWaveform(finalResult, clip.durationMs)
                repository.saveWaveformToCache(cacheKey, finalResult)
            }
        }
    }

    private fun updateUiWaveform(result: WaveformResult, durationMs: Long) {
        val uiBucketCount = AudioWaveformProcessor.calculateUiBucketCount(durationMs)
        val downsampled = AudioWaveformProcessor.downsample(result.rawAmplitudes, uiBucketCount)
        AudioWaveformProcessor.fillEdgeBuckets(downsampled)
        AudioWaveformProcessor.normalize(downsampled, result.maxAmplitude)
        _waveformData.value = downsampled
    }

    fun previewSilenceSegments(
        scope: CoroutineScope,
        params: SilenceDetectionParams,
        onComplete: suspend () -> Unit
    ) {
        val result = rawWaveformResult ?: return
        
        silencePreviewJob?.cancel()
        silencePreviewJob = scope.launch(ioDispatcher) {
            val config = DetectionUtils.SilenceDetectionConfig(
                threshold = params.threshold,
                minSilenceMs = params.minSilenceMs,
                paddingStartMs = params.paddingStartMs,
                paddingEndMs = params.paddingEndMs
            )
            val ranges = silenceDetectionUseCase.findSilence(
                result,
                config,
                params.minSegmentMs
            )
            _silencePreviewRanges.value = ranges
            onComplete()
        }
    }

    fun clearSilencePreview(scope: CoroutineScope, onComplete: suspend () -> Unit) {
        silencePreviewJob?.cancel()
        scope.launch(ioDispatcher) {
            _silencePreviewRanges.value = emptyList()
            onComplete()
        }
    }
    
    fun clearInternal() {
        waveformJob?.cancel()
        silencePreviewJob?.cancel()
        _waveformData.value = null
        _silencePreviewRanges.value = emptyList()
        rawWaveformResult = null
    }

    fun cancelJobs() {
        waveformJob?.cancel()
        silencePreviewJob?.cancel()
    }
}
