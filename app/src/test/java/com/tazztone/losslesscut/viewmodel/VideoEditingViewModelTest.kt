package com.tazztone.losslesscut.viewmodel

import android.net.Uri
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.usecase.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class VideoEditingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockRepo = mockk<IVideoEditingRepository>(relaxed = true)
    private val mockPrefs = mockk<AppPreferences>(relaxed = true)
    
    // Real UseCases for logic-heavy parts (pure functional logic like segment splitting/merging)
    // We use real instances here to verify actual state transformations correctly.
    private lateinit var clipUseCase: ClipManagementUseCase
    private lateinit var silenceUseCase: SilenceDetectionUseCase
    
    // Mocked for side-effects
    private val mockExportUseCase = mockk<ExportUseCase>(relaxed = true)
    private val mockSnapUseCase = mockk<ExtractSnapshotUseCase>(relaxed = true)
    private val mockSessionUseCase = mockk<SessionUseCase>(relaxed = true)
    private val mockSegmentDetector = mockk<SegmentDetectorUseCase>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockPrefs.undoLimitFlow } returns flowOf(30)
        coEvery { mockSessionUseCase.saveSession(any(), any()) } returns Result.success(Unit)
        clipUseCase = ClipManagementUseCase(mockRepo, testDispatcher)
        silenceUseCase = SilenceDetectionUseCase(mockRepo, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createUseCases() = VideoEditingUseCases(
        clipUseCase,
        mockExportUseCase,
        mockSnapUseCase,
        silenceUseCase,
        mockSessionUseCase,
        mockSegmentDetector
    )

    @Test
    public fun testInitialization_success() = runTest {
        val uris = listOf(Uri.parse("content://mock/video1.mp4"))
        
        val clip = createMockClip("uri1", 10000L)
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)
        
        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        
        viewModel.initialize(uris)
        
        val state = viewModel.uiState.value
        assertTrue("State should be Success but was $state", state is VideoEditingUiState.Success)
    }

    @Test
    public fun testUndoRedo_clipsChange() = runTest {
        val uris = listOf(Uri.parse("content://mock/video1.mp4"))
        
        val clip1 = createMockClip("content://mock/video1.mp4", 1000)
        val clip2 = createMockClip("content://mock/video2.mp4", 1000)

        coEvery { mockRepo.createClipFromUri("content://mock/video1.mp4") } returns Result.success(clip1)
        coEvery { mockRepo.createClipFromUri("content://mock/video2.mp4") } returns Result.success(clip2)
        
        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        
        viewModel.initialize(uris)
        
        // Add a clip
        viewModel.addClips(listOf(Uri.parse("content://mock/video2.mp4")))
        
        var state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.clips.size)
        assertTrue(state.canUndo)
        
        // Undo
        viewModel.undo()
        state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.clips.size)
        assertTrue(state.canRedo)
        
        // Redo
        viewModel.redo()
        state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.clips.size)
    }

    @Test
    public fun testSplitSegment_updatesState() = runTest {
        val uris = listOf(Uri.parse("content://mock/video1.mp4"))
        val clip = createMockClip("content://mock/video1.mp4", 1000)
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)
        
        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        
        viewModel.initialize(uris)
        
        // Split at 500ms
        viewModel.splitSegmentAt(500L)
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.segments.size)
        assertTrue(viewModel.isDirty.value)
    }

    @Test
    public fun testInitialization_failure() = runTest {
        val uris = listOf(Uri.parse("content://mock/bad_video.mp4"))
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.failure(Exception("Load failed"))
        
        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        
        viewModel.initialize(uris)
        
        assertTrue(viewModel.uiState.value is VideoEditingUiState.Error)
    }

    @Test
    public fun testCancelVisualDetection_resetsProgressAndState() = runTest {
        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)

        viewModel.cancelVisualDetection()

        verify { mockSegmentDetector.cancelVisual() }
        assertNull(viewModel.visualDetectionProgress.value)
    }

    @Test
    public fun testLateVisualResult_afterCancelIsIgnored() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 1000L)
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)
        val listenerSlot = slot<VisualDetectionListener>()
        every {
            mockSegmentDetector.detectVisual(
                scope = any(),
                uri = any(),
                config = any(),
                listener = capture(listenerSlot),
                clip = any(),
                allowDecode = any()
            )
        } just Runs

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))
        viewModel.previewVisualSegments(
            com.tazztone.losslesscut.domain.model.VisualDetectionConfig(
                strategy = com.tazztone.losslesscut.domain.model.VisualStrategy.BLACK_FRAMES,
                sensitivityThreshold = 20f,
                sampleIntervalFrames = 5,
                minSegmentDurationMs = 100L
            )
        )
        advanceUntilIdle()

        listenerSlot.captured.onComplete(listOf(100L..200L))
        advanceUntilIdle()
        assertEquals(listOf(100L..200L), viewModel.detectionPreviewRanges.value)

        viewModel.cancelVisualDetection()
        listenerSlot.captured.onComplete(listOf(300L..400L))
        advanceUntilIdle()
        assertTrue(viewModel.detectionPreviewRanges.value.isEmpty())
    }

    @Test
    public fun testSetInPoint_insideSegment_updatesStartMs() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 15000L).copy(
            segments = listOf(TrimSegment(startMs = 0L, endMs = 4000L))
        )
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))

        viewModel.setInPoint(1500L)

        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.segments.size)
        assertEquals(1500L, state.segments[0].startMs)
        assertEquals(4000L, state.segments[0].endMs)
    }

    @Test
    public fun testSetInPoint_beforeNextSegment_pullsNextSegmentStartMs() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 15000L).copy(
            segments = listOf(
                TrimSegment(startMs = 0L, endMs = 3000L),
                TrimSegment(startMs = 10000L, endMs = 14000L)
            )
        )
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))

        viewModel.setInPoint(5000L)

        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.segments.size)
        assertEquals(5000L, state.segments[1].startMs)
        assertEquals(14000L, state.segments[1].endMs)
        assertEquals(state.segments[1].id, state.selectedSegmentId)
    }

    @Test
    public fun testSetInPoint_afterAllSegments_createsNewSegment() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 20000L).copy(
            segments = listOf(TrimSegment(startMs = 0L, endMs = 3000L))
        )
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)
        coEvery { mockRepo.getKeyframes(any()) } returns listOf(0L, 2000L, 4000L, 6000L, 8000L, 10000L, 12000L, 14000L)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))

        viewModel.setInPoint(8500L)

        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.segments.size)
        val newSeg = state.segments[1]
        assertEquals(10000L, newSeg.startMs) // Lossless exports start at the next keyframe.
        assertEquals(14000L, newSeg.endMs) // 3rd keyframe after 10000 (10000, 12000, 14000)
        assertEquals(newSeg.id, state.selectedSegmentId)
    }

    @Test
    public fun testSetInPoint_withLosslessMode_snapsToNearestKeyframe() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 20000L).copy(
            segments = listOf(TrimSegment(startMs = 0L, endMs = 3000L))
        )
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)
        coEvery { mockRepo.getKeyframes(any()) } returns listOf(0L, 2000L, 4000L, 6000L, 8000L, 10000L, 12000L, 14000L)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))

        // 8547ms is safely rounded forward to keyframe at 10000ms.
        viewModel.setInPoint(8547L)

        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.segments.size)
        val newSeg = state.segments[1]
        assertEquals(10000L, newSeg.startMs) // Snapped forward to 10000ms.
        assertEquals(14000L, newSeg.endMs) // 3rd keyframe after 10000ms (10000, 12000, 14000)
        assertEquals(newSeg.id, state.selectedSegmentId)
    }

    @Test
    public fun testSetInPoint_atClipEnd_doesNotCrashOrCreateInvalidSegment() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 20000L).copy(
            segments = listOf(TrimSegment(startMs = 0L, endMs = 3000L))
        )
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))

        viewModel.setInPoint(clip.durationMs)

        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.segments.size)
        assertEquals(3000L, state.segments.single().endMs)
    }

    @Test
    public fun testSetOutPoint_withLosslessMode_snapsToNearestKeyframe() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 20000L).copy(
            segments = listOf(TrimSegment(startMs = 0L, endMs = 10000L))
        )
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)
        coEvery { mockRepo.getKeyframes(any()) } returns listOf(0L, 2000L, 4000L, 6000L, 8000L, 10000L)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))

        // 5800ms is safely rounded backward to keyframe at 4000ms.
        viewModel.setOutPoint(5800L)

        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.segments.size)
        assertEquals(0L, state.segments[0].startMs)
        assertEquals(4000L, state.segments[0].endMs) // Snapped backward to 4000ms.
    }

    @Test
    public fun testSetOutPoint_afterExistingSegment_extendsSegmentEndRightward() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 20000L).copy(
            segments = listOf(TrimSegment(startMs = 0L, endMs = 3000L))
        )
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))

        viewModel.setOutPoint(7000L)

        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.segments.size)
        assertEquals(0L, state.segments[0].startMs)
        assertEquals(7000L, state.segments[0].endMs)
    }

    @Test
    public fun testSetOutPoint_beforeAllSegments_createsNewSegmentBackward() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 20000L).copy(
            segments = listOf(TrimSegment(startMs = 10000L, endMs = 15000L))
        )
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)
        coEvery { mockRepo.getKeyframes(any()) } returns listOf(0L, 2000L, 4000L, 6000L, 8000L, 10000L, 12000L)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))

        // OUT point set at 6000ms (before all segments 10000..15000)
        viewModel.setOutPoint(6000L)

        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.segments.size)
        val newSeg = state.segments[0]
        assertEquals(0L, newSeg.startMs) // 3 keyframes backward from 6000 (4000, 2000, 0)
        assertEquals(6000L, newSeg.endMs)
        assertEquals(newSeg.id, state.selectedSegmentId)
    }

    @Test
    public fun testOnOriginalClipsDeleted_allClipsDeleted_resetsToInitialState() = runTest {
        val clip = createMockClip("content://mock/video1.mp4", 10000L)
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip.uri)))
        assertTrue(viewModel.uiState.value is VideoEditingUiState.Success)

        viewModel.onOriginalClipsDeleted(listOf(Uri.parse(clip.uri)))

        assertTrue(viewModel.uiState.value is VideoEditingUiState.Initial)
        assertFalse(viewModel.isDirty.value)
    }

    @Test
    public fun testOnOriginalClipsDeleted_subsetDeleted_updatesRemainingClips() = runTest {
        val clip1 = createMockClip("content://mock/video1.mp4", 10000L)
        val clip2 = createMockClip("content://mock/video2.mp4", 10000L)
        coEvery { mockRepo.createClipFromUri(eq("content://mock/video1.mp4")) } returns Result.success(clip1)
        coEvery { mockRepo.createClipFromUri(eq("content://mock/video2.mp4")) } returns Result.success(clip2)

        val viewModel = VideoEditingViewModel(mockRepo, mockPrefs, createUseCases(), testDispatcher)
        viewModel.initialize(listOf(Uri.parse(clip1.uri), Uri.parse(clip2.uri)))
        val initialState = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, initialState.clips.size)

        viewModel.onOriginalClipsDeleted(listOf(Uri.parse(clip1.uri)))

        val updatedState = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, updatedState.clips.size)
        assertEquals("content://mock/video2.mp4", updatedState.clips[0].uri)
    }

    private fun createMockClip(uri: String, durationMs: Long) = MediaClip(

        id = UUID.randomUUID(),
        uri = uri,
        fileName = "test.mp4",
        durationMs = durationMs,
        width = 1920,
        height = 1080,
        videoMime = "video/mp4",
        audioMime = "audio/aac",
        sampleRate = 44100,
        channelCount = 2,
        fps = 30f,
        rotation = 0,
        isAudioOnly = false
    )
}
