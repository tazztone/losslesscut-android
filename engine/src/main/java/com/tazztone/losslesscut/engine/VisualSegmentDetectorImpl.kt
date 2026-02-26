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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

@Suppress("MagicNumber")
class VisualSegmentDetectorImpl @Inject constructor(
    private val dataSource: MediaDataSource
) : IVisualSegmentDetector {

    private class DetectionContext(
        val extractor: MediaExtractor,
        val codec: MediaCodec,
        val config: VisualDetectionConfig,
        val detectedRanges: MutableList<TimeRangeMs> = mutableListOf()
    ) {
        var previousHash: Long? = null
        var previousSmallY: ByteArray? = null
        var currentHash: Long? = null
        var currentSmallY: ByteArray? = null
        var rangeStartUs: Long = -1L

        var lastProcessedUs: Long = -1L
        val sampleIntervalUs: Long = config.sampleIntervalMs * US_PER_MS
        val info: MediaCodec.BufferInfo = MediaCodec.BufferInfo()

        var sawInputEOS = false
        var sawOutputEOS = false
    }

    override suspend fun detect(
        uri: String,
        config: VisualDetectionConfig,
        onProgress: (Int, Int) -> Unit
    ): List<TimeRangeMs> = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var context: DetectionContext? = null

        try {
            dataSource.setExtractorSource(extractor, uri)
            val trackIndex = selectVideoTrack(extractor)
            if (trackIndex == -1) return@withContext emptyList()

            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()

            codec = MediaCodec.createDecoderByType(mime)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            codec.configure(format, null, null, NO_FLAGS)
            codec.start()

            context = DetectionContext(extractor, codec, config)
            val estimatedTotal = (durationUs / context.sampleIntervalUs).toInt()
            
            detectLoop(context, estimatedTotal, onProgress)

        } catch (e: MediaCodec.CodecException) {
            Log.e(TAG, "MediaCodec error during visual detection", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state during visual detection", e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Unexpected error during visual detection", e)
        } finally {
            cleanup(codec, extractor)
        }

        return@withContext filterRanges(context?.detectedRanges ?: emptyList(), config.minSegmentDurationMs)
    }

    private fun cleanup(codec: MediaCodec?, extractor: MediaExtractor) {
        try { 
            codec?.stop() 
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state when stopping codec during cleanup", e)
        }
        try { 
            codec?.release() 
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state when releasing codec during cleanup", e)
        }
        try { 
            extractor.release() 
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state when releasing extractor during cleanup", e)
        }
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

    private suspend fun detectLoop(ctx: DetectionContext, estimatedTotal: Int, onProgress: (Int, Int) -> Unit) {
        var processedCount = 0
        while (!ctx.sawOutputEOS) {
            withContext(Dispatchers.Default) { ensureActive() }
            
            if (!ctx.sawInputEOS) ctx.sawInputEOS = feedInput(ctx)

            val outputBufferIndex = ctx.codec.dequeueOutputBuffer(ctx.info, TIMEOUT_US)
            if (outputBufferIndex >= 0) {
                val wasProcessed = processOutputBufferResult(ctx, outputBufferIndex)
                if (wasProcessed) {
                    processedCount++
                    onProgress(processedCount, estimatedTotal)
                }
            }
        }

        if (ctx.rangeStartUs != -1L) {
            ctx.detectedRanges.add((ctx.rangeStartUs / US_PER_MS)..(ctx.lastProcessedUs / US_PER_MS))
        }
    }

    private fun processOutputBufferResult(ctx: DetectionContext, index: Int): Boolean {
        var didProcess = false
        if (ctx.info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            ctx.sawOutputEOS = true
        }

        if (ctx.info.size > 0) {
            val currentUs = ctx.info.presentationTimeUs
            if (shouldProcess(ctx.lastProcessedUs, currentUs, ctx.sampleIntervalUs)) {
                processOutputBuffer(ctx, index)
                ctx.lastProcessedUs = currentUs
                didProcess = true
            }
        }
        ctx.codec.releaseOutputBuffer(index, false)
        return didProcess
    }

    private fun feedInput(ctx: DetectionContext): Boolean {
        val inputBufferIndex = ctx.codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferIndex >= 0) {
            val inputBuffer = ctx.codec.getInputBuffer(inputBufferIndex) ?: return false
            val sampleSize = ctx.extractor.readSampleData(inputBuffer, 0)
            if (sampleSize < 0) {
                ctx.codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return true
            }
            ctx.codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, ctx.extractor.sampleTime, 0)
            ctx.extractor.advance()
        }
        return false
    }

    private fun shouldProcess(lastProcessedUs: Long, currentUs: Long, intervalUs: Long): Boolean {
        return lastProcessedUs == -1L || (currentUs - lastProcessedUs >= intervalUs)
    }

    private fun processOutputBuffer(ctx: DetectionContext, index: Int) {
        val outputBuffer = ctx.codec.getOutputBuffer(index)
        val outputFormat = ctx.codec.getOutputFormat(index)

        if (outputBuffer != null) {
            ctx.currentHash = null
            ctx.currentSmallY = null

            if (ctx.config.strategy == VisualStrategy.SCENE_CHANGE) {
                ctx.currentHash = calculatePHash(outputBuffer, outputFormat, ctx.info)
            } else if (ctx.config.strategy == VisualStrategy.FREEZE_FRAME) {
                ctx.currentSmallY = downscaleY(outputBuffer, outputFormat, ctx.info, DOWNSCALE_SIZE, DOWNSCALE_SIZE)
            }

            val isMatch = processFrame(outputBuffer, outputFormat, ctx.info, ctx.config, ctx)

            if (ctx.currentHash != null) ctx.previousHash = ctx.currentHash
            if (ctx.currentSmallY != null) ctx.previousSmallY = ctx.currentSmallY

            if (isMatch) {
                if (ctx.rangeStartUs == -1L) ctx.rangeStartUs = ctx.info.presentationTimeUs
            } else {
                if (ctx.rangeStartUs != -1L) {
                    ctx.detectedRanges.add((ctx.rangeStartUs / US_PER_MS)..(ctx.info.presentationTimeUs / US_PER_MS))
                    ctx.rangeStartUs = -1L
                }
            }
        }
    }

    private fun processFrame(
        buffer: ByteBuffer,
        format: MediaFormat,
        info: MediaCodec.BufferInfo,
        config: VisualDetectionConfig,
        ctx: DetectionContext
    ): Boolean {
        return when (config.strategy) {
            VisualStrategy.BLACK_FRAMES -> isBlackFrame(buffer, format, info, config.sensitivityThreshold)
            VisualStrategy.BLUR_QUALITY -> isBlurry(buffer, format, info, config.sensitivityThreshold)
            VisualStrategy.SCENE_CHANGE -> isSceneChange(config.sensitivityThreshold, ctx.currentHash, ctx.previousHash)
            VisualStrategy.FREEZE_FRAME -> isFreezeFrame(config.sensitivityThreshold, ctx.currentSmallY, ctx.previousSmallY)
        }
    }

    @Suppress("MagicNumber")
    private fun isBlackFrame(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo, threshold: Float): Boolean {
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val stride = if (format.containsKey(MediaFormat.KEY_STRIDE)) format.getInteger(MediaFormat.KEY_STRIDE) else width

        var sum = 0L
        var count = 0
        for (y in 0 until height step STEP_Y) {
            val rowStart = info.offset + y * stride
            if (rowStart >= buffer.limit()) break
            for (x in 0 until width step STEP_X) {
                val idx = rowStart + x
                if (idx < buffer.limit()) {
                    sum += buffer.get(idx).toInt() and PIXEL_MASK
                    count++
                }
            }
        }

        val mean = if (count > MIN_COUNT) sum.toDouble() / count else MAX_LUMA
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
                val p = downscaled[y * w + x].toInt() and PIXEL_MASK
                val pUp = downscaled[(y - 1) * w + x].toInt() and PIXEL_MASK
                val pDown = downscaled[(y + 1) * w + x].toInt() and PIXEL_MASK
                val pLeft = downscaled[y * w + (x - 1)].toInt() and PIXEL_MASK
                val pRight = downscaled[y * w + (x + 1)].toInt() and PIXEL_MASK

                val lap = pUp + pDown + pLeft + pRight - LAPLACIAN_CENTER_WEIGHT * p
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
            sad += abs((currentSmallY[i].toInt() and PIXEL_MASK) - (previousSmallY[i].toInt() and PIXEL_MASK))
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

        val xRatio = (width shl FIXED_POINT_SHIFT) / targetW
        val yRatio = (height shl FIXED_POINT_SHIFT) / finalH

        for (y in 0 until finalH) {
            val srcY = (y * yRatio) shr FIXED_POINT_SHIFT
            val rowOffset = info.offset + srcY * stride
            for (x in 0 until targetW) {
                val srcX = (x * xRatio) shr FIXED_POINT_SHIFT
                val offset = rowOffset + srcX
                if (offset < buffer.limit()) {
                    out[y * targetW + x] = buffer.get(offset)
                }
            }
        }
        return out
    }

    private fun calculatePHash(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo): Long {
        val small = downscaleY(buffer, format, info, DOWNSCALE_SIZE, DOWNSCALE_SIZE)

        val vals = DoubleArray(DOWNSCALE_SIZE * DOWNSCALE_SIZE)
        for (i in small.indices) vals[i] = (small[i].toInt() and PIXEL_MASK).toDouble()

        val rowTransformed = Array(DOWNSCALE_SIZE) { DoubleArray(DCT_SIZE) }
        val c = DoubleArray(DOWNSCALE_SIZE)
        c[0] = 1.0 / sqrt(2.0)
        for (i in 1 until DOWNSCALE_SIZE) c[i] = 1.0

        for (y in 0 until DOWNSCALE_SIZE) {
            for (u in 0 until DCT_SIZE) {
                var sum = 0.0
                for (x in 0 until DOWNSCALE_SIZE) {
                     sum += vals[y * DOWNSCALE_SIZE + x] * cos((2 * x + 1) * u * Math.PI / DCT_DENOMINATOR)
                }
                rowTransformed[y][u] = DCT_SCALE * c[u] * sum
            }
        }

        val finalDct = DoubleArray(PHASH_SIZE)
        for (u in 0 until DCT_SIZE) {
            for (v in 0 until DCT_SIZE) {
                var sum = 0.0
                for (y in 0 until DOWNSCALE_SIZE) {
                    sum += rowTransformed[y][u] * cos((2 * y + 1) * v * Math.PI / DCT_DENOMINATOR)
                }
                finalDct[v * DCT_SIZE + u] = DCT_SCALE * c[v] * sum
            }
        }

        val acValues = mutableListOf<Double>()
        for (i in 1 until PHASH_SIZE) {
            acValues.add(finalDct[i])
        }
        acValues.sort()
        val median = acValues[acValues.size / 2]

        var hash = 0L
        for (i in 0 until PHASH_SIZE) {
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
        private const val TIMEOUT_US = 10000L
        private const val US_PER_MS = 1000L
        private const val DOWNSCALE_SIZE = 32
        private const val DCT_SIZE = 8
        private const val PHASH_SIZE = 64
        private const val FIXED_POINT_SHIFT = 16
        private const val MAX_LUMA = 255.0
        private const val DCT_SCALE = 0.25
        private const val PIXEL_MASK = 0xFF
        private const val STEP_X = 10
        private const val STEP_Y = 10
        private const val LAPLACIAN_CENTER_WEIGHT = 4
        private const val DCT_DENOMINATOR = 2.0 * DOWNSCALE_SIZE
        private const val NO_FLAGS = 0
        private const val MIN_COUNT = 0
    }
}
