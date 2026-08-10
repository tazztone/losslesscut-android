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
import com.tazztone.losslesscut.domain.testutil.MediaClipTestFixture.createDummyClip

internal class ClipManagementUseCaseTest {

    private lateinit var repository: IVideoEditingRepository
    private lateinit var useCase: ClipManagementUseCase

    @Before
    internal fun setup() {
        repository = mockk()
        useCase = ClipManagementUseCase(repository, Dispatchers.Unconfined)
    }

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
    internal fun `splitSegment fails when position is out of segment bounds`() {
        val segmentId = UUID.randomUUID()
        val originalSegment = TrimSegment(segmentId, 0L, 1000L, SegmentAction.KEEP)
        val clip = createDummyClip(segments = listOf(originalSegment))

        val result = useCase.splitSegment(clip, 2000L, 100L)

        assertNull(result)
    }

    @Test
    internal fun `reorderClips returns original list when from index is out of bounds`() {
        val clip1 = createDummyClip(uri = "uri1", fileName = "1.mp4")
        val clip2 = createDummyClip(uri = "uri2", fileName = "2.mp4")
        val clips = listOf(clip1, clip2)

        val resultNegative = useCase.reorderClips(clips, -1, 1)
        val resultTooLarge = useCase.reorderClips(clips, 2, 1)

        assertEquals(clips, resultNegative)
        assertEquals(clips, resultTooLarge)
    }

    @Test
    internal fun `reorderClips returns original list when to index is out of bounds`() {
        val clip1 = createDummyClip(uri = "uri1", fileName = "1.mp4")
        val clip2 = createDummyClip(uri = "uri2", fileName = "2.mp4")
        val clips = listOf(clip1, clip2)

        val resultNegative = useCase.reorderClips(clips, 0, -1)
        val resultTooLarge = useCase.reorderClips(clips, 0, 3)

        assertEquals(clips, resultNegative)
        assertEquals(clips, resultTooLarge)
    }

    @Test
    internal fun `reorderClips returns original list when indices are same`() {
        val clip1 = createDummyClip(uri = "uri1", fileName = "1.mp4")
        val clip2 = createDummyClip(uri = "uri2", fileName = "2.mp4")
        val clips = listOf(clip1, clip2)

        val result = useCase.reorderClips(clips, 1, 1)

        assertEquals(clips, result)
    }

    @Test
    internal fun `updateSegmentBounds coerces end time when duration is too short`() {
        val segmentId = UUID.randomUUID()
        val seg1 = TrimSegment(segmentId, 0L, 500L, SegmentAction.KEEP)
        val clip = createDummyClip(segments = listOf(seg1))

        // Update with short duration (50ms < MIN_SEGMENT_DURATION_MS = 100ms)
        val result = useCase.updateSegmentBounds(clip, segmentId, 100L, 150L)

        assertEquals(100L, result.segments[0].startMs)
        assertEquals(100L + ClipManagementUseCase.MIN_SEGMENT_DURATION_MS, result.segments[0].endMs)
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
