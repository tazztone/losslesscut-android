package com.tazztone.losslesscut.engine

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

internal object VisualAlgorithms {
    private const val DOWNSCALE_SIZE = 32
    private const val DCT_SIZE = 8
    private const val PHASH_SIZE = 64
    private const val PIXEL_MASK = 0xFF
    private const val STEP_X = 10
    private const val STEP_Y = 10
    private const val LAPLACIAN_CENTER_WEIGHT = 4
    private const val DCT_SCALE = 0.25
    private const val MAX_LUMA = 255.0
    private const val NORM_OFFSET = 0.05
    private const val DCT_DENOMINATOR = 2.0 * DOWNSCALE_SIZE
    private const val BLUR_TARGET_WIDTH = 256

    private val DCT_COSINE_TABLE = Array(DOWNSCALE_SIZE) { pos ->
        DoubleArray(DCT_SIZE) { freq ->
            cos((2 * pos + 1) * freq * Math.PI / DCT_DENOMINATOR)
        }
    }

    private val DCT_C = DoubleArray(DOWNSCALE_SIZE).apply {
        this[0] = 1.0 / sqrt(2.0)
        for (i in 1 until DOWNSCALE_SIZE) this[i] = 1.0
    }

    private class PHashContext {
        val rowTransformed = Array(DOWNSCALE_SIZE) { DoubleArray(DCT_SIZE) }
        val finalDct = DoubleArray(PHASH_SIZE)
        val acValues = DoubleArray(PHASH_SIZE - 1)
    }

    private val pHashContext = object : ThreadLocal<PHashContext>() {
        override fun initialValue() = PHashContext()
    }

    fun calculateMeanLuma(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo): Double {
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val stride = if (format.containsKey(MediaFormat.KEY_STRIDE)) format.getInteger(MediaFormat.KEY_STRIDE) else width

        val limit = buffer.limit()
        var sum = 0L
        var count = 0
        for (y in 0 until height step STEP_Y) {
            val rowStart = info.offset + y * stride
            if (rowStart >= limit) break
            for (x in 0 until width step STEP_X) {
                val idx = rowStart + x
                if (idx < limit) {
                    sum += buffer.get(idx).toInt() and PIXEL_MASK
                    count++
                }
            }
        }

        if (count == 0) return MAX_LUMA
        return sum.toDouble() / count
    }

    fun calculateBlurVariance(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo): Double {
        val targetW = BLUR_TARGET_WIDTH
        val result = downscaleY(buffer, format, info, targetW, -1)
        val downscaled = result.data
        val w = result.width
        val h = result.height

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
        val rawVar = (sumSqVar / count) - (mean * mean)

        var lumaSum = 0.0
        for (i in 0 until (w * h)) {
            lumaSum += downscaled[i].toInt() and PIXEL_MASK
        }
        val meanLuma = lumaSum / (w * h)
        val lumaNorm = (meanLuma / MAX_LUMA).pow(2) + NORM_OFFSET
        return rawVar / lumaNorm
    }

    fun calculateSAD(current: ByteArray, previous: ByteArray): Double {
        val size = current.size.coerceAtMost(previous.size)
        if (size == 0) return 0.0

        var sad = 0L
        for (i in 0 until size) {
            sad += abs((current[i].toInt() and PIXEL_MASK) - (previous[i].toInt() and PIXEL_MASK))
        }
        return sad.toDouble() / size
    }

    fun calculatePHash(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo): Long {
        val small = downscaleY(buffer, format, info, DOWNSCALE_SIZE, DOWNSCALE_SIZE).data

        val ctx = pHashContext.get()!!
        val rowTransformed = ctx.rowTransformed
        val finalDct = ctx.finalDct
        val acValues = ctx.acValues

        for (y in 0 until DOWNSCALE_SIZE) {
            for (u in 0 until DCT_SIZE) {
                var sum = 0.0
                for (x in 0 until DOWNSCALE_SIZE) {
                     val pixelVal = (small[y * DOWNSCALE_SIZE + x].toInt() and PIXEL_MASK).toDouble()
                     sum += pixelVal * DCT_COSINE_TABLE[x][u]
                }
                rowTransformed[y][u] = DCT_SCALE * DCT_C[u] * sum
            }
        }

        for (u in 0 until DCT_SIZE) {
            for (v in 0 until DCT_SIZE) {
                var sum = 0.0
                for (y in 0 until DOWNSCALE_SIZE) {
                    sum += rowTransformed[y][u] * DCT_COSINE_TABLE[y][v]
                }
                finalDct[v * DCT_SIZE + u] = DCT_SCALE * DCT_C[v] * sum
            }
        }

        for (i in 1 until PHASH_SIZE) {
            acValues[i - 1] = finalDct[i]
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

    fun downscaleY(
        buffer: ByteBuffer, 
        format: MediaFormat, 
        info: MediaCodec.BufferInfo, 
        targetW: Int, 
        targetH: Int
    ): DownscaleResult {
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val stride = if (format.containsKey(MediaFormat.KEY_STRIDE)) format.getInteger(MediaFormat.KEY_STRIDE) else width

        val h = if (targetH == -1) (height * targetW / width) else targetH
        val finalH = if (h < 1) 1 else h

        val out = ByteArray(targetW * finalH)
        val limit = buffer.limit()
        val stepX = (width.toFloat() / targetW).coerceAtLeast(1.0f)
        val stepY = (height.toFloat() / finalH).coerceAtLeast(1.0f)

        for (y in 0 until finalH) {
            val srcY0 = (y * stepY).toInt().coerceIn(0, height - 1)
            val srcY1 = ((y + 1) * stepY).toInt().coerceIn(srcY0 + 1, height)
            val yRange = srcY0 until srcY1
            for (x in 0 until targetW) {
                val srcX0 = (x * stepX).toInt().coerceIn(0, width - 1)
                val srcX1 = ((x + 1) * stepX).toInt().coerceIn(srcX0 + 1, width)
                out[y * targetW + x] = calculateBoxAverage(buffer, info, stride, srcX0 until srcX1, yRange)
            }
        }
        return DownscaleResult(out, targetW, finalH)
    }

    private fun calculateBoxAverage(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        stride: Int,
        xRange: IntRange,
        yRange: IntRange
    ): Byte {
        var sum = 0
        var pixelCount = 0
        val limit = buffer.limit()
        for (sy in yRange) {
            val rowOffset = info.offset + sy * stride
            for (sx in xRange) {
                val offset = rowOffset + sx
                if (offset < limit) {
                    sum += buffer.get(offset).toInt() and PIXEL_MASK
                    pixelCount++
                }
            }
        }
        val avg = if (pixelCount > 0) (sum / pixelCount) else 0
        return avg.toByte()
    }

    data class DownscaleResult(val data: ByteArray, val width: Int, val height: Int)
}
