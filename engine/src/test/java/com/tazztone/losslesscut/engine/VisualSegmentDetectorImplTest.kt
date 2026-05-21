package com.tazztone.losslesscut.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.tazztone.losslesscut.engine.muxing.MediaDataSource
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VisualSegmentDetectorImplTest {

    private val dataSource = mockk<MediaDataSource>(relaxed = true)
    private lateinit var detector: VisualSegmentDetectorImpl

    @Before
    fun setUp() {
        mockkConstructor(MediaExtractor::class)
        mockkStatic(MediaCodec::class)
        detector = VisualSegmentDetectorImpl(dataSource)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `analyze handles successful detection loop`() = runBlocking {
        val format = mockk<MediaFormat>(relaxed = true)
        
        every { anyConstructed<MediaExtractor>().trackCount } returns 1
        every { anyConstructed<MediaExtractor>().getTrackFormat(0) } returns format
        every { format.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        every { format.getInteger(MediaFormat.KEY_WIDTH) } returns 1920
        every { format.getInteger(MediaFormat.KEY_HEIGHT) } returns 1080
        every { anyConstructed<MediaExtractor>().selectTrack(0) } returns Unit
        every { anyConstructed<MediaExtractor>().readSampleData(any(), any()) } returns 100 andThen 100 andThen -1
        every { anyConstructed<MediaExtractor>().sampleTime } returns 0L andThen 1000000L
        every { anyConstructed<MediaExtractor>().advance() } returns true
        every { anyConstructed<MediaExtractor>().release() } returns Unit

        val mockCodec = mockk<MediaCodec>(relaxed = true)
        every { MediaCodec.createDecoderByType(any()) } returns mockCodec
        
        every { mockCodec.dequeueInputBuffer(any()) } returns 0 andThen 1 andThen -1
        every { mockCodec.getInputBuffer(any()) } returns ByteBuffer.allocate(1024)
        
        var outputCallCount = 0
        every { mockCodec.dequeueOutputBuffer(any(), any()) } answers {
            val i = firstArg<MediaCodec.BufferInfo>()
            when (outputCallCount++) {
                0 -> {
                    i.set(0, 100, 0, 0)
                    0
                }
                1 -> {
                    i.set(0, 100, 1000000, 0)
                    0
                }
                2 -> {
                    i.set(0, 0, 2000000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    0
                }
                else -> -1
            }
        }
        
        every { mockCodec.getOutputBuffer(any()) } returns ByteBuffer.allocate(1920 * 1080 * 3 / 2)
        every { mockCodec.getOutputFormat(any()) } returns format

        var progressCount = 0
        val result = detector.analyze("test_uri", 1000) { current, total -> 
            progressCount++
        }

        assertTrue("Results should not be empty", result.isNotEmpty())
        assertEquals(2, progressCount)
        verify { mockCodec.start() }
        verify { mockCodec.release() }
    }

    @Test
    fun `analyze handles MediaCodec exception gracefully`() = runBlocking {
        val format = mockk<MediaFormat>()
        
        every { anyConstructed<MediaExtractor>().trackCount } returns 1
        every { anyConstructed<MediaExtractor>().getTrackFormat(0) } returns format
        every { format.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        every { format.getLong(MediaFormat.KEY_DURATION) } returns 1000000L
        every { anyConstructed<MediaExtractor>().selectTrack(0) } returns Unit
        every { anyConstructed<MediaExtractor>().release() } returns Unit

        every { MediaCodec.createDecoderByType(any()) } throws IllegalStateException("Codec failed")

        val result = detector.analyze("test_uri", 1000) { _, _ -> }

        assertTrue(result.isEmpty())
        verify { anyConstructed<MediaExtractor>().release() }
    }

    @Test
    fun `analyze handles exception during loop`() = runBlocking {
        val format = mockk<MediaFormat>(relaxed = true)

        every { anyConstructed<MediaExtractor>().trackCount } returns 1
        every { anyConstructed<MediaExtractor>().getTrackFormat(0) } returns format
        every { format.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        every { format.getLong(MediaFormat.KEY_DURATION) } returns 1000000L
        every { anyConstructed<MediaExtractor>().selectTrack(0) } returns Unit

        val mockCodec = mockk<MediaCodec>(relaxed = true)
        every { MediaCodec.createDecoderByType(any()) } returns mockCodec

        // Throw IllegalStateException during loop to simulate codec failure
        every { mockCodec.dequeueInputBuffer(any()) } throws IllegalStateException("Simulated Codec Error")

        val result = detector.analyze("test_uri", 1000) { _, _ -> }

        assertTrue(result.isEmpty())
        verify { mockCodec.stop() }
        verify { mockCodec.release() }
        verify { anyConstructed<MediaExtractor>().release() }
    }

    @Test
    fun `analyze handles generic Exception during loop`() = runBlocking {
        val format = mockk<MediaFormat>(relaxed = true)

        every { anyConstructed<MediaExtractor>().trackCount } returns 1
        every { anyConstructed<MediaExtractor>().getTrackFormat(0) } returns format
        every { format.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        every { format.getLong(MediaFormat.KEY_DURATION) } returns 1000000L
        every { anyConstructed<MediaExtractor>().selectTrack(0) } returns Unit

        val mockCodec = mockk<MediaCodec>(relaxed = true)
        every { MediaCodec.createDecoderByType(any()) } returns mockCodec

        // Throw generic exception during loop
        every { mockCodec.dequeueInputBuffer(any()) } throws RuntimeException("Unexpected error")

        val result = detector.analyze("test_uri", 1000) { _, _ -> }

        assertTrue(result.isEmpty())
        verify { mockCodec.stop() }
        verify { mockCodec.release() }
        verify { anyConstructed<MediaExtractor>().release() }
    }

    @Test
    fun `analyze returns empty list when no video track is found`() = runBlocking {
        val format = mockk<MediaFormat>()
        
        every { anyConstructed<MediaExtractor>().trackCount } returns 1
        every { anyConstructed<MediaExtractor>().getTrackFormat(0) } returns format
        every { format.getString(MediaFormat.KEY_MIME) } returns "audio/mp4"
        every { anyConstructed<MediaExtractor>().release() } returns Unit

        val result = detector.analyze("test_uri", 1000) { _, _ -> }
        
        assertTrue(result.isEmpty())
        verify(exactly = 0) { anyConstructed<MediaExtractor>().selectTrack(any()) }
    }
}
