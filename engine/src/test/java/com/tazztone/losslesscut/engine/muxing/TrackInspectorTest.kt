package com.tazztone.losslesscut.engine.muxing

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
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
        
        val videoFormat = mockk<MediaFormat>()
        every { videoFormat.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        every { videoFormat.containsKey(MediaFormat.KEY_DURATION) } returns true
        every { videoFormat.getLong(MediaFormat.KEY_DURATION) } returns 10_000_000L
        every { videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) } returns true
        every { videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) } returns 512 * 1024

        val audioFormat = mockk<MediaFormat>()
        every { audioFormat.getString(MediaFormat.KEY_MIME) } returns "audio/mp4a-latm"
        every { audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) } returns false

        every { extractor.trackCount } returns 2
        every { extractor.getTrackFormat(0) } returns videoFormat
        every { extractor.getTrackFormat(1) } returns audioFormat
        
        every { muxerWriter.addTrack(videoFormat) } returns 0
        every { muxerWriter.addTrack(audioFormat) } returns 1

        val inspector = TrackInspector()
        val plan = inspector.inspect(extractor, muxerWriter, keepAudio = true, keepVideo = true, selectedTracks = null)

        assertEquals(2, plan.trackMap.size)
        assertEquals(0, plan.trackMap[0])
        assertEquals(1, plan.trackMap[1])
        assertTrue(plan.isVideoTrackMap[0] == true)
        assertTrue(plan.isVideoTrackMap[1] == false)
        assertEquals(512 * 1024, plan.bufferSize)
        assertEquals(10_000_000L, plan.durationUs)
        assertTrue(plan.hasVideoTrack)
    }

    @Test
    fun `inspect respects keepAudio and keepVideo flags`() {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>()
        
        val videoFormat = mockk<MediaFormat>()
        every { videoFormat.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        every { videoFormat.containsKey(MediaFormat.KEY_DURATION) } returns true
        every { videoFormat.getLong(MediaFormat.KEY_DURATION) } returns 10_000_000L
        every { videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) } returns false

        val audioFormat = mockk<MediaFormat>()
        every { audioFormat.getString(MediaFormat.KEY_MIME) } returns "audio/mp4a-latm"
        every { audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) } returns false

        every { extractor.trackCount } returns 2
        every { extractor.getTrackFormat(0) } returns videoFormat
        every { extractor.getTrackFormat(1) } returns audioFormat
        
        every { muxerWriter.addTrack(videoFormat) } returns 0

        val inspector = TrackInspector()
        val plan = inspector.inspect(extractor, muxerWriter, keepAudio = false, keepVideo = true, selectedTracks = null)

        assertEquals(1, plan.trackMap.size)
        assertEquals(0, plan.trackMap[0])
        assertFalse(plan.trackMap.containsKey(1))
        assertTrue(plan.hasVideoTrack)
    }

    @Test
    fun `inspect respects selectedTracks list`() {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>()
        
        val videoFormat = mockk<MediaFormat>()
        every { videoFormat.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        every { videoFormat.containsKey(MediaFormat.KEY_DURATION) } returns true
        every { videoFormat.getLong(MediaFormat.KEY_DURATION) } returns 10_000_000L
        every { videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) } returns false

        val audioFormat = mockk<MediaFormat>()
        every { audioFormat.getString(MediaFormat.KEY_MIME) } returns "audio/mp4a-latm"
        every { audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) } returns false

        every { extractor.trackCount } returns 2
        every { extractor.getTrackFormat(0) } returns videoFormat
        every { extractor.getTrackFormat(1) } returns audioFormat
        
        every { muxerWriter.addTrack(audioFormat) } returns 0

        val inspector = TrackInspector()
        val plan = inspector.inspect(extractor, muxerWriter, keepAudio = true, keepVideo = true, selectedTracks = listOf(1))

        assertEquals(1, plan.trackMap.size)
        assertEquals(0, plan.trackMap[1])
        assertFalse(plan.trackMap.containsKey(0))
        assertFalse(plan.hasVideoTrack)
    }
}
