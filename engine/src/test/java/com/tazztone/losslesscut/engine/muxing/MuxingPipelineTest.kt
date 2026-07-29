package com.tazztone.losslesscut.engine.muxing

import android.content.ContentResolver
import android.content.Context
import com.tazztone.losslesscut.domain.engine.IMediaFinalizer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
public class MuxingPipelineTest {

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val dataSource = mockk<MediaDataSource>(relaxed = true)
    private val inspector = mockk<TrackInspector>(relaxed = true)
    private val timeMapper = SampleTimeMapper()
    private val mergeValidator = MergeValidator()
    private val mediaFinalizer = mockk<IMediaFinalizer>(relaxed = true)
    private lateinit var pipeline: MuxingPipeline

    @Before
    public fun setUp() {
        every { context.contentResolver } returns contentResolver
        pipeline = MuxingPipeline(
            context = context,
            dataSource = dataSource,
            inspector = inspector,
            timeMapper = timeMapper,
            mergeValidator = mergeValidator,
            mediaFinalizer = mediaFinalizer
        )
    }

    @Test
    public fun executeCutFailsWhenEndMsIsBeforeStartMs() = runBlocking {
        val request = MuxingCutRequest(
            inputUri = "content://media/1",
            outputUri = "content://media/2",
            startMs = 5000L,
            endMs = 1000L
        )

        val result = pipeline.executeCut(request)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    public fun executeMergeFailsWhenClipsListIsEmpty() = runBlocking {
        val request = MuxingMergeRequest(
            outputUri = "content://media/out",
            clips = emptyList()
        )

        val result = pipeline.executeMerge(request)
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }
}
