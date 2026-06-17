package com.tazztone.losslesscut.engine

import android.content.ContentResolver
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.tazztone.losslesscut.domain.engine.IMediaFinalizer
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.engine.muxing.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileDescriptor
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LosslessEngineImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val mediaFinalizer = mockk<IMediaFinalizer>(relaxed = true)
    private val dataSource = mockk<MediaDataSource>(relaxed = true)
    private val inspector = mockk<TrackInspector>(relaxed = true)
    private val timeMapper = SampleTimeMapper()
    private val mergeValidator = MergeValidator()
    
    private lateinit var collaborators: EngineCollaborators
    private lateinit var engine: LosslessEngineImpl

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        collaborators = EngineCollaborators(dataSource, inspector, timeMapper, mergeValidator)
        engine = LosslessEngineImpl(context, mediaFinalizer, collaborators, Dispatchers.Unconfined)
        
        mockkConstructor(MediaExtractor::class)
        mockkConstructor(MediaMuxer::class)
        mockkConstructor(MediaMetadataRetriever::class)
        mockkConstructor(MuxerWriter::class)
        mockkConstructor(ExtractorSampleCopier::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetMediaMetadata() = runBlocking {
        val uri = "test_uri"
        
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(any<Int>()) } returns "1000"
        
        every { anyConstructed<MediaExtractor>().trackCount } returns 1
        val format = mockk<MediaFormat>()
        every { anyConstructed<MediaExtractor>().getTrackFormat(any<Int>()) } returns format
        every { format.getString(any<String>()) } returns "video/avc"
        every { format.containsKey(any<String>()) } returns true
        every { format.getInteger(any<String>()) } returns 30
        
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<Context>(), any<Uri>()) } returns Unit
        every { anyConstructed<MediaExtractor>().setDataSource(any<Context>(), any<Uri>(), any()) } returns Unit

        val result = engine.getMediaMetadata(uri)
        assertTrue(result.isSuccess)
        val meta = result.getOrNull()!!
        assertEquals(1000L, meta.durationMs)
        assertEquals(30f, meta.fps)
    }

    @Test
    fun testExecuteLosslessCut() = runBlocking {
        val inputUri = "input_uri"
        val outputUri = "output_uri"
        
        val mockPfd = mockk<android.os.ParcelFileDescriptor>(relaxed = true)
        every { contentResolver.openFileDescriptor(any<Uri>(), any<String>()) } returns mockPfd
        every { mockPfd.fileDescriptor } returns FileDescriptor()
        
        val plan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0),
            isVideoTrackMap = mapOf(0 to true),
            bufferSize = 1024,
            durationUs = 1000000L,
            hasVideoTrack = true
        )
        every { inspector.inspect(any<MediaExtractor>(), any<MuxerWriter>(), any<Boolean>(), any<Boolean>(), any()) } returns plan
        
        // Mock MuxerWriter methods to avoid calling real MediaMuxer
        every { anyConstructed<MuxerWriter>().start() } returns Unit
        every { anyConstructed<MuxerWriter>().stopAndRelease() } returns Unit
        every { anyConstructed<MuxerWriter>().addTrack(any<MediaFormat>()) } returns 0
        every { anyConstructed<MuxerWriter>().writeSampleData(any<Int>(), any<ByteBuffer>(), any<MediaCodec.BufferInfo>()) } returns Unit

        // Mock ExtractorSampleCopier to bypass MediaExtractor loop issues
        coEvery { 
            anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any(), any()) 
        } returns mapOf(0 to 1000000L)

        // Mock simple extractor calls that happen before copier
        every { anyConstructed<MediaExtractor>().setDataSource(any<Context>(), any<Uri>(), any()) } returns Unit
        every { anyConstructed<MediaExtractor>().setDataSource(any<String>()) } returns Unit
        
        val result = engine.executeLosslessCut(inputUri, outputUri, 0, 1000, true, true, null, null)
        assertTrue(result.isSuccess)
        verify { anyConstructed<MuxerWriter>().start() }
        verify { mediaFinalizer.finalizeVideo(outputUri) }
    }

    @Test
    fun testExecuteLosslessMerge() = runBlocking {
        val outputUri = "output_uri"
        val clip = MediaClip(
            uri = "clip_uri", fileName = "test.mp4", durationMs = 1000,
            width = 1920, height = 1080, videoMime = "video/avc", audioMime = null,
            sampleRate = 0, channelCount = 0, fps = 30f, rotation = 0, isAudioOnly = false,
            segments = listOf(TrimSegment(startMs = 0, endMs = 1000))
        )
        
        val mockPfd = mockk<android.os.ParcelFileDescriptor>(relaxed = true)
        every { contentResolver.openFileDescriptor(any<Uri>(), any<String>()) } returns mockPfd
        
        val plan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0),
            isVideoTrackMap = mapOf(0 to true),
            bufferSize = 1024,
            durationUs = 1000000L,
            hasVideoTrack = true
        )
        every { inspector.inspect(any<MediaExtractor>(), any<MuxerWriter>(), any<Boolean>(), any<Boolean>(), any()) } returns plan
        
        // Mock MuxerWriter methods
        every { anyConstructed<MuxerWriter>().start() } returns Unit
        every { anyConstructed<MuxerWriter>().stopAndRelease() } returns Unit
        every { anyConstructed<MuxerWriter>().addTrack(any<MediaFormat>()) } returns 0
        every { anyConstructed<MuxerWriter>().writeSampleData(any<Int>(), any<ByteBuffer>(), any<MediaCodec.BufferInfo>()) } returns Unit

        // Mock ExtractorSampleCopier
        coEvery { 
            anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any(), any()) 
        } returns mapOf(0 to 1000000L)

        every { anyConstructed<MediaExtractor>().trackCount } returns 1
        val format = mockk<MediaFormat>(relaxed = true)
        every { anyConstructed<MediaExtractor>().getTrackFormat(any<Int>()) } returns format
        every { format.getString(any<String>()) } returns "video/avc"
        every { anyConstructed<MediaExtractor>().setDataSource(any<Context>(), any<Uri>(), any()) } returns Unit

        val result = engine.executeLosslessMerge(outputUri, listOf(clip), true, true, null, null)
        assertTrue(result.isSuccess)
        verify { anyConstructed<MuxerWriter>().start() }
        verify { mediaFinalizer.finalizeVideo(outputUri) }
    }

    @Test
    fun testExecuteLosslessMergeWithMetadataAndSelectedTracks() = runBlocking {
        val outputUri = "output_uri"
        val clip = MediaClip(
            uri = "clip_uri", fileName = "test.mp4", durationMs = 1000,
            width = 1920, height = 1080, videoMime = "video/avc", audioMime = "audio/mp4a-latm",
            sampleRate = 44100, channelCount = 2, fps = 30f, rotation = 0, isAudioOnly = false,
            segments = listOf(TrimSegment(startMs = 0, endMs = 1000))
        )
        
        val mockPfd = mockk<android.os.ParcelFileDescriptor>(relaxed = true)
        every { contentResolver.openFileDescriptor(any<Uri>(), any<String>()) } returns mockPfd
        
        val plan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0, 1 to 1, 2 to 2),
            isVideoTrackMap = mapOf(0 to true, 1 to false, 2 to false),
            bufferSize = 1024,
            durationUs = 1000000L,
            hasVideoTrack = true
        )
        every { inspector.inspect(any<MediaExtractor>(), any<MuxerWriter>(), any<Boolean>(), any<Boolean>(), any()) } returns plan
        
        // Mock MuxerWriter methods
        every { anyConstructed<MuxerWriter>().start() } returns Unit
        every { anyConstructed<MuxerWriter>().stopAndRelease() } returns Unit
        every { anyConstructed<MuxerWriter>().addTrack(any<MediaFormat>()) } returns 0
        every { anyConstructed<MuxerWriter>().writeSampleData(any<Int>(), any<ByteBuffer>(), any<MediaCodec.BufferInfo>()) } returns Unit

        // Mock ExtractorSampleCopier
        coEvery { 
            anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any(), any()) 
        } returns mapOf(0 to 1000000L, 1 to 1000000L, 2 to 1000000L)

        // The media file has 3 tracks: video, audio, and metadata (application/octet-stream)
        every { anyConstructed<MediaExtractor>().trackCount } returns 3
        
        val videoFormat = mockk<MediaFormat>(relaxed = true)
        every { videoFormat.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        
        val audioFormat = mockk<MediaFormat>(relaxed = true)
        every { audioFormat.getString(MediaFormat.KEY_MIME) } returns "audio/mp4a-latm"
        every { audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) } returns true
        every { audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) } returns 44100
        
        val metaFormat = mockk<MediaFormat>(relaxed = true)
        every { metaFormat.getString(MediaFormat.KEY_MIME) } returns "application/octet-stream"

        every { anyConstructed<MediaExtractor>().getTrackFormat(0) } returns videoFormat
        every { anyConstructed<MediaExtractor>().getTrackFormat(1) } returns audioFormat
        every { anyConstructed<MediaExtractor>().getTrackFormat(2) } returns metaFormat
        
        every { anyConstructed<MediaExtractor>().setDataSource(any<Context>(), any<Uri>(), any()) } returns Unit

        val result = engine.executeLosslessMerge(
            outputUri, listOf(clip), keepAudio = true, keepVideo = true, 
            rotationOverride = null, selectedTracks = listOf(0, 1, 2)
        )
        
        assertTrue(result.isSuccess)
        verify { anyConstructed<MuxerWriter>().start() }
        verify { mediaFinalizer.finalizeVideo(outputUri) }
    }
}
