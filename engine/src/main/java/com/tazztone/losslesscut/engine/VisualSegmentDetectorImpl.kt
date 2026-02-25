package com.tazztone.losslesscut.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import com.tazztone.losslesscut.domain.model.TimeRangeMs
import com.tazztone.losslesscut.domain.model.VisualDetectionConfig
import com.tazztone.losslesscut.domain.model.VisualStrategy
import com.tazztone.losslesscut.domain.usecase.IVisualSegmentDetector
import com.tazztone.losslesscut.engine.muxing.MediaDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

class VisualSegmentDetectorImpl @Inject constructor(
    private val dataSource: MediaDataSource
) : IVisualSegmentDetector {

    override suspend fun detect(uri: String, config: VisualDetectionConfig): List<TimeRangeMs> = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val detectedRanges = mutableListOf<TimeRangeMs>()

        try {
            dataSource.setExtractorSource(extractor, uri)
            val trackIndex = selectVideoTrack(extractor)
            if (trackIndex == -1) return@withContext emptyList()

            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()

            codec = MediaCodec.createDecoderByType(mime)

            // Configure for decode-only if supported (API 29+) to skip rendering
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)

            val flags = 0
            codec.configure(format, null, null, flags)
            codec.start()

            detectLoop(extractor, codec, config, durationUs, detectedRanges)

        } catch (e: Exception) {
            Log.e(TAG, "Error during visual detection", e)
        } finally {
            try { codec?.stop() } catch (e: Exception) {}
            try { codec?.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
        }

        return@withContext filterRanges(detectedRanges, config.minSegmentDurationMs)
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                extractor.selectTrack(i)
                return i
            }
        }
        return -1
    }

    private fun detectLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        config: VisualDetectionConfig,
        totalDurationUs: Long,
        detectedRanges: MutableList<TimeRangeMs>
    ) {
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        val timeoutUs = 10000L

        var lastProcessedUs = -1L
        val sampleIntervalUs = config.sampleIntervalMs * 1000L

        var previousHash: Long? = null
        var previousSmallY: ByteArray? = null

        var rangeStartUs: Long = -1

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val time = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, time, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(info, timeoutUs)
            if (outputBufferIndex >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }

                if (info.size > 0) {
                    val currentUs = info.presentationTimeUs

                    if (lastProcessedUs == -1L || (currentUs - lastProcessedUs >= sampleIntervalUs)) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        val outputFormat = codec.getOutputFormat(outputBufferIndex)

                        if (outputBuffer != null) {
                            // Optimization: Calculate features once
                            var currentHash: Long? = null
                            var currentSmallY: ByteArray? = null

                            if (config.strategy == VisualStrategy.SCENE_CHANGE) {
                                currentHash = calculatePHash(outputBuffer, outputFormat, info)
                            } else if (config.strategy == VisualStrategy.FREEZE_FRAME) {
                                currentSmallY = downscaleY(outputBuffer, outputFormat, info, 32, 32)
                            }

                            val isMatch = processFrame(
                                outputBuffer, outputFormat, info, config,
                                currentHash, currentSmallY, previousHash, previousSmallY
                            )

                            // Update state
                            if (currentHash != null) previousHash = currentHash
                            if (currentSmallY != null) previousSmallY = currentSmallY

                            if (isMatch) {
                                if (rangeStartUs == -1L) {
                                    rangeStartUs = currentUs
                                }
                            } else {
                                if (rangeStartUs != -1L) {
                                    detectedRanges.add((rangeStartUs / 1000)..(lastProcessedUs / 1000))
                                    rangeStartUs = -1L
                                }
                            }
                            lastProcessedUs = currentUs
                        }
                    }
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Handle format change
            }
        }

        if (rangeStartUs != -1L) {
            detectedRanges.add((rangeStartUs / 1000)..(lastProcessedUs / 1000))
        }
    }

    private fun processFrame(
        buffer: ByteBuffer,
        format: MediaFormat,
        info: MediaCodec.BufferInfo,
        config: VisualDetectionConfig,
        currentHash: Long?,
        currentSmallY: ByteArray?,
        previousHash: Long?,
        previousSmallY: ByteArray?
    ): Boolean {
        return when (config.strategy) {
            VisualStrategy.BLACK_FRAMES -> isBlackFrame(buffer, format, info, config.sensitivityThreshold)
            VisualStrategy.BLUR_QUALITY -> isBlurry(buffer, format, info, config.sensitivityThreshold)
            VisualStrategy.SCENE_CHANGE -> isSceneChange(config.sensitivityThreshold, currentHash, previousHash)
            VisualStrategy.FREEZE_FRAME -> isFreezeFrame(config.sensitivityThreshold, currentSmallY, previousSmallY)
        }
    }

    private fun isBlackFrame(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo, threshold: Float): Boolean {
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val stride = if (format.containsKey(MediaFormat.KEY_STRIDE)) format.getInteger(MediaFormat.KEY_STRIDE) else width

        var sum = 0L
        val stepX = 10
        val stepY = 10

        var count = 0
        for (y in 0 until height step stepY) {
            val rowStart = info.offset + y * stride
            if (rowStart >= buffer.limit()) break
            for (x in 0 until width step stepX) {
                val idx = rowStart + x
                if (idx < buffer.limit()) {
                    sum += buffer.get(idx).toInt() and 0xFF
                    count++
                }
            }
        }

        val mean = if (count > 0) sum.toDouble() / count else 255.0
        return mean < threshold
    }

    private fun isBlurry(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo, threshold: Float): Boolean {
        val targetW = 256
        val downscaled = downscaleY(buffer, format, info, targetW, -1)
        val w = targetW
        val h = downscaled.size / w

        var sumVar = 0.0
        var sumSqVar = 0.0
        var count = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val p = downscaled[y * w + x].toInt() and 0xFF
                val pUp = downscaled[(y - 1) * w + x].toInt() and 0xFF
                val pDown = downscaled[(y + 1) * w + x].toInt() and 0xFF
                val pLeft = downscaled[y * w + (x - 1)].toInt() and 0xFF
                val pRight = downscaled[y * w + (x + 1)].toInt() and 0xFF

                val lap = pUp + pDown + pLeft + pRight - 4 * p
                sumVar += lap
                sumSqVar += lap * lap
                count++
            }
        }

        if (count == 0) return false
        val mean = sumVar / count
        val variance = (sumSqVar / count) - (mean * mean)

        return variance < threshold
    }

    private fun isSceneChange(
        threshold: Float,
        currentHash: Long?,
        previousHash: Long?
    ): Boolean {
        if (currentHash == null || previousHash == null) return false
        val distance = java.lang.Long.bitCount(currentHash xor previousHash)
        return distance > threshold
    }

    private fun isFreezeFrame(
         threshold: Float,
         currentSmallY: ByteArray?,
         previousSmallY: ByteArray?
    ): Boolean {
        if (currentSmallY == null || previousSmallY == null) return false

        var sad = 0L
        for (i in currentSmallY.indices) {
            sad += abs((currentSmallY[i].toInt() and 0xFF) - (previousSmallY[i].toInt() and 0xFF))
        }
        val meanDiff = sad.toDouble() / currentSmallY.size

        return meanDiff < threshold
    }

    private fun downscaleY(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo, targetW: Int, targetH: Int): ByteArray {
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val stride = if (format.containsKey(MediaFormat.KEY_STRIDE)) format.getInteger(MediaFormat.KEY_STRIDE) else width

        val h = if (targetH == -1) (height * targetW / width) else targetH
        val finalH = if (h < 1) 1 else h

        val out = ByteArray(targetW * finalH)

        val xRatio = (width shl 16) / targetW
        val yRatio = (height shl 16) / finalH

        for (y in 0 until finalH) {
            val srcY = (y * yRatio) shr 16
            val rowOffset = info.offset + srcY * stride
            for (x in 0 until targetW) {
                val srcX = (x * xRatio) shr 16
                val offset = rowOffset + srcX
                if (offset < buffer.limit()) {
                    out[y * targetW + x] = buffer.get(offset)
                }
            }
        }
        return out
    }

    private fun calculatePHash(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo): Long {
        val small = downscaleY(buffer, format, info, 32, 32)

        val vals = DoubleArray(32 * 32)
        for (i in small.indices) vals[i] = (small[i].toInt() and 0xFF).toDouble()

        val rowTransformed = Array(32) { DoubleArray(8) }
        val c = DoubleArray(32)
        c[0] = 1.0 / sqrt(2.0)
        for (i in 1 until 32) c[i] = 1.0

        for (y in 0 until 32) {
            for (u in 0 until 8) {
                var sum = 0.0
                for (x in 0 until 32) {
                     sum += vals[y * 32 + x] * cos((2 * x + 1) * u * Math.PI / 64.0)
                }
                rowTransformed[y][u] = 0.25 * c[u] * sum
            }
        }

        val finalDct = DoubleArray(64)
        for (u in 0 until 8) {
            for (v in 0 until 8) {
                var sum = 0.0
                for (y in 0 until 32) {
                    sum += rowTransformed[y][u] * cos((2 * y + 1) * v * Math.PI / 64.0)
                }
                finalDct[v * 8 + u] = 0.25 * c[v] * sum
            }
        }

        val acValues = mutableListOf<Double>()
        for (i in 1 until 64) {
            acValues.add(finalDct[i])
        }
        acValues.sort()
        val median = acValues[acValues.size / 2]

        var hash = 0L
        for (i in 0 until 64) {
            val bit = if (finalDct[i] > median) 1L else 0L
            hash = hash or (bit shl i)
        }
        return hash
    }

    private fun filterRanges(ranges: List<TimeRangeMs>, minSegmentMs: Long): List<TimeRangeMs> {
        return ranges.filter { (it.last - it.first) >= minSegmentMs }
    }

    companion object {
        private const val TAG = "VisualSegmentDetector"
    }
}
