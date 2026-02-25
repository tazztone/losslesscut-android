package com.tazztone.losslesscut.engine

import com.tazztone.losslesscut.domain.engine.AudioDecoder
import com.tazztone.losslesscut.domain.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.domain.engine.AudioWaveformProcessor
import com.tazztone.losslesscut.domain.model.WaveformResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioWaveformExtractorImpl @Inject constructor(
    private val audioDecoder: AudioDecoder
) : AudioWaveformExtractor {

    override suspend fun extract(
        uri: String, 
        onProgress: ((WaveformResult) -> Unit)?
    ): WaveformResult? = withContext(Dispatchers.IO) {
        var buckets = FloatArray(0)
        var durationMs = 0L
        var lastProgressUpdateUs = 0L

        try {
            audioDecoder.decode(uri).collect { pcm ->
                if (buckets.isEmpty()) {
                    durationMs = pcm.durationUs / US_PER_MS
                    val bucketCount = AudioWaveformProcessor.calculateEngineBucketCount(durationMs)
                    buckets = FloatArray(bucketCount)
                }
                
                AudioWaveformProcessor.updateBuckets(
                    info = AudioWaveformProcessor.WaveformBufferInfo(
                        buffer = pcm.buffer,
                        size = pcm.size,
                        startTimeUs = pcm.timeUs,
                        totalDurationUs = pcm.durationUs,
                        sampleRate = pcm.sampleRate,
                        channelCount = pcm.channelCount
                    ),
                    buckets = buckets
                )

                val durationUs = pcm.durationUs
                val progressIntervalUs = if (durationUs > 0) durationUs / 10 else progressUpdateIntervalUs
                if (onProgress != null && pcm.timeUs - lastProgressUpdateUs > progressIntervalUs) {
                    lastProgressUpdateUs = pcm.timeUs
                    val currentMax = buckets.maxOrNull() ?: 0f
                    onProgress(WaveformResult(buckets.clone(), currentMax, durationUs))
                }
            }

            if (buckets.isEmpty()) return@withContext null

            val maxAmplitude = buckets.maxOrNull() ?: 0f
            // Store raw amplitudes BEFORE fillEdgeBuckets to preserve true silence at edges
            val rawForDetection = buckets.clone()
            AudioWaveformProcessor.fillEdgeBuckets(buckets)
            WaveformResult(rawForDetection, maxAmplitude, durationMs * US_PER_MS)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private companion object {
        private const val US_PER_MS = 1000L
        private const val progressUpdateIntervalUs = 1_000_000L
    }
}
