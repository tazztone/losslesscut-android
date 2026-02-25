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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    public fun testInitialization_success() = runTest {
        val uris = listOf(Uri.parse("content://mock/video1.mp4"))
        val mockRepo = mockk<IVideoEditingRepository>()
        val mockPrefs = mockk<AppPreferences>()
        val mockClipUseCase = mockk<ClipManagementUseCase>()
        val mockExportUseCase = mockk<ExportUseCase>()
        val mockSnapUseCase = mockk<ExtractSnapshotUseCase>()
        val mockSilenceUseCase = mockk<SilenceDetectionUseCase>()
        val mockSessionUseCase = mockk<SessionUseCase>()

        val mockClips = listOf(
            MediaClip(
                id = UUID.randomUUID(),
                uri = uris[0].toString(),
                fileName = "video1.mp4",
                durationMs = 10000L,
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
        )
        
        coEvery { mockClipUseCase.createClips(any()) } returns Result.success(mockClips)
        coEvery { mockRepo.evictOldCacheFiles() } returns Unit
        coEvery { mockRepo.getKeyframes(any()) } returns emptyList()
        coEvery { mockRepo.loadWaveformFromCache(any()) } returns null
        coEvery { mockRepo.extractWaveform(any(), any()) } returns null
        
        val useCases = VideoEditingUseCases(
            mockClipUseCase,
            mockExportUseCase,
            mockSnapUseCase,
            mockSilenceUseCase,
            mockSessionUseCase
        )
        
        val viewModel = VideoEditingViewModel(
            mockRepo,
            mockPrefs,
            useCases,
            testDispatcher
        )
        
        viewModel.initialize(uris)
        
        val state = viewModel.uiState.value
        assertTrue("State should be Success but was $state", state is VideoEditingUiState.Success)
    }
}
