package com.tazztone.losslesscut.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.tazztone.losslesscut.domain.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.domain.engine.AudioWaveformProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioWaveformExtractorImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AudioWaveformExtractor {

    override suspend fun extract(
        uri: String, 
        bucketCount: Int,
        onProgress: ((FloatArray) -> Unit)?
    ): FloatArray? = withContext(Dispatchers.IO) {
        val uriParsed = Uri.parse(uri)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uriParsed, null)
            val trackInfo = findAudioTrack(extractor) ?: return@withContext null
            extractor.selectTrack(trackInfo.index)
            
            decodeAndProcess(extractor, trackInfo.format, bucketCount, onProgress)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { extractor.release() } catch (e: Exception) {}
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): TrackInfo? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return TrackInfo(i, format)
            }
        }
        return null
    }

    private suspend fun decodeAndProcess(
        extractor: MediaExtractor,
        format: MediaFormat,
        bucketCount: Int,
        onProgress: ((FloatArray) -> Unit)?
    ): FloatArray? {
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(mime)
        
        return try {
            codec.configure(format, null, null, 0)
            codec.start()
            val params = ExtractionParams(
                extractor, codec, durationUs, bucketCount, sampleRate, channelCount, onProgress
            )
            val result = runDecodeLoop(params)
            codec.stop()
            result
        } finally {
            codec.release()
        }
    }

    private data class ExtractionParams(
        val extractor: MediaExtractor,
        val codec: MediaCodec,
        val durationUs: Long,
        val bucketCount: Int,
        val sampleRate: Int,
        val channelCount: Int,
        val onProgress: ((FloatArray) -> Unit)?
    )

    private suspend fun runDecodeLoop(params: ExtractionParams): FloatArray {
        val buckets = FloatArray(params.bucketCount)
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        var bufferArray = ByteArray(8192)
        var lastProgressUpdateUs = 0L

        while (!sawOutputEOS) {
            coroutineContext.ensureActive()
            if (!sawInputEOS) {
                sawInputEOS = feedInput(params.extractor, params.codec)
            }
            val drainParams = DrainParams(
                params.codec, info, buckets, params.durationUs, params.bucketCount,
                params.sampleRate, params.channelCount, params.onProgress
            )
            val drainResult = drainOutput(drainParams, bufferArray, lastProgressUpdateUs)
            bufferArray = drainResult.buffer
            lastProgressUpdateUs = drainResult.lastUpdate
            if (drainResult.eos) sawOutputEOS = true
        }
        AudioWaveformProcessor.normalize(buckets)
        return buckets
    }

    private fun feedInput(extractor: MediaExtractor, codec: MediaCodec): Boolean {
        val inIdx = codec.dequeueInputBuffer(10_000)
        if (inIdx >= 0) {
            val buf = codec.getInputBuffer(inIdx)!!
            val sampleSize = extractor.readSampleData(buf, 0)
            if (sampleSize < 0) {
                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return true
            } else {
                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
            }
        }
        return false
    }

    private fun drainOutput(
        params: DrainParams,
        bufferArray: ByteArray,
        lastProgressUpdateUs: Long
    ): DrainResult {
        var currentLastUpdate = lastProgressUpdateUs
        var currentBuffer = bufferArray
        var outIdx = params.codec.dequeueOutputBuffer(params.info, 10_000)
        while (outIdx >= 0) {
            val outBuf = params.codec.getOutputBuffer(outIdx)!!
            if (params.info.size > 0) {
                if (params.info.size > currentBuffer.size) currentBuffer = ByteArray(params.info.size)
                outBuf.position(params.info.offset)
                outBuf.limit(params.info.offset + params.info.size)
                outBuf.get(currentBuffer, 0, params.info.size)

                AudioWaveformProcessor.updateBuckets(
                    info = AudioWaveformProcessor.WaveformBufferInfo(
                        buffer = currentBuffer,
                        size = params.info.size,
                        startTimeUs = params.info.presentationTimeUs,
                        totalDurationUs = params.durationUs,
                        sampleRate = params.sampleRate,
                        channelCount = params.channelCount
                    ),
                    buckets = params.buckets
                )
            }
            params.codec.releaseOutputBuffer(outIdx, false)

            val progressIntervalUs = if (params.durationUs > 0) params.durationUs / 10 else 1_000_000L
            if (params.onProgress != null && params.info.presentationTimeUs - currentLastUpdate > progressIntervalUs) {
                currentLastUpdate = params.info.presentationTimeUs
                val currentBuckets = params.buckets.clone()
                AudioWaveformProcessor.normalize(currentBuckets)
                params.onProgress.invoke(currentBuckets) 
            }

            if (params.info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                return DrainResult(true, currentLastUpdate, currentBuffer)
            }
            outIdx = params.codec.dequeueOutputBuffer(params.info, 0)
        }
        return DrainResult(false, currentLastUpdate, currentBuffer)
    }

    private data class TrackInfo(val index: Int, val format: MediaFormat)
    private data class DrainParams(
        val codec: MediaCodec,
        val info: MediaCodec.BufferInfo,
        val buckets: FloatArray,
        val durationUs: Long,
        val bucketCount: Int,
        val sampleRate: Int,
        val channelCount: Int,
        val onProgress: ((FloatArray) -> Unit)?
    )
    private data class DrainResult(val eos: Boolean, val lastUpdate: Long, val buffer: ByteArray)
}
