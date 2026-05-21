package com.tazztone.losslesscut.engine

import android.media.MediaCodec
import android.media.MediaFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VisualAlgorithmsBenchmarkTest {
    @Test
    fun calculatePHash_benchmark() {
        val width = 64
        val height = 64
        val buffer = ByteBuffer.allocate(width * height)
        for (i in 0 until width * height) {
            buffer.put((i % 256).toByte())
        }
        buffer.flip()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        val info = MediaCodec.BufferInfo().apply { offset = 0; size = width * height }

        // Warm up
        for(i in 0 until 100) {
            buffer.rewind()
            VisualAlgorithms.calculatePHash(buffer, format, info)
        }

        val start = System.currentTimeMillis()
        for(i in 0 until 5000) {
            buffer.rewind()
            VisualAlgorithms.calculatePHash(buffer, format, info)
        }
        val end = System.currentTimeMillis()
        println("calculatePHash_benchmark took ${end - start} ms")
    }
}
