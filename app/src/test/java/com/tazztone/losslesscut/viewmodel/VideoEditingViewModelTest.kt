package com.tazztone.losslesscut.viewmodel

import android.net.Uri
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.usecase.*
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
    private val mockVisualDetector = mockk<IVisualSegmentDetector>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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
        mockVisualDetector
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
