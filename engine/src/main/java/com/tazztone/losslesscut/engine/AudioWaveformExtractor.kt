package com.tazztone.losslesscut.engine

import android.util.Log
import com.tazztone.losslesscut.domain.engine.AudioDecoder
import com.tazztone.losslesscut.domain.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.domain.engine.AudioWaveformProcessor
import com.tazztone.losslesscut.domain.model.WaveformResult
import com.tazztone.losslesscut.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioWaveformExtractorImpl @Inject constructor(
    private val audioDecoder: AudioDecoder,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AudioWaveformExtractor {

    override suspend fun extract(
        uri: String, 
        onProgress: ((WaveformResult) -> Unit)?
    ): WaveformResult? = withContext(ioDispatcher) {
        var accumulator: AudioWaveformProcessor.RmsAccumulator? = null
        var durationMs = 0L
        var lastProgressUpdateUs = 0L

        try {
            audioDecoder.decode(uri).collect { pcm ->
                if (accumulator == null) {
                    durationMs = pcm.durationUs / US_PER_MS
                    val bucketCount = AudioWaveformProcessor.calculateEngineBucketCount(durationMs)
                    accumulator = AudioWaveformProcessor.RmsAccumulator(bucketCount)
                }
                
                AudioWaveformProcessor.updateBucketsRms(
                    info = AudioWaveformProcessor.WaveformBufferInfo(
                        buffer = pcm.buffer,
                        size = pcm.size,
                        startTimeUs = pcm.timeUs,
                        totalDurationUs = pcm.durationUs,
                        sampleRate = pcm.sampleRate,
                        channelCount = pcm.channelCount
                    ),
                    accumulator = accumulator!!
                )

                val durationUs = pcm.durationUs
                val progressIntervalUs = if (durationUs > 0) durationUs / 10 else progressUpdateIntervalUs
                if (onProgress != null && pcm.timeUs - lastProgressUpdateUs > progressIntervalUs) {
                    lastProgressUpdateUs = pcm.timeUs
                    val currentBuckets = accumulator!!.buckets
                    val currentMax = currentBuckets.maxOrNull() ?: 0f
                    onProgress(WaveformResult(currentBuckets.clone(), currentMax, durationUs))
                }
            }

            val finalAccumulator = accumulator ?: return@withContext null
            val finalBuckets = finalAccumulator.toFinalRmsBuckets()
            if (finalBuckets.isEmpty()) return@withContext null

            val maxAmplitude = finalBuckets.maxOrNull() ?: 0f
            // Store raw amplitudes BEFORE fillEdgeBuckets to preserve true silence at edges
            val rawForDetection = finalBuckets.clone()
            AudioWaveformProcessor.fillEdgeBuckets(finalBuckets)
            WaveformResult(rawForDetection, maxAmplitude, durationMs * US_PER_MS)

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting waveform", e)
            null
        }
    }

    private companion object {
        private const val TAG = "AudioWaveformExtractor"
        private const val US_PER_MS = 1000L
        private const val progressUpdateIntervalUs = 1_000_000L
    }
}
