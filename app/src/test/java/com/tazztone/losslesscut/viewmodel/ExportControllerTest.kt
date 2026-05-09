package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.usecase.ExportUseCase
import com.tazztone.losslesscut.domain.usecase.ExtractSnapshotUseCase
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ExportControllerTest {

    private val mockExportUseCase = mockk<ExportUseCase>(relaxed = true)
    private val mockSnapshotUseCase = mockk<ExtractSnapshotUseCase>(relaxed = true)
    private val mockPrefs = mockk<AppPreferences>(relaxed = true)

    private lateinit var exportController: ExportController

    @Before
    fun setUp() {
        exportController = ExportController(
            mockExportUseCase,
            mockSnapshotUseCase,
            mockPrefs
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
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

    @Test
    fun testExportSegments_mapsParametersCorrectly() {
        val clip = createMockClip("content://mock/video.mp4", 1000L)
        val clips = listOf(clip)
        val selectedIndex = 0
        val settings = ExportSettings(
            isLossless = true,
            keepAudio = true,
            keepVideo = false,
            rotationOverride = 90,
            mergeSegments = true,
            selectedTracks = listOf(1, 2)
        )

        exportController.exportSegments(clips, selectedIndex, settings)

        val paramsSlot = slot<ExportUseCase.Params>()
        verify { mockExportUseCase.execute(capture(paramsSlot)) }

        val capturedParams = paramsSlot.captured
        assertEquals(clips, capturedParams.clips)
        assertEquals(selectedIndex, capturedParams.selectedClipIndex)
        assertEquals(settings.isLossless, capturedParams.isLossless)
        assertEquals(settings.keepAudio, capturedParams.keepAudio)
        assertEquals(settings.keepVideo, capturedParams.keepVideo)
        assertEquals(settings.rotationOverride, capturedParams.rotationOverride)
        assertEquals(settings.mergeSegments, capturedParams.mergeSegments)
        assertEquals(settings.selectedTracks, capturedParams.selectedTracks)
    }

    @Test
    fun testExtractSnapshot_success() = runTest {
        val clip = createMockClip("content://mock/video.mp4", 1000L)
        val positionMs = 500L
        val format = "PNG"
        val quality = 100
        val expectedResult = ExtractSnapshotUseCase.Result.Success("snapshot.png")

        every { mockPrefs.snapshotFormatFlow } returns flowOf(format)
        every { mockPrefs.jpgQualityFlow } returns flowOf(quality)
        coEvery { mockSnapshotUseCase.execute(any(), any(), any(), any()) } returns expectedResult

        val result = exportController.extractSnapshot(clip, positionMs)

        assertEquals(expectedResult, result)
        coVerify { mockSnapshotUseCase.execute(clip.uri, positionMs, format, quality) }
        assertFalse(exportController.isSnapshotInProgress.value)
    }

    @Test
    fun testExtractSnapshot_concurrency_returnsNullIfInProgress() = runTest {
        val clip = createMockClip("content://mock/video.mp4", 1000L)

        every { mockPrefs.snapshotFormatFlow } returns flowOf("JPEG")
        every { mockPrefs.jpgQualityFlow } returns flowOf(90)

        // Simulate a long-running extraction
        coEvery { mockSnapshotUseCase.execute(any(), any(), any(), any()) } coAnswers {
            delay(1000)
            ExtractSnapshotUseCase.Result.Success("snapshot.jpg")
        }

        var result1: ExtractSnapshotUseCase.Result? = null
        var result2: ExtractSnapshotUseCase.Result? = null

        launch {
            result1 = exportController.extractSnapshot(clip, 500L)
        }

        // Ensure the first one has started and set the state
        advanceTimeBy(100)
        assertTrue(exportController.isSnapshotInProgress.value)

        // The second one should return null immediately
        result2 = exportController.extractSnapshot(clip, 600L)

        advanceUntilIdle()

        assertTrue(result1 is ExtractSnapshotUseCase.Result.Success)
        assertNull(result2)
        assertFalse(exportController.isSnapshotInProgress.value)
    }

    @Test
    fun testExtractSnapshot_resetsInProgressOnFailure() = runTest {
        val clip = createMockClip("content://mock/video.mp4", 1000L)

        every { mockPrefs.snapshotFormatFlow } returns flowOf("JPEG")
        every { mockPrefs.jpgQualityFlow } returns flowOf(90)
        coEvery { mockSnapshotUseCase.execute(any(), any(), any(), any()) } throws RuntimeException("Extraction failed")

        try {
            exportController.extractSnapshot(clip, 500L)
            fail("Expected exception")
        } catch (e: Exception) {
            assertEquals("Extraction failed", e.message)
        }

        assertFalse(exportController.isSnapshotInProgress.value)
    }
}
