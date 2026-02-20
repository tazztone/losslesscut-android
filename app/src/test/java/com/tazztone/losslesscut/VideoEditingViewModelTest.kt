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
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = VideoEditingViewModel()
        mockkObject(LosslessEngine)
        mockkObject(StorageUtils)
        
        // Default mocks
        coEvery { LosslessEngine.probeKeyframes(any(), any()) } returns listOf(0.0, 5.0, 10.0)
        every { StorageUtils.getVideoMetadata(any(), any()) } returns Pair("mock_video.mp4", 10000L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testInitialization_createsInitialSegment() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        
        // We need to mock MediaMetadataRetriever as it's used in initialize
        // For simplicity in this env, we might just test the logic that follows
        
        viewModel.initialize(context, uri)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state is VideoEditingUiState.Success)
        val success = state as VideoEditingUiState.Success
        assertEquals(1, success.segments.size)
        assertEquals(0L, success.segments[0].startMs)
    }

    @Test
    fun testSplitSegment() = runTest {
        // Setup initial success state manually to avoid full initialize dependencies if possible
        // But initialize is better for integration-style unit test
        val uri = Uri.parse("content://mock/video.mp4")
        viewModel.initialize(context, uri)
        advanceUntilIdle()
        
        viewModel.splitSegmentAt(5000L)
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.segments.size)
        assertEquals(0L, state.segments[0].startMs)
        assertEquals(5000L, state.segments[0].endMs)
        assertEquals(5001L, state.segments[1].startMs)
        assertTrue(state.canUndo)
    }

    @Test
    fun testUndoAfterSplit() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        viewModel.initialize(context, uri)
        advanceUntilIdle()
        
        viewModel.splitSegmentAt(5000L)
        viewModel.undo()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.segments.size)
        assertFalse(state.canUndo)
    }

    @Test
    fun testToggleSegmentAction() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        viewModel.initialize(context, uri)
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
        viewModel.initialize(context, uri)
        advanceUntilIdle()
        
        // Push 35 changes
        for (i in 1..35) {
            viewModel.splitSegmentAt(i.toLong() * 100)
        }
        
        // History should be capped at 30. Initial + 35 splits = 36 states. 
        // If capped at 30, only 30 remain.
        // canUndo checks history.size > 1
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertTrue(state.canUndo)
        
        // Theoretically it should survive 30 pops.
        repeat(29) { viewModel.undo() }
        val finalState = viewModel.uiState.value as VideoEditingUiState.Success
        assertFalse("Should be at the bottom of the capped stack", finalState.canUndo)
    }
}
