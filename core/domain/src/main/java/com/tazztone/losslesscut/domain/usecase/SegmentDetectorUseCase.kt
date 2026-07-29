package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.cache.IAnalysisCache
import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.VisualDetectionConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

public interface VisualDetectionListener {
    public fun onProgress(progress: Pair<Int, Int>?) {}
    public fun onComplete(ranges: List<LongRange>)
    public fun onError(error: Throwable) {}
}

@Singleton
public class SegmentDetectorUseCase @Inject constructor(
    private val visualSegmentDetector: IVisualSegmentDetector,
    private val analysisCache: IAnalysisCache? = null,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private var visualJob: Job? = null
    private val requestGeneration = AtomicLong(0L)
    
    @Volatile
    private var cachedAnalysis: List<FrameAnalysis>? = null
    @Volatile
    private var cachedIntervalMs: Long = -1L
    @Volatile
    private var cachedUri: String? = null
    @Volatile
    private var cachedStrategy: com.tazztone.losslesscut.domain.model.VisualStrategy? = null

    @Suppress("TooGenericExceptionCaught")
    public fun detectVisual(
        scope: CoroutineScope,
        uri: String,
        config: VisualDetectionConfig,
        listener: VisualDetectionListener,
        clip: MediaClip? = null
    ) {
        cancelVisual()
        val requestId = requestGeneration.incrementAndGet()

        if (hasCachedAnalysisFor(uri, config)) {
            // Memory cache hit, fast-path filter
            visualJob = scope.launch(ioDispatcher) {
                val ranges = VisualSegmentFilter.filter(
                    frames = cachedAnalysis!!,
                    strategy = config.strategy,
                    threshold = config.sensitivityThreshold,
                    minSegmentMs = config.minSegmentDurationMs
                )
                notifyIfCurrent(requestId) { listener.onComplete(ranges) }
            }
            return
        }

        visualJob = scope.launch(ioDispatcher) {
            performVisualAnalysis(requestId, uri, config, listener, clip)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun performVisualAnalysis(
        requestId: Long,
        uri: String,
        config: VisualDetectionConfig,
        listener: VisualDetectionListener,
        clip: MediaClip?
    ) {
        try {
            val targetClip = clip ?: MediaClip(
                uri = uri, fileName = "", durationMs = 0L, width = 0, height = 0,
                videoMime = null, audioMime = null, sampleRate = 0, channelCount = 0,
                fps = 0f, rotation = 0, isAudioOnly = false
            )
            val persistentAnalysis = analysisCache?.getFrameAnalysis(
                targetClip, config.strategy, config.sampleIntervalMs
            )

            val analysis = if (persistentAnalysis != null) {
                persistentAnalysis
            } else {
                notifyIfCurrent(requestId) { listener.onProgress(null) }
                val newAnalysis = visualSegmentDetector.analyze(
                    uri = uri,
                    sampleIntervalMs = config.sampleIntervalMs,
                    strategy = config.strategy
                ) { processed, total ->
                    notifyIfCurrent(requestId) { listener.onProgress(processed to total) }
                }
                analysisCache?.saveFrameAnalysis(
                    targetClip, config.strategy, config.sampleIntervalMs, newAnalysis
                )
                newAnalysis
            }

            cachedAnalysis = analysis
            cachedIntervalMs = config.sampleIntervalMs
            cachedUri = uri
            cachedStrategy = config.strategy

            val ranges = VisualSegmentFilter.filter(
                frames = analysis,
                strategy = config.strategy,
                threshold = config.sensitivityThreshold,
                minSegmentMs = config.minSegmentDurationMs
            )
            notifyIfCurrent(requestId) { listener.onComplete(ranges) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notifyIfCurrent(requestId) { listener.onError(e) }
        } finally {
            if (requestGeneration.get() == requestId) {
                visualJob = null
            }
        }
    }

    public fun cancelVisual() {
        requestGeneration.incrementAndGet()
        visualJob?.cancel()
        visualJob = null
    }

    public fun hasCachedAnalysis(): Boolean {
        return cachedAnalysis != null
    }

    public fun clearCache() {
        cachedAnalysis = null
        cachedIntervalMs = -1L
        cachedUri = null
        cachedStrategy = null
    }

    private fun hasCachedAnalysisFor(uri: String, config: VisualDetectionConfig): Boolean {
        return cachedAnalysis != null &&
                cachedUri == uri &&
                cachedIntervalMs == config.sampleIntervalMs &&
                cachedStrategy == config.strategy
    }

    private inline fun notifyIfCurrent(requestId: Long, callback: () -> Unit) {
        if (requestGeneration.get() == requestId) callback()
    }
}
