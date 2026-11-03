package com.tazztone.losslesscut.engine.muxing

import android.media.MediaCodec
import android.media.MediaExtractor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ExtractorSampleCopierTest {

    private class Sample(val track: Int, val ptsUs: Long, val sync: Boolean = true, val size: Int = 10)

    /**
     * Stateful MediaExtractor stand-in: samples are yielded in file order, every selected
     * track seeks independently (sync samples only, as the platform does) and a sample that
     * does not fit the buffer is rejected with IllegalArgumentException like the native code.
     */
    private class FakeExtractor(private val samples: List<Sample>) {
        val mock: MediaExtractor = mockk()
        val seeks = mutableListOf<Pair<Long, Int>>()
        private val selected = mutableSetOf<Int>()
        private val trackStart = mutableMapOf<Int, Int>()
        private var pos = -1

        init {
            every { mock.selectTrack(any()) } answers { selected.add(firstArg()); Unit }
            every { mock.unselectTrack(any()) } answers { selected.remove(firstArg()); Unit }
            every { mock.seekTo(any(), any()) } answers { seek(firstArg(), secondArg()) }
            every { mock.sampleTrackIndex } answers { current()?.track ?: -1 }
            every { mock.sampleTime } answers { current()?.ptsUs ?: -1L }
            every { mock.sampleFlags } answers {
                if (current()?.sync == true) MediaExtractor.SAMPLE_FLAG_SYNC else 0
            }
            every { mock.readSampleData(any(), any()) } answers {
                val sample = current() ?: return@answers -1
                require(firstArg<ByteBuffer>().capacity() >= sample.size) { "buffer too small" }
                sample.size
            }
            every { mock.advance() } answers { pos = nextIndex(pos + 1); pos >= 0 }
        }

        private fun visible(i: Int) =
            samples[i].track in selected && i >= (trackStart[samples[i].track] ?: 0)

        private fun nextIndex(from: Int): Int =
            (from until samples.size).firstOrNull { visible(it) } ?: -1

        private fun current() = samples.getOrNull(pos)

        private fun seek(timeUs: Long, mode: Int) {
            seeks += timeUs to mode
            for (track in selected) {
                val syncs = samples.indices.filter { samples[it].track == track && samples[it].sync }
                val idx = when (mode) {
                    MediaExtractor.SEEK_TO_NEXT_SYNC -> syncs.firstOrNull { samples[it].ptsUs >= timeUs }
                    MediaExtractor.SEEK_TO_PREVIOUS_SYNC ->
                        syncs.lastOrNull { samples[it].ptsUs <= timeUs } ?: syncs.firstOrNull()
                    else -> syncs.minByOrNull { kotlin.math.abs(samples[it].ptsUs - timeUs) }
                }
                trackStart[track] = idx ?: samples.size
            }
            pos = nextIndex(0)
        }
    }

    private class Written(val track: Int, val ptsUs: Long, val keyframe: Boolean)

    private val muxerWriter = mockk<MuxerWriter>(relaxed = true)

    private fun written(): List<Written> {
        val tracks = mutableListOf<Int>()
        val infos = mutableListOf<MediaCodec.BufferInfo>()
        verify { muxerWriter.writeSampleData(capture(tracks), any(), capture(infos)) }
        return tracks.indices.map {
            Written(tracks[it], infos[it].presentationTimeUs, infos[it].flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0)
        }
    }

    private fun copier(fake: FakeExtractor) = ExtractorSampleCopier(fake.mock, muxerWriter, SampleTimeMapper())

    private val avPlan = SelectedTrackPlan(
        trackMap = mapOf(0 to 0, 1 to 1),
        isVideoTrackMap = mapOf(0 to true, 1 to false),
        bufferSize = 1024,
        durationUs = 10_000_000L,
        hasVideoTrack = true
    )

    /**
     * 30 fps video with a sync frame every 60 frames and 21.333 ms audio frames. Audio is
     * interleaved ~100 ms ahead of video in file order, as muxers commonly do.
     */
    private fun avStream(durationUs: Long): List<Sample> {
        val video = (0 until durationUs step 33_333L).mapIndexed { i, pts -> Sample(0, pts, sync = i % 60 == 0) }
        val audio = (0 until durationUs step 21_333L).map { Sample(1, it) }
        return (video + audio).sortedBy { if (it.track == 1) it.ptsUs - 100_000L else it.ptsUs }
    }

    @Test
    fun `video starts at the next sync frame and audio is aligned to it`() = runTest {
        val fake = FakeExtractor(avStream(6_000_000L))

        copier(fake).copy(avPlan, startUs = 1_500_000L, endUs = 3_000_000L, initialBuffer = ByteBuffer.allocateDirect(1024))

        val out = written()
        val video = out.filter { it.track == 0 }
        val audio = out.filter { it.track == 1 }
        assertEquals(0L, video.first().ptsUs)
        assertTrue(video.first().keyframe)
        // First audio frame is the first one at or after the sync frame, never before it.
        assertTrue("audio must not lead video: ${audio.first().ptsUs}", audio.first().ptsUs in 0L..21_333L)
        assertEquals(
            listOf(1_500_000L to MediaExtractor.SEEK_TO_NEXT_SYNC, 1_999_980L to MediaExtractor.SEEK_TO_PREVIOUS_SYNC),
            fake.seeks
        )
    }

    @Test
    fun `every track ends independently at endUs`() = runTest {
        // The audio sample at 3,007,953 sits in file order before the last in-range video frames.
        val fake = FakeExtractor(avStream(6_000_000L))

        val last = copier(fake).copy(avPlan, startUs = 0L, endUs = 3_000_000L, initialBuffer = ByteBuffer.allocateDirect(1024))

        val out = written()
        // Video keeps the frame at 3,033,303 as a possible reference and audio follows it there.
        assertEquals(3_033_303L, out.filter { it.track == 0 }.maxOf { it.ptsUs })
        assertEquals(3_029_286L, out.filter { it.track == 1 }.maxOf { it.ptsUs })
        assertEquals(mapOf(0 to 3_033_303L, 1 to 3_029_286L), last)
    }

    @Test
    fun `in-range B-frames after a reference past endUs are kept and audio is extended to it`() = runTest {
        // Decode order of an I B B B P stream at 30 fps, with audio interleaved by decode time.
        val video = listOf(
            Sample(0, 0L, sync = true),
            Sample(0, 133_332L, sync = false),
            Sample(0, 33_333L, sync = false),
            Sample(0, 66_666L, sync = false),
            Sample(0, 99_999L, sync = false),
            Sample(0, 266_664L, sync = false),
            Sample(0, 166_665L, sync = false),
            Sample(0, 199_998L, sync = false),
            Sample(0, 233_331L, sync = false),
            Sample(0, 399_996L, sync = false),
            Sample(0, 299_997L, sync = false),
        )
        val audio = (0 until 400_000L step 21_333L).map { Sample(1, it) }
        // The audio chunk covering the overshoot is interleaved before the reference frame.
        val fake = FakeExtractor(video.take(2) + audio.take(14) + video.drop(2) + audio.drop(14))

        copier(fake).copy(avPlan, startUs = 0L, endUs = 200_000L, initialBuffer = ByteBuffer.allocateDirect(1024))

        val out = written()
        // The reference P at 266,664 is kept, and so is the B at 233,331 that sits between
        // endUs and the reference; the next P at 399,996 ends the track.
        assertEquals(
            listOf(0L, 133_332L, 33_333L, 66_666L, 99_999L, 266_664L, 166_665L, 199_998L, 233_331L),
            out.filter { it.track == 0 }.map { it.ptsUs }
        )
        // Audio continues up to the kept reference frame instead of stopping at 200 ms, even
        // though those audio samples were read before the reference frame.
        assertEquals(255_996L, out.filter { it.track == 1 }.maxOf { it.ptsUs })
        assertEquals(13, out.count { it.track == 1 })
    }

    @Test
    fun `open-GOP leading pictures before the sync frame are dropped instead of clamped`() = runTest {
        // iPhone HEVC layout: audio leads, then the CRA frame, then a RASL picture with an earlier PTS.
        val fake = FakeExtractor(
            listOf(
                Sample(1, 13_054_666L),
                Sample(0, 13_068_333L, sync = true),
                Sample(0, 13_035_000L, sync = false),
                Sample(1, 13_075_999L),
                Sample(0, 13_101_666L, sync = false),
            )
        )

        copier(fake).copy(avPlan, startUs = 13_000_000L, endUs = 20_000_000L, initialBuffer = ByteBuffer.allocateDirect(1024))

        val out = written()
        assertEquals(listOf(0L, 7_666L, 33_333L), out.map { it.ptsUs })
        assertEquals(listOf(0, 1, 0), out.map { it.track })
    }

    @Test
    fun `unsnapped start skips provider pre-roll before the sync frame`() = runTest {
        val fake = FakeExtractor(listOf(Sample(0, 500L, sync = false), Sample(0, 1000L, sync = true), Sample(0, 1600L, sync = true)))
        val plan = avPlan.copy(trackMap = mapOf(0 to 0), isVideoTrackMap = mapOf(0 to true))

        val last = copier(fake).copy(plan, startUs = 750L, endUs = 1500L, initialBuffer = ByteBuffer.allocateDirect(1024))

        assertEquals(750L to MediaExtractor.SEEK_TO_NEXT_SYNC, fake.seeks.first())
        assertEquals(listOf(0L), written().map { it.ptsUs })
        assertEquals(mapOf(0 to 0L), last)
    }

    @Test
    fun `audio-only source seeks to the next sample at startUs`() = runTest {
        val fake = FakeExtractor((0 until 200_000L step 21_333L).map { Sample(1, it) })
        val plan = SelectedTrackPlan(mapOf(1 to 0), mapOf(1 to false), 1024, 200_000L, hasVideoTrack = false)

        copier(fake).copy(plan, startUs = 50_000L, endUs = 120_000L, initialBuffer = ByteBuffer.allocateDirect(1024))

        assertEquals(listOf(50_000L to MediaExtractor.SEEK_TO_NEXT_SYNC), fake.seeks)
        // 63,999 and 85,332 and 106,665 relative to the requested start, nothing before it.
        assertEquals(listOf(13_999L, 35_332L, 56_665L), written().map { it.ptsUs })
    }

    @Test
    fun `nothing is written when no sync frame follows startUs`() = runTest {
        val fake = FakeExtractor(avStream(1_000_000L))

        val last = copier(fake).copy(avPlan, startUs = 500_000L, endUs = 900_000L, initialBuffer = ByteBuffer.allocateDirect(1024))

        assertTrue(last.isEmpty())
        verify(exactly = 0) { muxerWriter.writeSampleData(any(), any(), any()) }
    }

    @Test
    fun `merge offset shifts every written sample and the result stays relative`() = runTest {
        val fake = FakeExtractor(avStream(3_000_000L))

        val last = copier(fake).copy(avPlan, 0L, 100_000L, ByteBuffer.allocateDirect(1024), globalOffsetUs = 2_000_000L)

        val out = written()
        assertEquals(2_000_000L, out.filter { it.track == 0 }.first().ptsUs)
        // Last in-range frame is 99,999; the non-sync frame at 133,332 is kept as a reference.
        assertEquals(2_133_332L, out.filter { it.track == 0 }.last().ptsUs)
        assertEquals(mapOf(0 to 133_332L, 1 to 127_998L), last)
    }

    @Test
    fun `buffer grows when a sample does not fit`() = runTest {
        val fake = FakeExtractor(listOf(Sample(0, 0L, size = 3_000), Sample(0, 33_333L, sync = false, size = 10)))
        val plan = avPlan.copy(trackMap = mapOf(0 to 0), isVideoTrackMap = mapOf(0 to true))

        copier(fake).copy(plan, 0L, 100_000L, ByteBuffer.allocateDirect(1024))

        val buffers = mutableListOf<ByteBuffer>()
        verify(exactly = 2) { muxerWriter.writeSampleData(0, capture(buffers), any()) }
        assertTrue(buffers.all { it.capacity() >= 3_000 })
    }
}
