package com.tazztone.losslesscut

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
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
class VideoEditingViewModelTest {

    private lateinit var viewModel: VideoEditingViewModel
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var mockEngine: LosslessEngineInterface
    private lateinit var mockStorageUtils: StorageUtils
    private lateinit var mockAppPreferences: AppPreferences
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockEngine = mockk()
        mockStorageUtils = mockk(relaxed = true)
        mockAppPreferences = mockk(relaxed = true)
        
        viewModel = VideoEditingViewModel(
            context,
            mockEngine,
            mockStorageUtils,
            mockAppPreferences,
            testDispatcher
        )
        
        // Default mocks
        coEvery { mockEngine.probeKeyframes(any(), any()) } returns listOf(0L, 5000L, 10000L)
        every { mockStorageUtils.getVideoMetadata(any()) } returns StorageUtils.VideoMetadata("mock_video.mp4", 10000L)

        val shadowRetriever = org.robolectric.Shadows.shadowOf(android.media.MediaMetadataRetriever())
        org.robolectric.shadows.ShadowMediaMetadataRetriever.addMetadata(
            "content://mock/video.mp4",
            android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE,
            "30"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testInitialization_createsInitialSegment() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        viewModel.initialize(uri)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue("State is not Success: $state", state is VideoEditingUiState.Success)
        val success = state as VideoEditingUiState.Success
        assertEquals(1, success.segments.size)
        assertEquals(0L, success.segments[0].startMs)
    }

    @Test
    fun testSplitSegment() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        viewModel.initialize(uri)
        advanceUntilIdle()
        
        viewModel.splitSegmentAt(5000L)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.segments.size)
        assertEquals(0L, state.segments[0].startMs)
        assertEquals(5000L, state.segments[0].endMs)
        assertEquals(5000L, state.segments[1].startMs)
        assertTrue(state.canUndo)
    }

    @Test
    fun testUndoAfterSplit() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        viewModel.initialize(uri)
        advanceUntilIdle()
        
        viewModel.splitSegmentAt(5000L)
        advanceUntilIdle()
        viewModel.undo()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.segments.size)
        assertFalse(state.canUndo)
    }

    @Test
    fun testToggleSegmentAction() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        viewModel.initialize(uri)
        advanceUntilIdle()
        
        // Split first so there are two segments, bypassing the "cannot delete last segment" protection
        viewModel.splitSegmentAt(5000L)
        advanceUntilIdle()
        
        val segmentId = (viewModel.uiState.value as VideoEditingUiState.Success).segments[0].id
        viewModel.toggleSegmentAction(segmentId)
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(SegmentAction.DISCARD, state.segments[0].action)
        
        viewModel.toggleSegmentAction(segmentId)
        assertEquals(SegmentAction.KEEP, (viewModel.uiState.value as VideoEditingUiState.Success).segments[0].action)
    }

    @Test
    fun testUndoStackCap() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        viewModel.initialize(uri)
        advanceUntilIdle()
        
        // Push 35 changes
        for (i in 1..35) {
            viewModel.splitSegmentAt(i.toLong() * 100)
        }
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertTrue(state.canUndo)
        
        repeat(29) { viewModel.undo() }
        val finalState = viewModel.uiState.value as VideoEditingUiState.Success
        assertFalse("Should be at the bottom of the capped stack", finalState.canUndo)
    }

    @Test
    fun testExport_CallsMergeWhenEnabledAndMultipleSegments() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        viewModel.initialize(uri)
        advanceUntilIdle()
        
        // Split to have two KEEP segments
        viewModel.splitSegmentAt(5000L)
        advanceUntilIdle()
        
        val outputUri = Uri.parse("content://mock/output.mp4")
        every { mockStorageUtils.createVideoOutputUri(any()) } returns outputUri
        coEvery { mockEngine.executeLosslessMerge(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(outputUri)
        
        viewModel.exportSegments(isLossless = true, mergeSegments = true)
        advanceUntilIdle()
        
        coVerify(exactly = 1) { 
            mockEngine.executeLosslessMerge(any(), eq(uri), eq(outputUri), any(), any(), any(), any()) 
        }
        coVerify(exactly = 0) { 
            mockEngine.executeLosslessCut(any(), any(), any(), any(), any(), any(), any(), any()) 
        }
    }
}
