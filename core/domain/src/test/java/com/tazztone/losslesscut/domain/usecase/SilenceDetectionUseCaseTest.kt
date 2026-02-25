package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.DetectionUtils
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.model.WaveformResult
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

public class SilenceDetectionUseCaseTest {

    private val repository = mockk<IVideoEditingRepository>()
    private val useCase = SilenceDetectionUseCase(repository, Dispatchers.Unconfined)

    @Test
    public fun testApplySilenceDetection(): Unit {
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

    @Test
    public fun testApplySilenceDetection_AllSilent(): Unit {
        val clip = createClip(1000)
        val silenceRanges = listOf(0L..1000L)
        val updatedClip = useCase.applySilenceDetection(clip, silenceRanges, 100L)
        
        // Safety net: should NOT be empty, should return a full KEEP segment
        assertEquals(1, updatedClip.segments.size)
        assertEquals(SegmentAction.KEEP, updatedClip.segments[0].action)
        assertEquals(0L, updatedClip.segments[0].startMs)
        assertEquals(1000L, updatedClip.segments[0].endMs)
    }

    @Test
    public fun testApplySilenceDetection_BoundaryClamping(): Unit {
        val clip = createClip(1000)
        // Silence starts at 5ms (should clamp to 0)
        // Silence ends at 995ms (should clamp to 1000)
        val silenceRanges = listOf(5L..995L)
        val updatedClip = useCase.applySilenceDetection(clip, silenceRanges, 100L)
        
        // Results in 1 KEEP segment if both clamp to 0..1000 DISCARD and safety net kicks in
        assertEquals(1, updatedClip.segments.size)
        assertEquals(SegmentAction.KEEP, updatedClip.segments[0].action)
    }

    @Test
    public fun testApplySilenceDetection_TinyKeepAtEnd(): Unit {
        val clip = createClip(1000)
        // Silence ends at 992ms, leaving 8ms KEEP at the end (threshold 10ms)
        val silenceRanges = listOf(100L..992L)
        val updatedClip = useCase.applySilenceDetection(clip, silenceRanges, 100L)
        
        // 0..100 KEEP, 100..1000 DISCARD (992 clamped to 1000)
        assertEquals(2, updatedClip.segments.size)
        assertEquals(SegmentAction.DISCARD, updatedClip.segments[1].action)
        assertEquals(1000L, updatedClip.segments[1].endMs)
    }

    @Test
    public fun testFindSilence_PaddingDoesNotCreateGhostSegments(): Unit = kotlinx.coroutines.runBlocking {
        // Waveform with two silent periods separated by a 100ms gap
        // Buckets: 10ms each. 
        // 0..400 clean silence (0.0), 400..500 noise (1.0), 500..1000 clean silence (0.0)
        val waveform = FloatArray(100) { i ->
            if (i in 40 .. 49) 1.0f else 0.0f
        }
        val waveformResult = WaveformResult(waveform, 1.0f, 1000_000L)
        
        // config with 200ms padding. 
        // If padding was applied BEFORE merge:
        // Range 1 (0..400) becomes 0..200 (shrink by 200 at end)
        // Range 2 (500..1000) becomes 700..1000 (shrink by 200 at start)
        // New gap is 200..700 = 500ms. 
        // If minSegmentMs is 400ms, they would NOT be merged.
        
        // With NEW pipeline (Merge BEFORE Pad):
        // Raw Range 1: 0..400, Raw Range 2: 500..1000. Gap = 100ms.
        // Gaps < 400ms are merged. 
        // Merged Range: 0..1000.
        // Then padding applied to 0..1000 (edge-aware) -> 0..1000.
        
        val config = DetectionUtils.SilenceDetectionConfig(
            threshold = 0.5f,
            minSilenceMs = 50L,
            paddingStartMs = 200L,
            paddingEndMs = 200L
        )
        
        val silenceRanges = useCase.findSilence(waveformResult, config, 400L)
        
        // Should be merged into one single range covering everything
        assertEquals(1, silenceRanges.size)
        assertEquals(0L..1000L, silenceRanges[0])
    }

    private fun createClip(durationMs: Long) = MediaClip(
        uri = "uri", fileName = "file", durationMs = durationMs,
        width = 0, height = 0, videoMime = null, audioMime = null,
        sampleRate = 0, channelCount = 0, fps = 0f, rotation = 0,
        isAudioOnly = false, segments = listOf(TrimSegment(startMs = 0, endMs = durationMs))
    )
}
