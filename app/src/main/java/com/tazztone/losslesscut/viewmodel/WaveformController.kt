package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.HashUtils
import com.tazztone.losslesscut.domain.model.DetectionUtils
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

    fun extractWaveform(scope: CoroutineScope, clip: MediaClip) {
        waveformJob?.cancel()
        _silencePreviewRanges.value = emptyList()
        waveformJob = scope.launch(ioDispatcher) {
            _waveformData.value = null
            
            // SHA-256 caching with duration/dims to detect content changes (best effort)
            val cacheKeyInput = "${clip.uri}_${clip.durationMs}_${clip.width}x${clip.height}"
            val cacheKey = "waveform_${HashUtils.sha256(cacheKeyInput)}.bin"
            
            val cached = repository.loadWaveformFromCache(cacheKey)
            if (cached != null) {
                _waveformData.value = cached
                return@launch
            }
            
            val bucketCount = AudioWaveformProcessor.calculateAdaptiveBucketCount(clip.durationMs)
            repository.extractWaveform(clip.uri, bucketCount = bucketCount)
                ?.let { waveform ->
                    _waveformData.value = waveform
                    repository.saveWaveformToCache(cacheKey, waveform)
                }
        }
    }

    fun previewSilenceSegments(
        scope: CoroutineScope,
        params: SilenceDetectionParams,
        onComplete: suspend () -> Unit
    ) {
        val waveform = _waveformData.value ?: return
        
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
                waveform, 
                params.clip.durationMs, 
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
    }

    fun cancelJobs() {
        waveformJob?.cancel()
        silencePreviewJob?.cancel()
    }
}
