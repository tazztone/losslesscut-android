package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.VisualDetectionConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
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
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private var visualJob: Job? = null
    
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
        listener: VisualDetectionListener
    ) {
        if (cachedUri == uri && cachedIntervalMs == config.sampleIntervalMs && cachedStrategy == config.strategy && cachedAnalysis != null) {
            // Cache hit, fast-path filter
            scope.launch {
                val ranges = VisualSegmentFilter.filter(
                    frames = cachedAnalysis!!,
                    strategy = config.strategy,
                    threshold = config.sensitivityThreshold,
                    minSegmentMs = config.minSegmentDurationMs
                )
                listener.onComplete(ranges)
            }
            return
        }

        cancelVisual()
        visualJob = scope.launch(ioDispatcher) {
            try {
                listener.onProgress(null)
                val analysis = visualSegmentDetector.analyze(
                    uri = uri, 
                    sampleIntervalMs = config.sampleIntervalMs,
                    strategy = config.strategy
                ) { processed, total ->
                    listener.onProgress(processed to total)
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
                listener.onComplete(ranges)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listener.onError(e)
            } finally {
                listener.onProgress(null)
            }
        }
    }

    public fun cancelVisual() {
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
}
