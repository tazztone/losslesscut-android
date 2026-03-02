package com.tazztone.losslesscut.engine

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

internal object VisualAlgorithms {
    private const val DOWNSCALE_SIZE = 32
    private const val DCT_SIZE = 8
    private const val PHASH_SIZE = 64
    private const val FIXED_POINT_SHIFT = 16
    private const val PIXEL_MASK = 0xFF
    private const val STEP_X = 10
    private const val STEP_Y = 10
    private const val LAPLACIAN_CENTER_WEIGHT = 4
    private const val DCT_SCALE = 0.25
    private const val MAX_LUMA = 255.0
    private const val DCT_DENOMINATOR = 2.0 * DOWNSCALE_SIZE
    private const val BLUR_TARGET_WIDTH = 256

    fun calculateMeanLuma(buffer: ByteBuffer, format: MediaFormat, info: MediaCodec.BufferInfo): Double {
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

        return if (count > 0) sum.toDouble() / count else MAX_LUMA
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
        return (sumSqVar / count) - (mean * mean)
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
        return DownscaleResult(out, targetW, finalH)
    }

    data class DownscaleResult(val data: ByteArray, val width: Int, val height: Int)
}
