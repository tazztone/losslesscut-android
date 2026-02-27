package com.tazztone.losslesscut.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import com.tazztone.losslesscut.domain.model.FrameAnalysis
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
        val sampleIntervalMs: Long,
        val analyses: MutableList<FrameAnalysis> = mutableListOf()
    ) {
        var previousHash: Long? = null
        var previousSmallY: ByteArray? = null

        var lastProcessedUs: Long = -1L
        val sampleIntervalUs: Long = sampleIntervalMs * US_PER_MS
        val info: MediaCodec.BufferInfo = MediaCodec.BufferInfo()

        var sawInputEOS = false
        var sawOutputEOS = false
    }

    override suspend fun analyze(
        uri: String,
        sampleIntervalMs: Long,
        onProgress: (Int, Int) -> Unit
    ): List<FrameAnalysis> = withContext(Dispatchers.Default) {
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
            codec.configure(format, null, null, 0 /* flags */)
            codec.start()

            context = DetectionContext(extractor, codec, sampleIntervalMs)
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

        return@withContext context?.analyses ?: emptyList()
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
            val meanLuma = calculateMeanLuma(outputBuffer, outputFormat, ctx.info)
            val blurVariance = calculateBlurVariance(outputBuffer, outputFormat, ctx.info)
            val currentHash = calculatePHash(outputBuffer, outputFormat, ctx.info)
            val currentSmallY = downscaleY(outputBuffer, outputFormat, ctx.info, DOWNSCALE_SIZE, DOWNSCALE_SIZE)
            
            val sceneDistance = if (ctx.previousHash != null) {
                java.lang.Long.bitCount(currentHash xor ctx.previousHash!!)
            } else null
            
            val freezeDiff = if (ctx.previousSmallY != null) {
                calculateSAD(currentSmallY, ctx.previousSmallY!!)
            } else null
            
            ctx.analyses.add(FrameAnalysis(
                timeMs = ctx.info.presentationTimeUs / US_PER_MS,
                meanLuma = meanLuma,
                blurVariance = blurVariance,
                sceneDistance = sceneDistance,
                freezeDiff = freezeDiff
            ))

            ctx.previousHash = currentHash
            ctx.previousSmallY = currentSmallY
        }
    }

    @Suppress("MagicNumber")
    private fun calculateMeanLuma(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo): Double {
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

        return if (count > MIN_COUNT) sum.toDouble() / count else MAX_LUMA
    }

    private fun calculateBlurVariance(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo): Double {
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

        if (count == 0) return 0.0
        val mean = sumVar / count
        return (sumSqVar / count) - (mean * mean)
    }

    private fun calculateSAD(current: ByteArray, previous: ByteArray): Double {
        val size = current.size.coerceAtMost(previous.size)
        if (size == 0) return 0.0

        var sad = 0L
        for (i in 0 until size) {
            sad += abs((current[i].toInt() and PIXEL_MASK) - (previous[i].toInt() and PIXEL_MASK))
        }
        return sad.toDouble() / size
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


    companion object {
        private const val TAG = "VisualSegmentDetector"
        private const val TIMEOUT_US = 10000L
        private const val US_PER_MS = 1000L
        private const val DOWNSCALE_SIZE = 32
        private const val DCT_SIZE = 8 // Look for 8x8 low frequency patterns
        private const val PHASH_SIZE = 64
        private const val FIXED_POINT_SHIFT = 16
        private const val MAX_LUMA = 255.0
        
        // DCT scaling factor for 2D transformation
        private const val DCT_SCALE = 0.25
        
        private const val PIXEL_MASK = 0xFF
        private const val STEP_X = 10
        private const val STEP_Y = 10
        
        // Neighborhood weight for laplacian blur detection
        private const val LAPLACIAN_CENTER_WEIGHT = 4
        
        private const val DCT_DENOMINATOR = 2.0 * DOWNSCALE_SIZE
        private const val MIN_COUNT = 0
    }
}
