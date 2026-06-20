package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.usecase.ClipManagementUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ClipControllerTest {

    private val mockClipManagementUseCase = mockk<ClipManagementUseCase>()
    private lateinit var clipController: ClipController

    @Before
    fun setUp() {
        clipController = ClipController(mockClipManagementUseCase)
    }

    private fun createMockClip(segments: List<TrimSegment>): MediaClip {
        return MediaClip(
            id = UUID.randomUUID(),
            uri = "content://mock/video1.mp4",
            fileName = "test.mp4",
            durationMs = 10000L,
            width = 1920,
            height = 1080,
            videoMime = "video/mp4",
            audioMime = "audio/aac",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = false,
            segments = segments
        )
    }

    @Test
    fun splitSegment_happyPath_delegatesToUseCase() {
        // Arrange
        val segment = TrimSegment(startMs = 0, endMs = 1000)
        val clip = createMockClip(listOf(segment))
        val positionMs = 500L
        val expectedClip = clip.copy(segments = listOf(
            TrimSegment(startMs = 0, endMs = 500),
            TrimSegment(startMs = 500, endMs = 1000)
        ))

        every {
            mockClipManagementUseCase.splitSegment(clip, positionMs, ClipController.MIN_SEGMENT_DURATION_MS)
        } returns expectedClip

        // Act
        val result = clipController.splitSegment(clip, positionMs)

        // Assert
        assertEquals(expectedClip, result)
        verify { mockClipManagementUseCase.splitSegment(clip, positionMs, ClipController.MIN_SEGMENT_DURATION_MS) }
    }

    @Test
    fun splitSegment_tooCloseToStart_returnsNull() {
        // Arrange
        val segment = TrimSegment(startMs = 0, endMs = 1000)
        val clip = createMockClip(listOf(segment))
        val positionMs = 50L // < MIN_SEGMENT_DURATION_MS (100)

        // Act
        val result = clipController.splitSegment(clip, positionMs)

        // Assert
        assertNull(result)
        verify(exactly = 0) { mockClipManagementUseCase.splitSegment(any(), any(), any()) }
    }

    @Test
    fun splitSegment_tooCloseToEnd_returnsNull() {
        // Arrange
        val segment = TrimSegment(startMs = 0, endMs = 1000)
        val clip = createMockClip(listOf(segment))
        val positionMs = 950L // 1000 - 950 = 50 < MIN_SEGMENT_DURATION_MS (100)

        // Act
        val result = clipController.splitSegment(clip, positionMs)

        // Assert
        assertNull(result)
        verify(exactly = 0) { mockClipManagementUseCase.splitSegment(any(), any(), any()) }
    }

    @Test
    fun splitSegment_positionOutOfBounds_returnsNull() {
        // Arrange
        val segment = TrimSegment(startMs = 0, endMs = 1000)
        val clip = createMockClip(listOf(segment))
        val positionMs = 2000L // out of segment bounds

        // Act
        val result = clipController.splitSegment(clip, positionMs)

        // Assert
        assertNull(result)
        verify(exactly = 0) { mockClipManagementUseCase.splitSegment(any(), any(), any()) }
    }

    @Test
    fun markSegmentDiscarded_delegatesToUseCase() {
        // Arrange
        val segmentId = UUID.randomUUID()
        val clip = createMockClip(emptyList())
        val expectedClip = clip.copy(durationMs = 9999L)

        every { mockClipManagementUseCase.markSegmentDiscarded(clip, segmentId) } returns expectedClip

        // Act
        val result = clipController.markSegmentDiscarded(clip, segmentId)

        // Assert
        assertEquals(expectedClip, result)
        verify { mockClipManagementUseCase.markSegmentDiscarded(clip, segmentId) }
    }

    @Test
    fun reorderClips_delegatesToUseCase() {
        // Arrange
        val clips = listOf(createMockClip(emptyList()), createMockClip(emptyList()))
        val expectedClips = clips.reversed()

        every { mockClipManagementUseCase.reorderClips(clips, 0, 1) } returns expectedClips

        // Act
        val result = clipController.reorderClips(clips, 0, 1)

        // Assert
        assertEquals(expectedClips, result)
        verify { mockClipManagementUseCase.reorderClips(clips, 0, 1) }
    }

    @Test
    fun reorderClips_fromIndexOutOfBounds_returnsOriginalList() {
        // Arrange
        val clips = listOf(createMockClip(emptyList()), createMockClip(emptyList()))

        // Act
        val resultNegative = clipController.reorderClips(clips, -1, 1)
        val resultTooLarge = clipController.reorderClips(clips, 2, 1)

        // Assert
        assertEquals(clips, resultNegative)
        assertEquals(clips, resultTooLarge)
        verify(exactly = 0) { mockClipManagementUseCase.reorderClips(any(), any(), any()) }
    }

    @Test
    fun reorderClips_toIndexOutOfBounds_returnsOriginalList() {
        // Arrange
        val clips = listOf(createMockClip(emptyList()), createMockClip(emptyList()))

        // Act
        val resultNegative = clipController.reorderClips(clips, 0, -1)
        val resultTooLarge = clipController.reorderClips(clips, 0, 3)

        // Assert
        assertEquals(clips, resultNegative)
        assertEquals(clips, resultTooLarge)
        verify(exactly = 0) { mockClipManagementUseCase.reorderClips(any(), any(), any()) }
    }

    @Test
    fun reorderClips_sameIndex_returnsOriginalList() {
        // Arrange
        val clips = listOf(createMockClip(emptyList()), createMockClip(emptyList()))

        // Act
        val result = clipController.reorderClips(clips, 1, 1)

        // Assert
        assertEquals(clips, result)
        verify(exactly = 0) { mockClipManagementUseCase.reorderClips(any(), any(), any()) }
    }

    @Test
    fun updateSegmentBounds_validDuration_delegatesToUseCase() {
        // Arrange
        val segmentId = UUID.randomUUID()
        val clip = createMockClip(emptyList())
        val start = 0L
        val end = 500L
        val expectedClip = clip.copy(durationMs = 9999L)

        every { mockClipManagementUseCase.updateSegmentBounds(clip, segmentId, start, end) } returns expectedClip

        // Act
        val result = clipController.updateSegmentBounds(clip, segmentId, start, end)

        // Assert
        assertEquals(expectedClip, result)
        verify { mockClipManagementUseCase.updateSegmentBounds(clip, segmentId, start, end) }
    }

    @Test
    fun updateSegmentBounds_durationTooShort_coercesEnd() {
        // Arrange
        val segmentId = UUID.randomUUID()
        val clip = createMockClip(emptyList())
        val start = 100L
        val end = 150L // duration is 50 < 100
        val expectedClip = clip.copy(durationMs = 9999L)

        // The controller should coerce `end` to start + MIN_SEGMENT_DURATION_MS
        val expectedEnd = start + ClipController.MIN_SEGMENT_DURATION_MS

        every { mockClipManagementUseCase.updateSegmentBounds(clip, segmentId, start, expectedEnd) } returns expectedClip

        // Act
        val result = clipController.updateSegmentBounds(clip, segmentId, start, end)

        // Assert
        assertEquals(expectedClip, result)
        verify { mockClipManagementUseCase.updateSegmentBounds(clip, segmentId, start, expectedEnd) }
    }
}
