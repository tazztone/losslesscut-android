package com.tazztone.losslesscut.engine

import android.media.MediaCodec
import android.media.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VisualAlgorithmsTest {

    @Test
    fun calculateMeanLuma_withUniformBuffer_returnsCorrectValue() {
        val width = 100
        val height = 100
        val lumaValue = 128.toByte()
        val buffer = ByteBuffer.allocate(width * height)
        for (i in 0 until width * height) buffer.put(lumaValue)
        buffer.flip()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        val info = MediaCodec.BufferInfo().apply {
            offset = 0
            size = width * height
            presentationTimeUs = 0
        }

        val meanLuma = VisualAlgorithms.calculateMeanLuma(buffer, format, info)
        assertEquals(128.0, meanLuma, 0.1)
    }

    @Test
    fun calculateSAD_withIdenticalBuffers_returnsZero() {
        val b1 = byteArrayOf(10, 20, 30)
        val b2 = byteArrayOf(10, 20, 30)
        val sad = VisualAlgorithms.calculateSAD(b1, b2)
        assertEquals(0.0, sad, 0.0)
    }

    @Test
    fun calculateSAD_withDifferentBuffers_returnsCorrectValue() {
        val b1 = byteArrayOf(10, 20, 30)
        val b2 = byteArrayOf(15, 25, 35)
        val sad = VisualAlgorithms.calculateSAD(b1, b2)
        assertEquals(5.0, sad, 0.0)
    }

    @Test
    fun calculatePHash_isStableForIdenticalInput() {
        val width = 64
        val height = 64
        val buffer = ByteBuffer.allocate(width * height)
        for (i in 0 until width * height) {
            buffer.put((i % 256).toByte())
        }
        buffer.flip()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        val info = MediaCodec.BufferInfo().apply { offset = 0; size = width * height }

        val hash1 = VisualAlgorithms.calculatePHash(buffer, format, info)
        buffer.rewind()
        val hash2 = VisualAlgorithms.calculatePHash(buffer, format, info)

        assertEquals(hash1, hash2)
    }

    @Test
    fun calculateBlurVariance_withSolidBuffer_returnsLowVariance() {
        val width = 300
        val height = 300
        val buffer = ByteBuffer.allocate(width * height)
        for (i in 0 until width * height) buffer.put(128.toByte())
        buffer.flip()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        val info = MediaCodec.BufferInfo().apply { offset = 0; size = width * height }

        val variance = VisualAlgorithms.calculateBlurVariance(buffer, format, info)
        assertEquals(0.0, variance, 0.1)
    }
}
