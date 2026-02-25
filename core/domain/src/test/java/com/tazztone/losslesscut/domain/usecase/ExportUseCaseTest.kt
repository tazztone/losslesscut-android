package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

internal class ExportUseCaseTest {

    private lateinit var repository: IVideoEditingRepository
    private lateinit var useCase: ExportUseCase

    @Before
    internal fun setup() {
        repository = mockk()
        useCase = ExportUseCase(repository, Dispatchers.Unconfined)
    }

    private fun createDummyClip(
        uri: String = "uri",
        fileName: String = "test.mp4",
        segments: List<TrimSegment> = listOf(TrimSegment(startMs = 0, endMs = 1000L))
    ): MediaClip = MediaClip(
        uri = uri,
        fileName = fileName,
        durationMs = 1000L,
        width = 1920,
        height = 1080,
        videoMime = "video/mp4",
        audioMime = "audio/mp4",
        sampleRate = 44100,
        channelCount = 2,
        fps = 30f,
        rotation = 0,
        isAudioOnly = false,
        segments = segments
    )

    @Test
    internal fun `execute cutSegments emits success when all segments succeed`() = runTest {
        val clip = createDummyClip(segments = listOf(
            TrimSegment(startMs = 0, endMs = 500, action = SegmentAction.KEEP),
            TrimSegment(startMs = 500, endMs = 1000, action = SegmentAction.KEEP)
        ))
        val params = ExportUseCase.Params(
            clips = listOf(clip),
            selectedClipIndex = 0,
            isLossless = true,
            keepAudio = true,
            keepVideo = true,
            rotationOverride = null,
            mergeSegments = false
        )

        coEvery { repository.createMediaOutputUri(any(), any()) } returns "output_uri"
        coEvery { repository.executeLosslessCut(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success("path")

        val results = useCase.execute(params).toList()

        assertTrue(results.any { it is ExportUseCase.Result.Progress })
        assertTrue(results.last() is ExportUseCase.Result.Success)
        assertEquals(2, (results.last() as ExportUseCase.Result.Success).count)
    }

    @Test
    internal fun `execute cutSegments emits failure when repository fails`() = runTest {
        val clip = createDummyClip(segments = listOf(
            TrimSegment(startMs = 0, endMs = 500, action = SegmentAction.KEEP)
        ))
        val params = ExportUseCase.Params(
            clips = listOf(clip),
            selectedClipIndex = 0,
            isLossless = true,
            keepAudio = true,
            keepVideo = true,
            rotationOverride = null,
            mergeSegments = false
        )

        coEvery { repository.createMediaOutputUri(any(), any()) } returns "output_uri"
        coEvery { repository.executeLosslessCut(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.failure(Exception("Cut failed"))

        val results = useCase.execute(params).toList()

        assertTrue(results.last() is ExportUseCase.Result.Failure)
        assertTrue((results.last() as ExportUseCase.Result.Failure).error.contains("failed"))
    }

    @Test
    internal fun `execute mergeSegments emits success when repository succeeds`() = runTest {
        val clip = createDummyClip(segments = listOf(
            TrimSegment(startMs = 0, endMs = 500, action = SegmentAction.KEEP)
        ))
        val params = ExportUseCase.Params(
            clips = listOf(clip),
            selectedClipIndex = 0,
            isLossless = true,
            keepAudio = true,
            keepVideo = true,
            rotationOverride = null,
            mergeSegments = true
        )

        coEvery { repository.createMediaOutputUri(any(), any()) } returns "output_uri"
        coEvery { repository.executeLosslessMerge(any(), any(), any(), any(), any(), any()) } returns Result.success("path")

        val results = useCase.execute(params).toList()

        assertTrue(results.any { it is ExportUseCase.Result.Progress })
        assertTrue(results.last() is ExportUseCase.Result.Success)
        assertEquals(1, (results.last() as ExportUseCase.Result.Success).count)
    }
    
    @Test
    internal fun `execute fails when no KEEP segments found`() = runTest {
        val clip = createDummyClip(segments = listOf(
            TrimSegment(startMs = 0, endMs = 1000, action = SegmentAction.DISCARD)
        ))
        val params = ExportUseCase.Params(
            clips = listOf(clip),
            selectedClipIndex = 0,
            isLossless = true,
            keepAudio = true,
            keepVideo = true,
            rotationOverride = null,
            mergeSegments = false
        )

        val results = useCase.execute(params).toList()

        assertTrue(results.last() is ExportUseCase.Result.Failure)
        assertEquals("No tracks found to cut", (results.last() as ExportUseCase.Result.Failure).error)
    }
}
