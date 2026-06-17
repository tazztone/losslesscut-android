package com.tazztone.losslesscut.engine.muxing

import android.media.MediaExtractor
import android.media.MediaFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackInspectorTest {

    @Test
    fun `inspect selects video and audio tracks by default`() {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>()
        
        val videoFormat = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        videoFormat.setLong(MediaFormat.KEY_DURATION, 10_000_000L)
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 512 * 1024)

        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2)
        // No KEY_MAX_INPUT_SIZE

        every { extractor.trackCount } returns 2
        every { extractor.getTrackFormat(0) } returns videoFormat
        every { extractor.getTrackFormat(1) } returns audioFormat
        
        // Use matchers to return different indices
        every { muxerWriter.addTrack(match { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }) } returns 0
        every { muxerWriter.addTrack(match { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }) } returns 1

        val inspector = TrackInspector()
        val plan = inspector.inspect(extractor, muxerWriter, keepAudio = true, keepVideo = true, selectedTracks = null)

        assertEquals(2, plan.trackMap.size)
        assertEquals(0, plan.trackMap[0])
        assertEquals(1, plan.trackMap[1])
        
        assertEquals(512 * 1024, plan.bufferSize)
        assertEquals(10_000_000L, plan.durationUs)
        assertTrue(plan.hasVideoTrack)
        
        verify(exactly = 1) { muxerWriter.addTrack(match { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }) }
        verify(exactly = 1) { muxerWriter.addTrack(match { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }) }
    }

    @Test
    fun `inspect respects keepAudio and keepVideo flags`() {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>()
        
        val videoFormat = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2)

        every { extractor.trackCount } returns 2
        every { extractor.getTrackFormat(0) } returns videoFormat
        every { extractor.getTrackFormat(1) } returns audioFormat
        
        every { muxerWriter.addTrack(any()) } returns 0

        val inspector = TrackInspector()
        val plan = inspector.inspect(extractor, muxerWriter, keepAudio = false, keepVideo = true, selectedTracks = null)

        assertEquals(1, plan.trackMap.size)
        assertEquals(0, plan.trackMap[0])
        assertFalse(plan.trackMap.containsKey(1))
        assertTrue(plan.hasVideoTrack)
        
        verify(exactly = 1) { muxerWriter.addTrack(any()) }
    }

    @Test
    fun `inspect respects selectedTracks list`() {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>()
        
        val videoFormat = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2)

        every { extractor.trackCount } returns 2
        every { extractor.getTrackFormat(0) } returns videoFormat
        every { extractor.getTrackFormat(1) } returns audioFormat
        
        every { muxerWriter.addTrack(any()) } returns 0

        val inspector = TrackInspector()
        val plan = inspector.inspect(extractor, muxerWriter, keepAudio = true, keepVideo = true, selectedTracks = listOf(1))

        assertEquals(1, plan.trackMap.size)
        assertEquals(0, plan.trackMap[1])
        assertFalse(plan.trackMap.containsKey(0))
        assertFalse(plan.hasVideoTrack)
        
        verify(exactly = 1) { muxerWriter.addTrack(any()) }
    }

    @Test
    fun `inspect preserves color space and HDR metadata keys`() {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>()
        
        val videoFormat = MediaFormat.createVideoFormat("video/dolby-vision", 3840, 2160)
        videoFormat.setLong(MediaFormat.KEY_DURATION, 5_000_000L)
        videoFormat.setInteger("color-standard", MediaFormat.COLOR_STANDARD_BT2020)
        videoFormat.setInteger("color-transfer", MediaFormat.COLOR_TRANSFER_HLG)
        videoFormat.setInteger("color-range", MediaFormat.COLOR_RANGE_LIMITED)
        videoFormat.setInteger("profile", 8)
        videoFormat.setInteger("level", 4)
        
        val staticInfoBuffer = java.nio.ByteBuffer.allocate(10)
        staticInfoBuffer.put(byteArrayOf(1, 2, 3))
        staticInfoBuffer.flip()
        videoFormat.setByteBuffer("hdr-static-info", staticInfoBuffer)
        
        val plusInfoBuffer = java.nio.ByteBuffer.allocate(5)
        plusInfoBuffer.put(byteArrayOf(4, 5))
        plusInfoBuffer.flip()
        videoFormat.setByteBuffer("hdr10-plus-info", plusInfoBuffer)

        every { extractor.trackCount } returns 1
        every { extractor.getTrackFormat(0) } returns videoFormat
        
        val capturedFormat = io.mockk.slot<MediaFormat>()
        every { muxerWriter.addTrack(capture(capturedFormat)) } returns 0

        val inspector = TrackInspector()
        inspector.inspect(extractor, muxerWriter, keepAudio = false, keepVideo = true, selectedTracks = null)

        assertTrue(capturedFormat.isCaptured)
        val clean = capturedFormat.captured
        assertEquals("video/dolby-vision", clean.getString(MediaFormat.KEY_MIME))
        assertEquals(3840, clean.getInteger(MediaFormat.KEY_WIDTH))
        assertEquals(2160, clean.getInteger(MediaFormat.KEY_HEIGHT))
        assertEquals(MediaFormat.COLOR_STANDARD_BT2020, clean.getInteger("color-standard"))
        assertEquals(MediaFormat.COLOR_TRANSFER_HLG, clean.getInteger("color-transfer"))
        assertEquals(MediaFormat.COLOR_RANGE_LIMITED, clean.getInteger("color-range"))
        assertEquals(8, clean.getInteger("profile"))
        assertEquals(4, clean.getInteger("level"))
        assertTrue(clean.getByteBuffer("hdr-static-info") != null)
        assertTrue(clean.getByteBuffer("hdr10-plus-info") != null)
    }

    @Test
    fun `inspect handles float frame rate format safely`() {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>()
        
        val videoFormat = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        videoFormat.setLong(MediaFormat.KEY_DURATION, 5_000_000L)
        // Store frame rate as Float
        videoFormat.setFloat(MediaFormat.KEY_FRAME_RATE, 29.97f)

        every { extractor.trackCount } returns 1
        every { extractor.getTrackFormat(0) } returns videoFormat
        
        val capturedFormat = io.mockk.slot<MediaFormat>()
        every { muxerWriter.addTrack(capture(capturedFormat)) } returns 0

        val inspector = TrackInspector()
        inspector.inspect(extractor, muxerWriter, keepAudio = false, keepVideo = true, selectedTracks = null)

        assertTrue(capturedFormat.isCaptured)
        val clean = capturedFormat.captured
        assertEquals(29.97f, clean.getFloat(MediaFormat.KEY_FRAME_RATE), 0.001f)
    }

    @Test
    fun `inspect handles invalid metadata types safely without crashing`() {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>()
        
        val videoFormat = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        videoFormat.setLong(MediaFormat.KEY_DURATION, 5_000_000L)
        
        // Put mismatched type: profile and color-standard as strings/floats instead of ints
        videoFormat.setString("profile", "high")
        videoFormat.setFloat("color-standard", 1.0f)

        every { extractor.trackCount } returns 1
        every { extractor.getTrackFormat(0) } returns videoFormat
        
        val capturedFormat = io.mockk.slot<MediaFormat>()
        every { muxerWriter.addTrack(capture(capturedFormat)) } returns 0

        val inspector = TrackInspector()
        // Should not throw NullPointerException or ClassCastException
        inspector.inspect(extractor, muxerWriter, keepAudio = false, keepVideo = true, selectedTracks = null)

        assertTrue(capturedFormat.isCaptured)
    }

    @Test
    fun `inspect handles generic metadata data tracks safely`() {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>()
        
        val metaFormat = MediaFormat()
        metaFormat.setString(MediaFormat.KEY_MIME, "application/x-quicktime-metadata")

        every { extractor.trackCount } returns 1
        every { extractor.getTrackFormat(0) } returns metaFormat
        
        val capturedFormat = io.mockk.slot<MediaFormat>()
        every { muxerWriter.addTrack(capture(capturedFormat)) } returns 0

        val inspector = TrackInspector()
        inspector.inspect(extractor, muxerWriter, keepAudio = true, keepVideo = true, selectedTracks = listOf(0))

        assertTrue(capturedFormat.isCaptured)
        val clean = capturedFormat.captured
        assertEquals("application/x-quicktime-metadata", clean.getString(MediaFormat.KEY_MIME))
    }
}
