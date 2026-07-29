package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.VisualDetectionConfig
import com.tazztone.losslesscut.domain.model.VisualStrategy
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
internal class SegmentDetectorUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private val visualDetector = mockk<IVisualSegmentDetector>()
    private lateinit var segmentDetector: SegmentDetectorUseCase

    @Before
    internal fun setUp() {
        segmentDetector = SegmentDetectorUseCase(
            visualDetector,
            testDispatcher
        )
    }

    @Test
    internal fun testDetectVisual_cacheMissAndHit() = runTest(testDispatcher) {
        val uri = "content://mock/1.mp4"
        val config = VisualDetectionConfig(
            strategy = VisualStrategy.BLACK_FRAMES,
            sensitivityThreshold = 0.1f,
            sampleIntervalMs = 1000L,
            minSegmentDurationMs = 100L
        )
        val analyses = listOf(
            FrameAnalysis(timeMs = 0L, meanLuma = 0.05, blurVariance = 10.0, sceneDistance = null, freezeDiff = null),
            FrameAnalysis(timeMs = 1000L, meanLuma = 0.2, blurVariance = 10.0, sceneDistance = null, freezeDiff = null)
        )

        coEvery { visualDetector.analyze(uri, 1000L, any()) } returns analyses

        var progressCalls = 0
        var rangesResult: List<LongRange>? = null

        // 1. First run: cache miss
        segmentDetector.detectVisual(
            scope = this,
            uri = uri,
            config = config,
            listener = object : VisualDetectionListener {
                override fun onProgress(progress: Pair<Int, Int>?) {
                    progressCalls++
                }
                override fun onComplete(ranges: List<LongRange>) {
                    rangesResult = ranges
                }
                override fun onError(error: Throwable) {
                    fail("Should not fail: ${error.message}")
                }
            }
        )
        advanceUntilIdle()

        assertNotNull(rangesResult)
        assertEquals(1, rangesResult!!.size) // Frame 0 is under 0.1 luma (BLACK_FRAMES)
        assertEquals(0L..100L, rangesResult!![0]) // expanded to minSegmentDurationMs
        assertTrue(segmentDetector.hasCachedAnalysis())

        // 2. Second run: cache hit (should not call analyze again)
        coEvery { visualDetector.analyze(any(), any(), any()) } throws IllegalStateException("Should not call analyze again")
        var hitRangesResult: List<LongRange>? = null

        segmentDetector.detectVisual(
            scope = this,
            uri = uri,
            config = config,
            listener = object : VisualDetectionListener {
                override fun onComplete(ranges: List<LongRange>) {
                    hitRangesResult = ranges
                }
                override fun onError(error: Throwable) {
                    fail("Should not fail: ${error.message}")
                }
            }
        )
        advanceUntilIdle()

        assertNotNull(hitRangesResult)
        assertEquals(1, hitRangesResult!!.size)
        assertEquals(0L..100L, hitRangesResult!![0])

        // 3. Clear cache
        segmentDetector.clearCache()
        assertFalse(segmentDetector.hasCachedAnalysis())
    }

    @Test
    internal fun testCancelVisual_preventsOnComplete() = runTest(testDispatcher) {
        val uri = "content://mock/cancel.mp4"
        val config = VisualDetectionConfig(
            strategy = VisualStrategy.BLACK_FRAMES,
            sensitivityThreshold = 0.1f,
            sampleIntervalMs = 1000L,
            minSegmentDurationMs = 100L
        )

        coEvery { visualDetector.analyze(uri, 1000L, any()) } coAnswers {
            kotlinx.coroutines.delay(500)
            emptyList()
        }

        var completed = false

        segmentDetector.detectVisual(
            scope = this,
            uri = uri,
            config = config,
            listener = object : VisualDetectionListener {
                override fun onComplete(ranges: List<LongRange>) {
                    completed = true
                }
            }
        )

        segmentDetector.cancelVisual()
        advanceUntilIdle()

        assertFalse("onComplete should not be called when cancelled", completed)
        assertFalse(segmentDetector.hasCachedAnalysis())
    }
}
