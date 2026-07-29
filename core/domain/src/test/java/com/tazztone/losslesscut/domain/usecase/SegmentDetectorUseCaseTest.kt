package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.VisualDetectionConfig
import com.tazztone.losslesscut.domain.model.VisualStrategy
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
            visualSegmentDetector = visualDetector,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    internal fun testDetectVisual_cacheMissAndHit() = runTest(testDispatcher) {
        val uri = "content://mock/1.mp4"
        val config = VisualDetectionConfig(
            strategy = VisualStrategy.BLACK_FRAMES,
            sensitivityThreshold = 0.1f,
            sampleIntervalFrames = 5,
            minSegmentDurationMs = 100L
        )
        val analyses = listOf(
            FrameAnalysis(timeMs = 0L, meanLuma = 0.05, blurVariance = 10.0, sceneDistance = null, freezeDiff = null),
            FrameAnalysis(timeMs = 1000L, meanLuma = 0.2, blurVariance = 10.0, sceneDistance = null, freezeDiff = null)
        )

        coEvery { visualDetector.analyze(uri = eq(uri), sampleIntervalFrames = eq(5), strategy = any(), onProgress = any()) } returns analyses

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
        coEvery { visualDetector.analyze(any(), any(), any(), any()) } throws IllegalStateException("Should not call analyze again")
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
            sampleIntervalFrames = 5,
            minSegmentDurationMs = 100L
        )

        coEvery { visualDetector.analyze(uri = eq(uri), sampleIntervalFrames = eq(5), strategy = any(), onProgress = any()) } coAnswers {
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

    @Test
    internal fun testSupersededDetection_cannotPublishOldResult() = runTest(testDispatcher) {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstUri = "content://mock/first.mp4"
        val secondUri = "content://mock/second.mp4"
        val config = VisualDetectionConfig(
            strategy = VisualStrategy.BLACK_FRAMES,
            sensitivityThreshold = 0.1f,
            sampleIntervalFrames = 5,
            minSegmentDurationMs = 100L
        )

        coEvery {
            visualDetector.analyze(
                uri = any(),
                sampleIntervalFrames = any(),
                strategy = any(),
                onProgress = any()
            )
        } coAnswers {
            if (firstArg<String>() == firstUri) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) {
                    releaseFirst.await()
                }
            }
            emptyList()
        }

        var firstCompleted = false
        var secondCompleted = false
        segmentDetector.detectVisual(
            scope = this,
            uri = firstUri,
            config = config,
            listener = object : VisualDetectionListener {
                override fun onComplete(ranges: List<LongRange>) {
                    firstCompleted = true
                }
            }
        )
        runCurrent()
        assertTrue(firstStarted.isCompleted)

        segmentDetector.detectVisual(
            scope = this,
            uri = secondUri,
            config = config,
            listener = object : VisualDetectionListener {
                override fun onComplete(ranges: List<LongRange>) {
                    secondCompleted = true
                }
            }
        )
        advanceUntilIdle()
        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertFalse(firstCompleted)
        assertTrue(secondCompleted)
    }
}
