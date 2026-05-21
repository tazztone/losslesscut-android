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
import org.junit.Assert.assertTrue
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
        
        val updatedClip = useCase.applyDetectionRanges(clip, silenceRanges, 100L, SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES)
        
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
        val updatedClip = useCase.applyDetectionRanges(clip, silenceRanges, 100L, SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES)
        
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
        val updatedClip = useCase.applyDetectionRanges(clip, silenceRanges, 100L, SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES)
        
        // Results in 1 KEEP segment if both clamp to 0..1000 DISCARD and safety net kicks in
        assertEquals(1, updatedClip.segments.size)
        assertEquals(SegmentAction.KEEP, updatedClip.segments[0].action)
    }

    @Test
    public fun testApplySilenceDetection_TinyKeepAtEnd(): Unit {
        val clip = createClip(1000)
        // Silence ends at 992ms, leaving 8ms KEEP at the end (threshold 10ms)
        val silenceRanges = listOf(100L..992L)
        val updatedClip = useCase.applyDetectionRanges(clip, silenceRanges, 100L, SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES)
        
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
        
        val silenceRanges = useCase.findSilence(waveformResult, config, 400L).finalRanges
        
        // Should be merged into one single range covering everything
        assertEquals(1, silenceRanges.size)
        assertEquals(0L..1000L, silenceRanges[0])
    }

    @Test
    public fun testApplySilenceDetection_MinKeepDurationAtEnd(): Unit {
        val clip = createClip(1000)
        // Silence ends at 985ms, leaving 15ms KEEP at the end.
        // If minKeepSegmentDurationMs is 20ms, it should be merged into DISCARD.
        val silenceRanges = listOf(100L..985L)
        val updatedClip = useCase.applyDetectionRanges(clip, silenceRanges, 20L, SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES)
        
        assertEquals(2, updatedClip.segments.size)
        assertEquals(SegmentAction.DISCARD, updatedClip.segments[1].action)
        assertEquals(1000L, updatedClip.segments[1].endMs)
    }

    @Test
    public fun testApplySilenceDetection_SplitMode(): Unit {
        val clip = createClip(1000)
        // Ranges at 300..400 and 700..800
        // Split points at 300 and 700.
        // Segments: 0-300, 300-700, 700-1000 (all KEEP)
        val silenceRanges = listOf(300L..400L, 700L..800L)
        val updatedClip = useCase.applyDetectionRanges(clip, silenceRanges, 100L, SilenceDetectionUseCase.DetectionMode.SPLIT_AT_BOUNDARIES)
        
        assertEquals(3, updatedClip.segments.size)
        assertEquals(0L, updatedClip.segments[0].startMs)
        assertEquals(300L, updatedClip.segments[0].endMs)
        assertEquals(300L, updatedClip.segments[1].startMs)
        assertEquals(700L, updatedClip.segments[1].endMs)
        assertEquals(700L, updatedClip.segments[2].startMs)
        assertEquals(1000L, updatedClip.segments[2].endMs)
        assertTrue(updatedClip.segments.all { it.action == SegmentAction.KEEP })
    }

    @Test
    public fun testApplySilenceDetection_AdjacentRanges(): Unit {
        val clip = createClip(1000)
        // Adjacent ranges: 100-200 and 200-300
        val silenceRanges = listOf(100L..200L, 200L..300L)
        val updatedClip = useCase.applyDetectionRanges(clip, silenceRanges, 10L, SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES)
        
        // Should merge into 0-100 (KEEP), 100-300 (DISCARD), 300-1000 (KEEP)
        assertEquals(3, updatedClip.segments.size)
        assertEquals(SegmentAction.DISCARD, updatedClip.segments[1].action)
        assertEquals(100L, updatedClip.segments[1].startMs)
        assertEquals(300L, updatedClip.segments[1].endMs)
    }

    @Test
    public fun testFindSilence_EmptyInput(): Unit = kotlinx.coroutines.runBlocking {
        val config = DetectionUtils.SilenceDetectionConfig(0.5f, 100L, 0, 0)
        
        val res1 = useCase.findSilence(WaveformResult(floatArrayOf(), 0f, 0L), config, 100L)
        assertTrue(res1.finalRanges.isEmpty())
        
        val res2 = useCase.findSilence(WaveformResult(floatArrayOf(0f), 0f, -1L), config, 100L)
        assertTrue(res2.finalRanges.isEmpty())
    }

    @Test
    public fun testFindSilence_LongWaveformYields(): Unit = kotlinx.coroutines.runBlocking {
        // Test yielding logic with a long waveform
        val waveform = FloatArray(2000) { 0f }
        val waveformResult = WaveformResult(waveform, 1.0f, 2000_000L)
        val config = DetectionUtils.SilenceDetectionConfig(0.5f, 100L, 0, 0)
        
        val result = useCase.findSilence(waveformResult, config, 100L)
        assertEquals(1, result.finalRanges.size)
        assertEquals(0L..2000L, result.finalRanges[0])
    }

    private fun createClip(durationMs: Long) = MediaClip(
        uri = "uri", fileName = "file", durationMs = durationMs,
        width = 0, height = 0, videoMime = null, audioMime = null,
        sampleRate = 0, channelCount = 0, fps = 0f, rotation = 0,
        isAudioOnly = false, segments = listOf(TrimSegment(startMs = 0, endMs = durationMs))
    )

    @Test
    public fun testApplySilenceDetection_BoundaryClampingStartNearEnd(): Unit {
        val clip = createClip(1000)
        // Silence starts at 995ms (should clamp to 1000)
        // Silence ends at 1000ms
        val silenceRanges = listOf(995L..1000L)
        val updatedClip = useCase.applyDetectionRanges(clip, silenceRanges, 100L, SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES)

        // Results in 1 KEEP segment because silence is clamped to 1000..1000, so no DISCARD segment
        assertEquals(1, updatedClip.segments.size)
        assertEquals(SegmentAction.KEEP, updatedClip.segments[0].action)
        assertEquals(1000L, updatedClip.segments[0].endMs)
    }

    @Test
    public fun testApplySilenceDetection_BoundaryClampingEndNearStart(): Unit {
        val clip = createClip(1000)
        // Silence starts at 0ms
        // Silence ends at 5ms (should clamp to 0)
        val silenceRanges = listOf(0L..5L)
        val updatedClip = useCase.applyDetectionRanges(clip, silenceRanges, 100L, SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES)

        // Results in 1 KEEP segment because silence is clamped to 0..0, so no DISCARD segment
        assertEquals(1, updatedClip.segments.size)
        assertEquals(SegmentAction.KEEP, updatedClip.segments[0].action)
        assertEquals(1000L, updatedClip.segments[0].endMs)
    }
}
