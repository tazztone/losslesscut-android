package com.tazztone.losslesscut.engine.muxing

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class MuxerWriterTest {

    private lateinit var mockMuxer: MediaMuxer
    private lateinit var muxerWriter: MuxerWriter

    @Before
    fun setup() {
        mockMuxer = mockk(relaxed = true)
        muxerWriter = MuxerWriter(mockMuxer)
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun teardown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `addTrack delegates to muxer when not started`() {
        val format = mockk<MediaFormat>()
        every { mockMuxer.addTrack(format) } returns 1

        val trackIndex = muxerWriter.addTrack(format)

        assertEquals(1, trackIndex)
        verify { mockMuxer.addTrack(format) }
    }

    @Test
    fun `addTrack throws IllegalStateException when already started`() {
        muxerWriter.start()

        val format = mockk<MediaFormat>()

        val exception = assertThrows(IllegalStateException::class.java) {
            muxerWriter.addTrack(format)
        }
        assertEquals("Cannot add track after muxer started", exception.message)
    }

    @Test
    fun `setOrientationHint delegates to muxer when not started`() {
        muxerWriter.setOrientationHint(90)
        verify { mockMuxer.setOrientationHint(90) }
    }

    @Test
    fun `setOrientationHint throws IllegalStateException when already started`() {
        muxerWriter.start()

        val exception = assertThrows(IllegalStateException::class.java) {
            muxerWriter.setOrientationHint(90)
        }
        assertEquals("Cannot set orientation after muxer started", exception.message)
    }

    @Test
    fun `start delegates to muxer only once`() {
        muxerWriter.start()
        muxerWriter.start() // Second call should be ignored

        verify(exactly = 1) { mockMuxer.start() }
    }

    @Test
    fun `writeSampleData delegates to muxer when started`() {
        muxerWriter.start()

        val buffer = mockk<ByteBuffer>()
        val bufferInfo = MediaCodec.BufferInfo()

        muxerWriter.writeSampleData(0, buffer, bufferInfo)

        verify { mockMuxer.writeSampleData(0, buffer, bufferInfo) }
    }

    @Test
    fun `writeSampleData throws IllegalStateException when not started`() {
        val buffer = mockk<ByteBuffer>()
        val bufferInfo = MediaCodec.BufferInfo()

        val exception = assertThrows(IllegalStateException::class.java) {
            muxerWriter.writeSampleData(0, buffer, bufferInfo)
        }
        assertEquals("Muxer must be started before writing sample data", exception.message)
    }

    @Test
    fun `stopAndRelease stops and releases muxer when started`() {
        muxerWriter.start()

        muxerWriter.stopAndRelease()

        verify { mockMuxer.stop() }
        verify { mockMuxer.release() }
    }

    @Test
    fun `stopAndRelease only releases muxer when not started`() {
        muxerWriter.stopAndRelease()

        verify(exactly = 0) { mockMuxer.stop() }
        verify { mockMuxer.release() }
    }

    @Test
    fun `stopAndRelease catches exception on stop and proceeds to release`() {
        muxerWriter.start()

        // Simulating the MediaMuxer throwing IllegalStateException on stop
        every { mockMuxer.stop() } throws IllegalStateException("Already stopped")

        muxerWriter.stopAndRelease()

        verify { mockMuxer.stop() }
        verify { mockMuxer.release() }
        verify { Log.e(any(), "Muxer stop failed, likely already stopped or released", any<IllegalStateException>()) }
    }

    @Test
    fun `stopAndRelease catches exception on release`() {
        muxerWriter.start()

        every { mockMuxer.release() } throws IllegalStateException("Release failed")

        // Should not throw
        muxerWriter.stopAndRelease()

        verify { mockMuxer.stop() }
        verify { mockMuxer.release() }
        verify { Log.e(any(), "Muxer release failed", any<IllegalStateException>()) }
    }
}
