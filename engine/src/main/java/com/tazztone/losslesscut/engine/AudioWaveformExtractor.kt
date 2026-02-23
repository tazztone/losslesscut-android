package com.tazztone.losslesscut.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.tazztone.losslesscut.domain.engine.AudioWaveformExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioWaveformExtractorImpl @Inject constructor() : AudioWaveformExtractor {

    override suspend fun extract(
        context: Context, 
        uri: Uri, 
        bucketCount: Int,
        onProgress: ((FloatArray) -> Unit)?
    ): FloatArray? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            // Find first audio track
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) return@withContext null

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()

                val buckets = FloatArray(bucketCount)
                val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            var bufferArray = ByteArray(8192) 
            
            var lastProgressUpdateUs = 0L
            val progressIntervalUs = if (durationUs > 0) durationUs / 10 else 1_000_000L 

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIdx = codec.dequeueOutputBuffer(info, 10_000)
                while (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    
                    if (info.size > 0) {
                        if (info.size > bufferArray.size) {
                            bufferArray = ByteArray(info.size)
                        }
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.get(bufferArray, 0, info.size)

                        var peak = 0
                        
                        for (j in 0 until info.size - 1 step 4) {
                            val low = bufferArray[j].toInt() and 0xFF
                            val high = bufferArray[j+1].toInt() shl 8
                            val sample = (high or low).toShort().toInt()
                            
                            val absVal = if (sample < 0) -sample else sample
                            if (absVal > peak) peak = absVal
                        }

                        val bucketIndex = if (durationUs > 0) {
                            ((info.presentationTimeUs.toDouble() / durationUs) * (bucketCount - 1))
                                .toInt().coerceIn(0, bucketCount - 1)
                        } else 0

                        val normalizedPeak = peak.toFloat() / Short.MAX_VALUE
                        if (normalizedPeak > buckets[bucketIndex]) {
                            buckets[bucketIndex] = normalizedPeak
                        }
                    }

                    codec.releaseOutputBuffer(outIdx, false)
                    
                    if (onProgress != null && info.presentationTimeUs - lastProgressUpdateUs > progressIntervalUs) {
                        lastProgressUpdateUs = info.presentationTimeUs
                        
                        val currentBuckets = buckets.clone()
                        val currentMax = currentBuckets.maxOrNull() ?: 1f
                        if (currentMax > 0f) {
                            for (i in currentBuckets.indices) {
                                currentBuckets[i] /= currentMax
                            }
                        }
                        onProgress(currentBuckets) 
                    }

                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                        break
                    }
                    outIdx = codec.dequeueOutputBuffer(info, 0)
                }
            }

                codec.stop()
                
                val maxPeak = buckets.maxOrNull() ?: 1f
                if (maxPeak > 0f) {
                    for (i in buckets.indices) {
                        buckets[i] /= maxPeak
                    }
                }
                buckets
            } finally {
                codec.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { extractor.release() } catch (e: Exception) {}
        }
    }
}
