package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

internal class ClipManagementUseCaseTest {

    private lateinit var repository: IVideoEditingRepository
    private lateinit var useCase: ClipManagementUseCase

    @Before
    internal fun setup() {
        repository = mockk()
        useCase = ClipManagementUseCase(repository, Dispatchers.Unconfined)
    }

    private fun createDummyClip(
        uri: String = "uri",
        fileName: String = "test.mp4",
        durationMs: Long = 1000L,
        segments: List<TrimSegment> = listOf(TrimSegment(startMs = 0, endMs = 1000L))
    ): MediaClip = MediaClip(
        uri = uri,
        fileName = fileName,
        durationMs = durationMs,
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
    internal fun `splitSegment succeeds when position is within bounds and respects min duration`() {
        val segmentId = UUID.randomUUID()
        val originalSegment = TrimSegment(segmentId, 0L, 1000L, SegmentAction.KEEP)
        val clip = createDummyClip(segments = listOf(originalSegment))

        val result = useCase.splitSegment(clip, 500L, 100L)

        assertNotNull(result)
        assertEquals(2, result!!.segments.size)
        assertEquals(0L, result.segments[0].startMs)
        assertEquals(500L, result.segments[0].endMs)
        assertEquals(500L, result.segments[1].startMs)
        assertEquals(1000L, result.segments[1].endMs)
        assertNotEquals(result.segments[0].id, result.segments[1].id)
    }

    @Test
    internal fun `splitSegment fails when position is too close to start`() {
        val segmentId = UUID.randomUUID()
        val originalSegment = TrimSegment(segmentId, 0L, 1000L, SegmentAction.KEEP)
        val clip = createDummyClip(segments = listOf(originalSegment))

        val result = useCase.splitSegment(clip, 50L, 100L)

        assertNull(result)
    }

    @Test
    internal fun `splitSegment fails when position is too close to end`() {
        val segmentId = UUID.randomUUID()
        val originalSegment = TrimSegment(segmentId, 0L, 1000L, SegmentAction.KEEP)
        val clip = createDummyClip(segments = listOf(originalSegment))

        val result = useCase.splitSegment(clip, 950L, 100L)

        assertNull(result)
    }

    @Test
    internal fun `markSegmentDiscarded toggles action correctly`() {
        val segmentId = UUID.randomUUID()
        val seg1 = TrimSegment(segmentId, 0L, 500L, SegmentAction.KEEP)
        val seg2 = TrimSegment(UUID.randomUUID(), 500L, 1000L, SegmentAction.KEEP)
        val clip = createDummyClip(segments = listOf(seg1, seg2))

        val result = useCase.markSegmentDiscarded(clip, segmentId)

        assertNotNull(result)
        assertEquals(SegmentAction.DISCARD, result!!.segments.find { it.id == segmentId }?.action)

        val toggleBack = useCase.markSegmentDiscarded(result, segmentId)
        assertNotNull(toggleBack)
        assertEquals(SegmentAction.KEEP, toggleBack!!.segments.find { it.id == segmentId }?.action)
    }

    @Test
    internal fun `markSegmentDiscarded fails for the last KEEP segment`() {
        val segmentId = UUID.randomUUID()
        val seg1 = TrimSegment(segmentId, 0L, 1000L, SegmentAction.KEEP)
        val clip = createDummyClip(segments = listOf(seg1))

        val result = useCase.markSegmentDiscarded(clip, segmentId)

        assertNull(result)
    }

    @Test
    internal fun `updateSegmentBounds modifies correct segment`() {
        val segmentId = UUID.randomUUID()
        val seg1 = TrimSegment(segmentId, 0L, 500L, SegmentAction.KEEP)
        val clip = createDummyClip(segments = listOf(seg1))

        val result = useCase.updateSegmentBounds(clip, segmentId, 100L, 600L)

        assertEquals(100L, result.segments[0].startMs)
        assertEquals(600L, result.segments[0].endMs)
    }

    @Test
    internal fun `reorderClips shifts clips correctly`() {
        val clip1 = createDummyClip(uri = "uri1", fileName = "1.mp4")
        val clip2 = createDummyClip(uri = "uri2", fileName = "2.mp4")
        val clip3 = createDummyClip(uri = "uri3", fileName = "3.mp4")
        val clips = listOf(clip1, clip2, clip3)

        val result = useCase.reorderClips(clips, 0, 2)

        assertEquals(listOf(clip2, clip3, clip1), result)
    }

    @Test
    internal fun `createClips returns successful list`() = runTest {
        val uri = "test_uri"
        val expectedClip = createDummyClip(uri = uri)
        coEvery { repository.createClipFromUri(any()) } returns Result.success(expectedClip)

        val result = useCase.createClips(listOf(uri))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(expectedClip, result.getOrThrow()[0])
    }
}
