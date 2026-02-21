package com.tazztone.losslesscut

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object AudioWaveformExtractor {

    /**
     * Decodes audio from [uri] and returns [bucketCount] RMS amplitude values (0.0..1.0).
     * Designed to run on an IO dispatcher — never call on main thread.
     */
    fun extract(context: Context, uri: Uri, bucketCount: Int = 1000): FloatArray? {
        val extractor = MediaExtractor()
        return try {
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

            if (audioTrackIndex == -1 || format == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val buckets = FloatArray(bucketCount)
            val bucketCounts = IntArray(bucketCount)
            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

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
                            val presentationUs = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, sampleSize, presentationUs, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIdx = codec.dequeueOutputBuffer(info, 10_000)
                while (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    outBuf.order(ByteOrder.LITTLE_ENDIAN)
                    
                    val bucketIndex = ((info.presentationTimeUs.toDouble() / durationUs) * (bucketCount - 1))
                        .toInt().coerceIn(0, bucketCount - 1)

                    // Compute RMS of this decoded PCM chunk (16-bit samples assumed)
                    var sumSq = 0.0
                    val shortCount = info.size / 2
                    if (shortCount > 0) {
                        for (i in 0 until shortCount) {
                            val s = outBuf.short.toDouble() / Short.MAX_VALUE
                            sumSq += s * s
                        }
                        val rms = sqrt(sumSq / shortCount).toFloat()
                        // Accumulate into bucket (average later)
                        buckets[bucketIndex] += rms
                        bucketCounts[bucketIndex]++
                    }

                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                        break
                    }
                    outIdx = codec.dequeueOutputBuffer(info, 0)
                }
            }

            codec.stop()
            codec.release()

            // Average buckets
            val averagedBuckets = FloatArray(bucketCount) { i ->
                if (bucketCounts[i] > 0) buckets[i] / bucketCounts[i] else 0f
            }

            // Normalize to 0..1 based on local max
            val max = averagedBuckets.maxOrNull() ?: 1f
            if (max > 0f) {
                for (i in averagedBuckets.indices) {
                    averagedBuckets[i] /= max
                }
            }

            averagedBuckets
        } catch (e: Exception) {
            e.printStackTrace()
            null // Waveform is optional — degrade gracefully
        } finally {
            try { extractor.release() } catch (e: Exception) {}
        }
    }
}
