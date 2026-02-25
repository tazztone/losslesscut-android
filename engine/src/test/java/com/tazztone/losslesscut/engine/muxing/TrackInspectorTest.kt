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
}
