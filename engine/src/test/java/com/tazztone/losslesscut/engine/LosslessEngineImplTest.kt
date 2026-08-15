package com.tazztone.losslesscut.engine

import android.content.ContentResolver
import android.content.Context
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
    
    private val muxingPipeline = mockk<MuxingPipeline>(relaxed = true)
    
    private lateinit var engine: LosslessEngineImpl

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        engine = LosslessEngineImpl(
            dataSource, muxingPipeline, Dispatchers.Unconfined
        )
        
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
        val uri = "content://media/external/video/1"
        
        val retrieverMeta = LosslessEngineHelper.BasicMeta(1000L, 1920, 1080, 0)
        mockkObject(LosslessEngineHelper)
        every { LosslessEngineHelper.readBasicMetadata(any(), any()) } returns retrieverMeta
        every { LosslessEngineHelper.readTrackMetadata(any()) } returns LosslessEngineHelper.TrackData(
            "video/avc", "audio/mp4a-latm", 44100, 2, 30f, emptyList()
        )
        
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

        coEvery { muxingPipeline.executeCut(any()) } returns Result.success(outputUri)

        val result = engine.executeLosslessCut(inputUri, outputUri, 0, 1000, true, true, null, null)
        assertTrue(result.isSuccess)
        coVerify { muxingPipeline.executeCut(any()) }
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

        coEvery { muxingPipeline.executeMerge(any()) } returns Result.success(outputUri)

        val result = engine.executeLosslessMerge(outputUri, listOf(clip), true, true, null, null)
        assertTrue(result.isSuccess)
        coVerify { muxingPipeline.executeMerge(any()) }
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

        coEvery { muxingPipeline.executeMerge(any()) } returns Result.success(outputUri)

        val result = engine.executeLosslessMerge(outputUri, listOf(clip), true, true, 90, listOf(0, 1))
        assertTrue(result.isSuccess)
        coVerify { muxingPipeline.executeMerge(any()) }
    }
}
