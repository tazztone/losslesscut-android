package com.tazztone.losslesscut.engine.muxing

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tazztone.losslesscut.domain.engine.IMediaFinalizer
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.TrimSegment
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
public class MuxingPipelineTest {

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val dataSource = mockk<MediaDataSource>(relaxed = true)
    private val inspector = mockk<TrackInspector>(relaxed = true)
    private val timeMapper = SampleTimeMapper()
    private val mergeValidator = mockk<MergeValidator>(relaxed = true)
    private val mediaFinalizer = mockk<IMediaFinalizer>(relaxed = true)
    private lateinit var pipeline: MuxingPipeline
    private var tempFile: File? = null

    @Before
    public fun setUp() {
        every { context.contentResolver } returns contentResolver
        mockkConstructor(MediaExtractor::class)
        mockkConstructor(MediaMuxer::class)
        mockkConstructor(MuxerWriter::class)
        mockkConstructor(ExtractorSampleCopier::class)

        every { anyConstructed<MediaExtractor>().selectTrack(any()) } just Runs
        every { anyConstructed<MediaExtractor>().readSampleData(any(), any()) } returns -1
        every { anyConstructed<MediaExtractor>().seekTo(any(), any()) } just Runs
        every { anyConstructed<MediaExtractor>().advance() } returns false

        pipeline = MuxingPipeline(
            context = context,
            dataSource = dataSource,
            inspector = inspector,
            timeMapper = timeMapper,
            mergeValidator = mergeValidator,
            mediaFinalizer = mediaFinalizer
        )
    }

    @After
    public fun tearDown() {
        unmockkAll()
        tempFile?.delete()
    }

    private fun createTempPfd(): ParcelFileDescriptor {
        val file = File.createTempFile("mux_test_", ".mp4")
        tempFile = file
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
    }

    @Test
    public fun getVideoFpsReturnsExpectedFps() {
        val intFormat = mockk<MediaFormat>()
        every { intFormat.containsKey(MediaFormat.KEY_FRAME_RATE) } returns true
        every { intFormat.getInteger(MediaFormat.KEY_FRAME_RATE) } returns 60
        assertEquals(60f, MuxingPipeline.getVideoFps(intFormat))

        val floatFormat = mockk<MediaFormat>()
        every { floatFormat.containsKey(MediaFormat.KEY_FRAME_RATE) } returns true
        every { floatFormat.getInteger(MediaFormat.KEY_FRAME_RATE) } throws ClassCastException()
        every { floatFormat.getFloat(MediaFormat.KEY_FRAME_RATE) } returns 29.97f
        assertEquals(29.97f, MuxingPipeline.getVideoFps(floatFormat))

        val missingFormat = mockk<MediaFormat>()
        every { missingFormat.containsKey(MediaFormat.KEY_FRAME_RATE) } returns false
        assertEquals(30f, MuxingPipeline.getVideoFps(missingFormat))

        val invalidFormat = mockk<MediaFormat>()
        every { invalidFormat.containsKey(MediaFormat.KEY_FRAME_RATE) } returns true
        every { invalidFormat.getInteger(MediaFormat.KEY_FRAME_RATE) } throws RuntimeException()
        every { invalidFormat.getFloat(MediaFormat.KEY_FRAME_RATE) } throws RuntimeException()
        assertEquals(30f, MuxingPipeline.getVideoFps(invalidFormat))
    }

    @Test
    public fun executeCutFailsWhenEndMsIsBeforeStartMs() = runBlocking {
        val request = MuxingCutRequest(
            inputUri = "content://media/1",
            outputUri = "content://media/2",
            startMs = 5000L,
            endMs = 1000L
        )

        val result = pipeline.executeCut(request)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    public fun executeCutFailsWhenPfdCannotBeOpened() = runBlocking {
        every { contentResolver.openFileDescriptor(any(), "rw") } returns null

        val request = MuxingCutRequest(
            inputUri = "content://media/1",
            outputUri = "content://media/2",
            startMs = 0L,
            endMs = 5000L
        )

        val result = pipeline.executeCut(request)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    public fun executeCutFailsWhenNoTracksFound() = runBlocking {
        val pfd = createTempPfd()
        every { contentResolver.openFileDescriptor(any(), "rw") } returns pfd

        every { inspector.inspect(any(), any(), any(), any(), any()) } returns SelectedTrackPlan(
            trackMap = emptyMap(),
            isVideoTrackMap = emptyMap(),
            bufferSize = 1024,
            hasVideoTrack = false,
            durationUs = 0L
        )

        val request = MuxingCutRequest(
            inputUri = "content://media/1",
            outputUri = "content://media/2",
            startMs = 0L,
            endMs = 5000L
        )

        val result = pipeline.executeCut(request)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No tracks found") == true)
    }

    @Test
    public fun executeCutSuccessfullyCutsVideoTrack() = runBlocking {
        val pfd = createTempPfd()
        every { contentResolver.openFileDescriptor(any(), "rw") } returns pfd

        every { inspector.inspect(any(), any(), any(), any(), any()) } returns SelectedTrackPlan(
            trackMap = mapOf(0 to 0),
            isVideoTrackMap = mapOf(0 to true),
            bufferSize = 2048,
            hasVideoTrack = true,
            durationUs = 10000000L
        )
        coEvery { anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any()) } returns mapOf(0 to 5000000L)
        coEvery { anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any(), any()) } returns mapOf(0 to 5000000L)

        val request = MuxingCutRequest(
            inputUri = "content://media/1",
            outputUri = "content://media/2",
            startMs = 1000L,
            endMs = 6000L,
            rotationOverride = 90
        )

        val result = pipeline.executeCut(request)
        assertTrue(result.isSuccess)
        assertEquals("content://media/2", result.getOrNull())
        verify { mediaFinalizer.finalizeVideo("content://media/2") }
    }

    @Test
    public fun executeCutSuccessfullyCutsAudioOnlyTrack() = runBlocking {
        val pfd = createTempPfd()
        every { contentResolver.openFileDescriptor(any(), "rw") } returns pfd

        every { inspector.inspect(any(), any(), any(), any(), any()) } returns SelectedTrackPlan(
            trackMap = mapOf(0 to 0),
            isVideoTrackMap = mapOf(0 to false),
            bufferSize = 2048,
            hasVideoTrack = false,
            durationUs = 10000000L
        )
        coEvery { anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any()) } returns mapOf(0 to 5000000L)
        coEvery { anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any(), any()) } returns mapOf(0 to 5000000L)

        val request = MuxingCutRequest(
            inputUri = "content://media/1",
            outputUri = "content://media/audio_out",
            startMs = 0L,
            endMs = 5000L
        )

        val result = pipeline.executeCut(request)
        assertTrue(result.isSuccess)
        verify { mediaFinalizer.finalizeAudio("content://media/audio_out") }
    }

    @Test
    public fun executeCutDeletesCorruptedOutputFileOnException() = runBlocking {
        val pfd = createTempPfd()
        every { contentResolver.openFileDescriptor(any(), "rw") } returns pfd

        every { inspector.inspect(any(), any(), any(), any(), any()) } returns SelectedTrackPlan(
            trackMap = mapOf(0 to 0),
            isVideoTrackMap = mapOf(0 to true),
            bufferSize = 2048,
            hasVideoTrack = true,
            durationUs = 10000000L
        )
        coEvery { anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any()) } throws RuntimeException("Write error")
        coEvery { anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any(), any()) } throws RuntimeException("Write error")

        val request = MuxingCutRequest(
            inputUri = "content://media/1",
            outputUri = "content://media/error_out",
            startMs = 0L,
            endMs = 5000L
        )

        val result = pipeline.executeCut(request)
        assertTrue(result.isFailure)
        verify { contentResolver.delete(Uri.parse("content://media/error_out"), null, null) }
    }

    @Test
    public fun executeMergeFailsWhenClipsListIsEmpty() = runBlocking {
        val request = MuxingMergeRequest(
            outputUri = "content://media/out",
            clips = emptyList()
        )

        val result = pipeline.executeMerge(request)
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    public fun executeMergeFailsWhenPfdCannotBeOpened() = runBlocking {
        every { contentResolver.openFileDescriptor(any(), "rw") } returns null

        val clip = createTestClip()
        val request = MuxingMergeRequest(
            outputUri = "content://media/out",
            clips = listOf(clip)
        )

        val result = pipeline.executeMerge(request)
        assertTrue(result.isFailure)
    }

    @Test
    public fun executeMergeSuccessfullyMergesVideoClips() = runBlocking {
        val pfd = createTempPfd()
        every { contentResolver.openFileDescriptor(any(), "rw") } returns pfd

        val videoFormat = mockk<MediaFormat>(relaxed = true)
        every { videoFormat.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        every { videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE) } returns true
        every { videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE) } returns 30

        val audioFormat = mockk<MediaFormat>(relaxed = true)
        every { audioFormat.getString(MediaFormat.KEY_MIME) } returns "audio/mp4a-latm"
        every { audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) } returns true
        every { audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) } returns 44100

        every { anyConstructed<MediaExtractor>().trackCount } returns 2
        every { anyConstructed<MediaExtractor>().getTrackFormat(0) } returns videoFormat
        every { anyConstructed<MediaExtractor>().getTrackFormat(1) } returns audioFormat

        val mockPlan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0, 1 to 1),
            isVideoTrackMap = mapOf(0 to true, 1 to false),
            bufferSize = 4096,
            hasVideoTrack = true,
            durationUs = 20000000L
        )
        every { inspector.inspect(any(), any(), any(), any(), any()) } returns mockPlan
        every { inspector.inspectClipForMerge(any(), any(), any(), any(), any()) } returns mockPlan

        coEvery { anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any()) } returns mapOf(0 to 5000000L)
        coEvery { anyConstructed<ExtractorSampleCopier>().copy(any(), any(), any(), any(), any()) } returns mapOf(0 to 5000000L)

        val clip1 = createTestClip()
        val clip2 = createTestClip()
        val request = MuxingMergeRequest(
            outputUri = "content://media/merged_out",
            clips = listOf(clip1, clip2),
            rotationOverride = 0
        )

        val result = pipeline.executeMerge(request)
        assertTrue(result.isSuccess)
        assertEquals("content://media/merged_out", result.getOrNull())
        verify { mediaFinalizer.finalizeVideo("content://media/merged_out") }
    }

    private fun createTestClip(): MediaClip {
        return MediaClip(
            id = UUID.randomUUID(),
            uri = "content://media/external/video/1",
            fileName = "sample.mp4",
            durationMs = 10000L,
            width = 1920,
            height = 1080,
            videoMime = "video/mp4",
            audioMime = "audio/mp4",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = false,
            segments = listOf(TrimSegment(startMs = 0, endMs = 10000L))
        )
    }
}
