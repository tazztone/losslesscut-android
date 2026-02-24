package com.tazztone.losslesscut.viewmodel

import android.net.Uri
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VideoEditingFlowTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepo = mockk<IVideoEditingRepository>(relaxed = true)
    private val mockPrefs = mockk<AppPreferences>(relaxed = true)
    
    private lateinit var viewModel: VideoEditingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        val useCases = VideoEditingUseCases(
            ClipManagementUseCase(mockRepo, testDispatcher),
            ExportUseCase(mockRepo, testDispatcher),
            ExtractSnapshotUseCase(mockRepo, testDispatcher),
            SilenceDetectionUseCase(mockRepo, testDispatcher),
            SessionUseCase(mockRepo, testDispatcher)
        )
        
        viewModel = VideoEditingViewModel(
            mockRepo,
            mockPrefs,
            useCases,
            testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testIntegratedEditingFlow() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        val clip = MediaClip(
            uri = uri.toString(),
            fileName = "video.mp4",
            durationMs = 10000L,
            width = 1920, height = 1080,
            videoMime = "video/mp4", audioMime = "audio/aac",
            sampleRate = 44100, channelCount = 2,
            fps = 30f, rotation = 0, isAudioOnly = false
        )
        
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)
        val waveform = FloatArray(100) { 0.1f }
        coEvery { mockRepo.loadWaveformFromCache(any()) } returns waveform

        // 1. Initialize
        viewModel.initialize(listOf(uri))
        advanceUntilIdle()
        
        var state = viewModel.uiState.value
        assertTrue("Should be Success but was $state", state is VideoEditingUiState.Success)
        
        // 2. Split
        viewModel.splitSegmentAt(5000L)
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(2, (state as VideoEditingUiState.Success).segments.size)

        // 3. Undo
        viewModel.undo()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(1, (state as VideoEditingUiState.Success).segments.size)
    }
}
