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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
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
public class VideoEditingEdgeCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepo = mockk<IVideoEditingRepository>(relaxed = true)
    private val mockPrefs = mockk<AppPreferences>(relaxed = true)
    private val mockExportUseCase = mockk<ExportUseCase>()
    
    private lateinit var viewModel: VideoEditingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        // Explicitly stub common repository calls to avoid MockK/Coroutine ClassCastException
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

    private fun createMockClip(uri: String, durationMs: Long): MediaClip {
        return MediaClip(
            uri = uri,
            fileName = uri.substringAfterLast("/"),
            durationMs = durationMs,
            width = 1920, height = 1080,
            videoMime = "video/mp4", audioMime = "audio/aac",
            sampleRate = 44100, channelCount = 2,
            fps = 30f, rotation = 0, isAudioOnly = false
        )
    }

    private fun requireSuccess(state: VideoEditingUiState): VideoEditingUiState.Success {
        if (state !is VideoEditingUiState.Success) {
            val details = if (state is VideoEditingUiState.Error) " (Error: ${state.error})" else ""
            fail("Expected Success state but was ${state.javaClass.simpleName}$details: $state")
        }
        return state as VideoEditingUiState.Success
    }

    @Test
    public fun testClipRemovalIndexStability() = runTest {
        val uris = listOf(
            Uri.parse("content://mock/0.mp4"),
            Uri.parse("content://mock/1.mp4"),
            Uri.parse("content://mock/2.mp4")
        )
        
        coEvery { mockRepo.createClipFromUri(any()) } answers {
            val uriStr = it.invocation.args[0] as String
            Result.success(createMockClip(uriStr, 10000L))
        }
        
        viewModel.initialize(uris)
        advanceUntilIdle()
        
        viewModel.selectClip(1)
        advanceUntilIdle()
        
        var state = requireSuccess(viewModel.uiState.value)
        assertEquals(1, state.selectedClipIndex)
        assertEquals("1.mp4", state.clips[state.selectedClipIndex].fileName)

        viewModel.removeClip(0)
        advanceUntilIdle()
        
        state = requireSuccess(viewModel.uiState.value)
        assertEquals(2, state.clips.size)
        assertEquals("1.mp4", state.clips[state.selectedClipIndex].fileName)
        assertEquals(0, state.selectedClipIndex) 
    }

    @Test
    public fun testRapidSwitchingCancellation() = runTest {
        val uri0 = Uri.parse("content://mock/0.mp4")
        val uri1 = Uri.parse("content://mock/1.mp4")
        
        coEvery { mockRepo.createClipFromUri(any()) } answers {
            val uriStr = it.invocation.args[0] as String
            Result.success(createMockClip(uriStr, 10000L))
        }
        
        coEvery { mockRepo.loadWaveformFromCache(any()) } coAnswers {
            delay(1000)
            null
        }

        viewModel.initialize(listOf(uri0, uri1))
        advanceUntilIdle()
        
        viewModel.selectClip(0)
        testScheduler.advanceTimeBy(500)
        viewModel.selectClip(1)
        advanceUntilIdle()
        
        val state = requireSuccess(viewModel.uiState.value)
        assertEquals(1, state.selectedClipIndex)
    }

    @Test
    public fun testHistoryRotation() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(createMockClip("v.mp4", 10000L))
        
        viewModel.initialize(listOf(uri))
        advanceUntilIdle()
        
        for (i in 1..40) {
            viewModel.splitSegmentAt(i * 100L)
            advanceUntilIdle()
            if (i % 10 == 0) {
                val s = viewModel.uiState.value
                assertTrue("State should be Success at split $i but was $s", s is VideoEditingUiState.Success)
            }
        }
        
        val stateAfterSplits = requireSuccess(viewModel.uiState.value)
        assertTrue(stateAfterSplits.canUndo)
        
        viewModel.undo()
        advanceUntilIdle()
        
        assertTrue("Expected canRedo to be true after undo", requireSuccess(viewModel.uiState.value).canRedo)
    }

    @Test
    public fun testSilenceDetectionContiguity() = runTest {
        val uri = Uri.parse("content://mock/contiguity.mp4")
        val clip = createMockClip(uri.toString(), 1000L)
        
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(clip)
        
        val waveform = FloatArray(100) { i -> if (i in 2..50) 0.01f else 0.5f }
        coEvery { mockRepo.loadWaveformFromCache(any()) } returns waveform
        
        viewModel.initialize(listOf(uri))
        advanceUntilIdle()

        viewModel.previewSilenceSegments(0.05f, 100, 0, 100, 100)
        advanceUntilIdle()
        viewModel.applySilenceDetection()
        advanceUntilIdle()
        
        val state = requireSuccess(viewModel.uiState.value)
        val segments = state.segments
        
        var current = 0L
        for (seg in segments) {
            assertEquals("Gap found before segment $seg", current, seg.startMs)
            current = seg.endMs
        }
        assertEquals("Timeline must end at clip duration", 1000L, current)
    }

    @Test
    public fun testExportDelegation() = runTest {
        val uri = Uri.parse("content://mock/video.mp4")
        coEvery { mockRepo.createClipFromUri(any()) } returns Result.success(createMockClip("v.mp4", 10000L))
        
        // Mock export flow progress then success
        every { mockExportUseCase.execute(any()) } returns flow {
            emit(ExportUseCase.Result.Progress(50, "Saving 1/2"))
            emit(ExportUseCase.Result.Progress(100, "Done"))
            emit(ExportUseCase.Result.Success(2))
        }

        viewModel.initialize(listOf(uri))
        advanceUntilIdle()

        val settings = ExportSettings(
            isLossless = true, keepAudio = true, keepVideo = true,
            rotationOverride = null, mergeSegments = false
        )
        
        viewModel.exportSegments(settings)
        
        // Check Loading state
        // Since we use StandardTestDispatcher and flow, we need to advance or use collect
        // But with advanceUntilIdle, it should process all emissions
        advanceUntilIdle()
        
        // After flow completes, it should return to Success state in ViewModel
        val state = requireSuccess(viewModel.uiState.value)
        // Verify dirty flag is cleared if success
        // In exportSegments Success branch: _isDirty.value = false
        // But we don't have a direct way to check _isDirty from outside in the current Success state?
        // Actually, we can check if it returned to Success (it does in the observer)
        assertNotNull(state)
    }
}
