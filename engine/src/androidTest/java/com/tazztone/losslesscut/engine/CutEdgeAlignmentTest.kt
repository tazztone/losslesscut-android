package com.tazztone.losslesscut.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tazztone.losslesscut.domain.engine.IMediaFinalizer
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.engine.muxing.MediaDataSource
import com.tazztone.losslesscut.engine.muxing.MergeValidator
import com.tazztone.losslesscut.engine.muxing.MuxingCutRequest
import com.tazztone.losslesscut.engine.muxing.MuxingMergeRequest
import com.tazztone.losslesscut.engine.muxing.MuxingPipeline
import com.tazztone.losslesscut.engine.muxing.SampleTimeMapper
import com.tazztone.losslesscut.engine.muxing.TrackInspector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real MediaExtractor/MediaMuxer pipeline on bundled clips and checks the
 * edges of the output: video and audio both start at 0, nothing leads the first sync
 * frame, no track is truncated by another, and merged segments are contiguous.
 *
 * Clips: 10 s, 320x240 @ 30 fps, sync frame every 2 s, AAC 48 kHz (21.333 ms frames).
 * `cut_h264_bframes.mp4` has closed GOPs with B-frames; `cut_hevc_opengop.mp4` has
 * open GOPs, so leading pictures precede each CRA frame in presentation order.
 */
@RunWith(AndroidJUnit4::class)
class CutEdgeAlignmentTest {

    private companion object {
        const val AUTHORITY = "com.tazztone.losslesscut.engine.test.provider"
        const val FRAME_US = 33_333L
        const val AUDIO_FRAME_US = 21_334L
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pipeline = MuxingPipeline(
        context, MediaDataSource(context), TrackInspector(), SampleTimeMapper(), MergeValidator(),
        object : IMediaFinalizer {
            override fun finalizeVideo(uri: String) = Unit
            override fun finalizeAudio(uri: String) = Unit
        }
    )

    private class TrackSamples(val ptsUs: List<Long>) {
        val first get() = ptsUs.first()
        val last get() = ptsUs.last()
    }

    private fun asset(name: String): Uri {
        val file = File(context.cacheDir, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    private fun output(name: String): Pair<File, Uri> {
        val file = File(context.cacheDir, name)
        file.delete()
        return file to FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    /** Presentation timestamps per track kind, in presentation order. */
    private fun samplesOf(file: File): Map<String, TrackSamples> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val kinds = (0 until extractor.trackCount).associateWith {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.substringBefore('/')
            }
            kinds.keys.forEach { extractor.selectTrack(it) }
            val pts = kinds.keys.associateWith { mutableListOf<Long>() }
            while (extractor.sampleTrackIndex >= 0) {
                pts.getValue(extractor.sampleTrackIndex).add(extractor.sampleTime)
                if (!extractor.advance()) break
            }
            return pts.entries.associate { (track, list) -> kinds.getValue(track) to TrackSamples(list.sorted()) }
        } finally {
            extractor.release()
        }
    }

    /** True when consecutive timestamps are one frame apart, except for up to [joinAllowance] larger gaps. */
    private fun maxVideoGapIsOneFrame(pts: List<Long>, joinAllowance: Int): Boolean =
        pts.zipWithNext { a, b -> b - a }.count { it > FRAME_US + 1_000 } <= joinAllowance

    private fun clip(uri: Uri, vararg ranges: Pair<Long, Long>) = MediaClip(
        uri = uri.toString(), fileName = "clip.mp4", durationMs = 10_000, width = 320, height = 240,
        videoMime = "video/avc", audioMime = "audio/mp4a-latm", sampleRate = 48_000, channelCount = 1,
        fps = 30f, rotation = 0, isAudioOnly = false,
        segments = ranges.map { TrimSegment(startMs = it.first, endMs = it.second) }
    )

    private fun assertAlignedCut(name: String, startMs: Long, endMs: Long) = runBlocking {
        val (outFile, outUri) = output("out_$name")
        val result = pipeline.executeCut(MuxingCutRequest(asset(name).toString(), outUri.toString(), startMs, endMs))
        assertTrue("cut failed: ${result.exceptionOrNull()}", result.isSuccess)

        val tracks = samplesOf(outFile)
        val video = tracks.getValue("video")
        val audio = tracks.getValue("audio")
        // Sync frame at 2 s is the first at or after the requested 1.5 s start.
        val expectedDurationUs = (endMs - 2_000) * 1_000
        assertEquals("video must start at 0", 0L, video.first)
        assertEquals("no duplicate or clamped video timestamps", video.ptsUs.size, video.ptsUs.toSet().size)
        assertTrue("audio must not lead video (${audio.first})", audio.first in 0L..AUDIO_FRAME_US)
        // The end may overshoot by the one reference frame kept for trailing B-frames.
        assertTrue("video truncated early at ${video.last}", video.last >= expectedDurationUs - FRAME_US)
        assertTrue("video ran past the end at ${video.last}", video.last <= expectedDurationUs + 2 * FRAME_US)
        assertTrue("audio truncated early at ${audio.last}", audio.last >= video.last - 2 * AUDIO_FRAME_US)
        assertTrue("audio ran past video at ${audio.last}", audio.last <= video.last + AUDIO_FRAME_US)
        val frames = expectedDurationUs / FRAME_US + 1
        assertTrue("frame count ${video.ptsUs.size}, expected about $frames", video.ptsUs.size in frames..(frames + 2))
    }

    @Test
    fun h264ClosedGopCutIsAlignedAtBothEdges() = assertAlignedCut("cut_h264_bframes.mp4", 1_500, 4_500)

    @Test
    fun hevcOpenGopCutDropsLeadingPicturesAndStaysAligned() = assertAlignedCut("cut_hevc_opengop.mp4", 1_500, 4_500)

    @Test
    fun mergedSegmentsAreContiguousOnBothTracks() = runBlocking {
        val name = "cut_h264_bframes.mp4"
        val input = asset(name)
        val (outFile, outUri) = output("merged_$name")
        val result = pipeline.executeMerge(
            MuxingMergeRequest(outUri.toString(), listOf(clip(input, 1_500L to 3_000L), clip(input, 5_500L to 7_000L)))
        )
        assertTrue("merge failed: ${result.exceptionOrNull()}", result.isSuccess)

        val tracks = samplesOf(outFile)
        val video = tracks.getValue("video")
        val audio = tracks.getValue("audio")
        assertEquals(0L, video.first)
        assertTrue("audio must not lead video (${audio.first})", audio.first in 0L..AUDIO_FRAME_US)
        // Each segment: sync frame at 2 s / 6 s up to the end -> 31 frames, plus the reference
        // frame past the end and the B-frame before it.
        assertTrue("frame count ${video.ptsUs.size}", video.ptsUs.size in 62..66)
        assertTrue("video has a hole: ${video.ptsUs}", maxVideoGapIsOneFrame(video.ptsUs, joinAllowance = 2))
        val maxVideoGap = video.ptsUs.zipWithNext { a, b -> b - a }.max()
        val maxAudioGap = audio.ptsUs.zipWithNext { a, b -> b - a }.max()
        assertTrue("video gap $maxVideoGap us at the join", maxVideoGap <= 3 * FRAME_US)
        assertTrue("audio gap $maxAudioGap us at the join", maxAudioGap <= 4 * AUDIO_FRAME_US)
        assertTrue("audio truncated early at ${audio.last}", audio.last >= video.last - 2 * AUDIO_FRAME_US)
    }
}
