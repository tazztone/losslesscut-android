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
        bucketCount: Int,
        onProgress: ((WaveformResult) -> Unit)?
    ): WaveformResult? = withContext(Dispatchers.IO) {
        val buckets = FloatArray(bucketCount)
        var durationUs = 0L
        var lastProgressUpdateUs = 0L

        try {
            audioDecoder.decode(uri).collect { pcm ->
                durationUs = pcm.durationUs

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

                val progressIntervalUs = if (durationUs > 0) durationUs / 10 else 1_000_000L
                if (onProgress != null && pcm.timeUs - lastProgressUpdateUs > progressIntervalUs) {
                    lastProgressUpdateUs = pcm.timeUs
                    val currentMax = buckets.maxOrNull() ?: 0f
                    onProgress(WaveformResult(buckets.clone(), currentMax, durationUs))
                }
            }

            val maxAmplitude = buckets.maxOrNull() ?: 0f
            WaveformResult(buckets, maxAmplitude, durationUs)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
