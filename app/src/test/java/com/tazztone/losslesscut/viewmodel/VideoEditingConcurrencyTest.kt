package com.tazztone.losslesscut.viewmodel

import android.net.Uri
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import com.tazztone.losslesscut.domain.usecase.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
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
public class VideoEditingConcurrencyTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepo = mockk<IVideoEditingRepository>(relaxed = true)
    private val mockPrefs = mockk<AppPreferences>(relaxed = true)
    private val mockExportUseCase = mockk<ExportUseCase>(relaxed = true)
    
    private lateinit var viewModel: VideoEditingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        coEvery { mockRepo.loadWaveformFromCache(any()) } returns null
        coEvery { mockRepo.extractWaveform(any(), any(), any()) } returns null
        coEvery { mockRepo.getKeyframes(any()) } returns emptyList()
        
        val useCases = VideoEditingUseCases(
            ClipManagementUseCase(mockRepo, testDispatcher),
            mockExportUseCase,
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
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createMockClip(uri: String): MediaClip {
        return MediaClip(
            uri = uri,
            fileName = uri.substringAfterLast("/"),
            durationMs = 10000L,
            width = 1920, height = 1080,
            videoMime = "video/mp4", audioMime = "audio/aac",
            sampleRate = 44100, channelCount = 2,
            fps = 30f, rotation = 0, isAudioOnly = false
        )
    }

    @Test
    public fun testExportReentrancyGuard() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(createMockClip(uri.toString()))
        
        // Return a flow that suspends forever so we can check re-entrancy
        every { mockExportUseCase.execute(any()) } returns flow {
            delay(1000000)
            emit(ExportUseCase.Result.Success(1))
        }

        viewModel.initialize(listOf(uri))
        advanceUntilIdle()

        val settings = ExportSettings(
            isLossless = true, keepAudio = true, keepVideo = true,
            rotationOverride = null, mergeSegments = false
        )
        
        // Trigger two exports simultaneously
        viewModel.exportSegments(settings)
        viewModel.exportSegments(settings)
        
        // Wait for coroutines to reach the execute() call
        advanceTimeBy(100) 
        runCurrent()
        
        verify(exactly = 1) { mockExportUseCase.execute(any()) }
    }

    @Test
    public fun testClipSelectionWaveformRace() = runTest {
        val uri0 = "content://mock/0.mp4"
        val uri1 = "content://mock/1.mp4"
        
        coEvery { mockRepo.createClipFromUri(any()) } answers {
            val uriStr = it.invocation.args[0] as String
            Result.success(createMockClip(uriStr))
        }
        
        // Slow waveform extraction for clip 0
        coEvery { mockRepo.extractWaveform(uri0, any(), any()) } coAnswers {
            delay(1000)
            floatArrayOf(0f, 0f, 0f)
        }
        
        // Fast waveform extraction for clip 1
        coEvery { mockRepo.extractWaveform(uri1, any(), any()) } coAnswers {
            delay(100)
            floatArrayOf(1f, 1f, 1f)
        }

        viewModel.initialize(listOf(Uri.parse(uri0), Uri.parse(uri1)))
        advanceUntilIdle()
        
        // Select clip 0 (slow)
        viewModel.selectClip(0)
        testScheduler.advanceTimeBy(500) // 0 is still "loading"
        
        // Select clip 1 (fast)
        viewModel.selectClip(1)
        
        // Wait for both to "finish"
        advanceUntilIdle()
        
        // Verify clip 1's waveform is shown, and clip 0's didn't overwrite it
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), viewModel.waveformData.value, 0.0001f)
    }

    @Test
    public fun testSilencePreviewCleanupOnClipSwitch() = runTest {
        val uri0 = "content://mock/0.mp4"
        val uri1 = "content://mock/1.mp4"
        
        coEvery { mockRepo.createClipFromUri(any()) } answers {
            val uriStr = it.invocation.args[0] as String
            Result.success(createMockClip(uriStr))
        }
        
        coEvery { mockRepo.loadWaveformFromCache(any()) } answers {
            floatArrayOf(0.1f, 0.1f, 0.1f, 0.1f, 0.1f)
        }

        viewModel.initialize(listOf(Uri.parse(uri0), Uri.parse(uri1)))
        advanceUntilIdle()
        
        // Select clip 0 and generate silence preview
        viewModel.selectClip(0)
        advanceUntilIdle()
        
        viewModel.previewSilenceSegments(0.5f, 10, 0, 10, 100)
        advanceUntilIdle()
        
        assertTrue(viewModel.silencePreviewRanges.value.isNotEmpty())
        
        // Switch to clip 1
        viewModel.selectClip(1)
        advanceUntilIdle()
        
        assertTrue(viewModel.silencePreviewRanges.value.isEmpty())
    }
}
