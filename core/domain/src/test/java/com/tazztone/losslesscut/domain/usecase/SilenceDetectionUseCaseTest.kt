package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class SilenceDetectionUseCaseTest {

    private val repository = mockk<IVideoEditingRepository>()
    private val useCase = SilenceDetectionUseCase(repository, Dispatchers.Unconfined)

    @Test
    fun testApplySilenceDetection() {
        val clip = MediaClip(
            uri = "uri",
            fileName = "file",
            durationMs = 1000,
            width = 0,
            height = 0,
            videoMime = null,
            audioMime = null,
            sampleRate = 0,
            channelCount = 0,
            fps = 0f,
            rotation = 0,
            isAudioOnly = false,
            segments = listOf(TrimSegment(startMs = 0, endMs = 1000))
        )
        
        // Silence between 400 and 600
        val silenceRanges = listOf(400L..600L)
        
        val updatedClip = useCase.applySilenceDetection(clip, silenceRanges, 100L)
        
        // Should result in 3 segments: 
        // 0-400 (KEEP), 400-600 (DISCARD), 600-1000 (KEEP)
        assertEquals(3, updatedClip.segments.size)
        assertEquals(0L, updatedClip.segments[0].startMs)
        assertEquals(400L, updatedClip.segments[0].endMs)
        assertEquals(SegmentAction.KEEP, updatedClip.segments[0].action)
        
        assertEquals(400L, updatedClip.segments[1].startMs)
        assertEquals(600L, updatedClip.segments[1].endMs)
        assertEquals(SegmentAction.DISCARD, updatedClip.segments[1].action)
        
        assertEquals(600L, updatedClip.segments[2].startMs)
        assertEquals(1000L, updatedClip.segments[2].endMs)
        assertEquals(SegmentAction.KEEP, updatedClip.segments[2].action)
    }
}
