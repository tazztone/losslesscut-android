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
    private var rawWaveformResult: WaveformResult? = null

    fun extractWaveform(scope: CoroutineScope, clip: MediaClip) {
        waveformJob?.cancel()
        _silencePreviewRanges.value = emptyList()
        waveformJob = scope.launch(ioDispatcher) {
            _waveformData.value = null
            rawWaveformResult = null
            
            // SHA-256 caching with duration/dims to detect content changes (best effort)
            // Use v2 suffix for new WaveformResult format
            val cacheKeyInput = "${clip.uri}_${clip.durationMs}_${clip.width}x${clip.height}"
            val cacheKey = "waveform_${HashUtils.sha256(cacheKeyInput)}.v2.bin"
            
            val cached = repository.loadWaveformFromCache(cacheKey)
            if (cached != null) {
                rawWaveformResult = cached
                val normalized = cached.rawAmplitudes.clone()
                AudioWaveformProcessor.normalize(normalized, cached.maxAmplitude)
                _waveformData.value = normalized
                return@launch
            }
            
            val bucketCount = AudioWaveformProcessor.calculateAdaptiveBucketCount(clip.durationMs)
            repository.extractWaveform(clip.uri, bucketCount = bucketCount) { progressResult ->
                // Update UI with normalized view of progress
                val normalized = progressResult.rawAmplitudes.clone()
                AudioWaveformProcessor.normalize(normalized, progressResult.maxAmplitude)
                _waveformData.value = normalized
            }?.let { finalResult ->
                rawWaveformResult = finalResult
                val normalized = finalResult.rawAmplitudes.clone()
                AudioWaveformProcessor.normalize(normalized, finalResult.maxAmplitude)
                _waveformData.value = normalized
                repository.saveWaveformToCache(cacheKey, finalResult)
            }
        }
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
                paddingEndMs = params.paddingEndMs,
                minSegmentMs = params.minSegmentMs
            )
            val ranges = silenceDetectionUseCase.findSilence(
                result,
                config
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
