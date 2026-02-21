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
        coEvery { mockEngine.probeKeyframes(any(), any()) } returns listOf(0L, 5000L, 10000L)
        
        val defaultMetadata = StorageUtils.DetailedMetadata(
            fileName = "mock_video.mp4",
            durationMs = 10000L,
            width = 1920,
            height = 1080,
            videoMime = "video/avc",
            audioMime = "audio/mp4a-latm",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = false
        )
        every { mockStorageUtils.getDetailedMetadata(any()) } returns defaultMetadata
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
    fun testToggleSegmentAction() = runTest {
        val uris = listOf(Uri.parse("content://mock/video.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
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
    fun testReorderClips() = runTest {
        val uris = listOf(Uri.parse("content://mock/video1.mp4"), Uri.parse("content://mock/video2.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        val firstClipId = (viewModel.uiState.value as VideoEditingUiState.Success).clips[0].id
        viewModel.reorderClips(0, 1)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(firstClipId, state.clips[1].id)
        assertTrue(state.canUndo)
    }

    @Test
    fun testValidationFailure_EmitsEvent() = runTest {
        val uris = listOf(Uri.parse("content://mock/video1.mp4"), Uri.parse("content://mock/video2.mp4"))
        
        val baseMetadata = StorageUtils.DetailedMetadata(
            fileName = "v1.mp4", durationMs = 1000L, width = 1920, height = 1080,
            videoMime = "video/avc", audioMime = "audio/mp4a-latm", sampleRate = 44100,
            channelCount = 2, fps = 30f, rotation = 0, isAudioOnly = false
        )
        val incompatibleMetadata = baseMetadata.copy(fileName = "v2.mp4", width = 1280) // Diff res
        
        every { mockStorageUtils.getDetailedMetadata(uris[0]) } returns baseMetadata
        every { mockStorageUtils.getDetailedMetadata(uris[1]) } returns incompatibleMetadata
        
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as VideoEditingUiState.Success
        assertEquals(1, state.clips.size) // Only v1 should be added
    }

    @Test
    fun testAddClips_AppendsCompatibleClips() = runTest {
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
        assertEquals("mock_video.mp4", state.clips[0].fileName) // v2 because v1 was removed
        assertTrue(state.canUndo)
    }

    @Test
    fun testExport_CallsMergeWithCorrectClips() = runTest {
        val uris = listOf(Uri.parse("content://mock/v1.mp4"), Uri.parse("content://mock/v2.mp4"))
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        val outputUri = Uri.parse("content://mock/output.mp4")
        every { mockStorageUtils.createMediaOutputUri(any(), any()) } returns outputUri
        coEvery { mockEngine.executeLosslessMerge(any(), any(), any(), any(), any(), any()) } returns Result.success(outputUri)
        
        viewModel.exportSegments(isLossless = true, mergeSegments = true)
        advanceUntilIdle()
        
        coVerify(exactly = 1) { 
            mockEngine.executeLosslessMerge(any(), eq(outputUri), any(), any(), any(), any()) 
        }
    }
}
