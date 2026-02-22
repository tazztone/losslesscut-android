package com.tazztone.losslesscut.viewmodel
import com.tazztone.losslesscut.di.*
import com.tazztone.losslesscut.customviews.*
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.*
import com.tazztone.losslesscut.viewmodel.*
import com.tazztone.losslesscut.engine.*
import com.tazztone.losslesscut.data.*
import com.tazztone.losslesscut.utils.*

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        coEvery { mockEngine.getKeyframes(any(), any()) } returns Result.success(listOf(0L, 5000L, 10000L))
        
        val defaultMetadata = MediaMetadata(
            durationMs = 10000L,
            width = 1920,
            height = 1080,
            videoMime = "video/avc",
            audioMime = "audio/mp4a-latm",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            tracks = emptyList()
        )
        coEvery { mockEngine.getMediaMetadata(any(), any()) } returns Result.success(defaultMetadata)
        every { mockStorageUtils.getFileName(any()) } returns "mock_video.mp4"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testInitialization_createsInitialClipsAndSegments() = runTest {
        val uris = listOf(Uri.parse("content://mock/video1.mp4"), Uri.parse("content://mock/video2.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue("State is not Success: $state", state is VideoEditingUiState.Success)
        val success = state as VideoEditingUiState.Success
        assertEquals(2, success.clips.size)
        assertEquals(1, success.segments.size)
        assertEquals(0L, success.segments[0].startMs)
    }

    @Test
    fun testSplitSegment() = runTest {
        val uris = listOf(Uri.parse("content://mock/video.mp4"))
        viewModel.initialize(uris)
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
        val uris = listOf(Uri.parse("content://mock/video.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        viewModel.splitSegmentAt(5000L)
        advanceUntilIdle()
        viewModel.undo()
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.segments.size)
        assertFalse(state.canUndo)
    }

    @Test
    fun testMarkSegmentDiscarded() = runTest {
        val uris = listOf(Uri.parse("content://mock/video.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        viewModel.splitSegmentAt(5000L)
        advanceUntilIdle()
        
        val segmentId = (viewModel.uiState.value as VideoEditingUiState.Success).segments[0].id
        viewModel.markSegmentDiscarded(segmentId)
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(SegmentAction.DISCARD, state.segments[0].action)
        
        viewModel.markSegmentDiscarded(segmentId)
        assertEquals(SegmentAction.KEEP, (viewModel.uiState.value as VideoEditingUiState.Success).segments[0].action)
    }

    @Test
    fun testReorderClips() = runTest {
        val uris = listOf(Uri.parse("content://mock/video1.mp4"), Uri.parse("content://mock/video2.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        val firstClipUri = (viewModel.uiState.value as VideoEditingUiState.Success).clips[0].uri
        viewModel.reorderClips(0, 1)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(firstClipUri, state.clips[1].uri)
        assertTrue(state.canUndo)
    }

    @Test
    fun testAddClips_AppendsClips() = runTest {
        val uris = listOf(Uri.parse("content://mock/video1.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        val newUris = listOf(Uri.parse("content://mock/video2.mp4"))
        viewModel.addClips(newUris)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(2, state.clips.size)
        assertTrue(state.canUndo)
    }

    @Test
    fun testRemoveClip() = runTest {
        val uris = listOf(Uri.parse("content://mock/v1.mp4"), Uri.parse("content://mock/v2.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        viewModel.removeClip(0)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.clips.size)
        assertEquals("mock_video.mp4", state.clips[0].fileName)
        assertTrue(state.canUndo)
    }

    @Test
    fun testExport_CallsMergeWithCorrectClips() = runTest {
        val uris = listOf(Uri.parse("content://mock/v1.mp4"), Uri.parse("content://mock/v2.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        val outputUri = Uri.parse("content://mock/output.mp4")
        coEvery { mockStorageUtils.createMediaOutputUri(any(), any(), any()) } returns outputUri
        coEvery { mockEngine.executeLosslessMerge(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(outputUri)
        
        viewModel.exportSegments(isLossless = true, keepAudio = true, keepVideo = true, rotationOverride = null, mergeSegments = true)
        advanceUntilIdle()
        
        coVerify(exactly = 1) { 
            mockEngine.executeLosslessMerge(any(), eq(outputUri), any(), any(), any(), any(), any()) 
        }
    }
}
