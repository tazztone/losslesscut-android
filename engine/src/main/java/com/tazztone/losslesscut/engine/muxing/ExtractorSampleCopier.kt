package com.tazztone.losslesscut.engine.muxing

import android.media.MediaCodec
import android.media.MediaExtractor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer

/**
 * Main copier loop that reads from MediaExtractor and writes to MuxerWriter.
 *
 * Cut semantics: the segment starts at the first video sync frame at or after
 * [copy]'s `startUs` and every selected track is aligned to that frame, so the
 * output's video and audio both begin at presentation time 0 (plus the merge
 * offset). Each track ends independently, so an audio sample past `endUs` never
 * truncates the video track. Video keeps one non-sync frame past `endUs`: in decode
 * order such a frame is the reference for in-range B-frames that follow it, and the
 * B-frames between `endUs` and that frame are kept as well so playback has no hole.
 * Audio samples just past `endUs` are held back and written up to that frame's
 * timestamp, so both tracks end together instead of leaving a gap at merge joins.
 */
class ExtractorSampleCopier(
    private val extractor: MediaExtractor,
    private val muxerWriter: MuxerWriter,
    private val timeMapper: SampleTimeMapper
) {
    private companion object {
        /** Hard cap for sample buffer growth; protects against corrupt sample sizes. */
        const val MAX_BUFFER_BYTES = 256 * 1024 * 1024

        /** How far past endUs non-video samples are held back for a possible video overshoot. */
        const val PENDING_WINDOW_US = 1_000_000L
    }

    private var buffer: ByteBuffer? = null

    /**
     * Copies samples from [extractor] to [muxerWriter] for the specified time range.
     * @param initialBuffer Buffer to read samples into; replaced with a larger one if a sample
     *   does not fit (sources without KEY_MAX_INPUT_SIZE under-report).
     * @return Muxer track index to last written sample time map (relative Us).
     */
    suspend fun copy(
        plan: SelectedTrackPlan,
        startUs: Long,
        endUs: Long,
        initialBuffer: ByteBuffer,
        globalOffsetUs: Long = 0L
    ): Map<Int, Long> {
        val current = buffer
        if (current == null || current.capacity() < initialBuffer.capacity()) buffer = initialBuffer
        val lastSampleTimeByMuxerTrack = mutableMapOf<Int, Long>()
        for (trackIdx in plan.trackMap.keys) extractor.selectTrack(trackIdx)

        val thresholdUs = seekToSegmentStart(plan, startUs) ?: return lastSampleTimeByMuxerTrack
        val state = CopyState(plan, thresholdUs, endUs, globalOffsetUs, lastSampleTimeByMuxerTrack)
        do {
            currentCoroutineContext().ensureActive()
        } while (copyCurrentSample(state) && extractor.advance())
        flushPending(state)
        return lastSampleTimeByMuxerTrack
    }

    private class PendingSample(val data: ByteBuffer, val timeUs: Long, val flags: Int)

    private class CopyState(
        val plan: SelectedTrackPlan,
        val thresholdUs: Long,
        val endUs: Long,
        val globalOffsetUs: Long,
        val lastSampleTimeByMuxerTrack: MutableMap<Int, Long>
    ) {
        val finished: MutableSet<Int> = mutableSetOf()
        val overshot: MutableSet<Int> = mutableSetOf()
        val pending: MutableMap<Int, MutableList<PendingSample>> = mutableMapOf()
        var extendedEndUs: Long = endUs
    }

    /** @return false once the stream has ended or every selected track has passed endUs. */
    private fun copyCurrentSample(state: CopyState): Boolean {
        val trackIdx = extractor.sampleTrackIndex
        val muxIdx = state.plan.trackMap[trackIdx]
        val sampleTime = extractor.sampleTime
        val isVideo = state.plan.isVideoTrackMap[trackIdx] == true
        // Once a reference frame past endUs is kept, video runs up to it; audio is held back instead.
        val limitUs = if (isVideo) state.extendedEndUs else state.endUs
        return when {
            trackIdx < 0 -> false
            muxIdx == null || trackIdx in state.finished -> true
            sampleTime > limitUs -> copyPastEnd(state, trackIdx, muxIdx, sampleTime, isVideo)
            // Before the threshold: provider pre-roll, audio ahead of the first video sync
            // frame, or open-GOP leading pictures that reference the previous GOP.
            sampleTime < state.thresholdUs -> true
            else -> writeCurrentSample(state, muxIdx, sampleTime)
        }
    }

    private fun copyPastEnd(state: CopyState, trackIdx: Int, muxIdx: Int, sampleTime: Long, isVideo: Boolean): Boolean {
        val isSync = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
        val keepAsReference = isVideo && !isSync && trackIdx !in state.overshot
        if (keepAsReference) {
            state.overshot.add(trackIdx)
            state.extendedEndUs = maxOf(state.extendedEndUs, sampleTime)
            return writeCurrentSample(state, muxIdx, sampleTime)
        }
        if (!isVideo && sampleTime <= state.endUs + PENDING_WINDOW_US) {
            holdPending(state, trackIdx, sampleTime)
            return true
        }
        state.finished.add(trackIdx)
        return state.finished.size < state.plan.trackMap.size
    }

    /** Copies the current sample aside; [flushPending] writes it if video ends up reaching it. */
    private fun holdPending(state: CopyState, trackIdx: Int, sampleTime: Long) {
        val sampleSize = readSample()
        if (sampleSize < 0) return
        val source = checkNotNull(buffer)
        source.position(0)
        source.limit(sampleSize)
        val copy = ByteBuffer.allocate(sampleSize).put(source)
        copy.flip()
        state.pending.getOrPut(trackIdx) { mutableListOf() }
            .add(PendingSample(copy, sampleTime, extractor.sampleFlags))
    }

    private fun flushPending(state: CopyState) {
        for ((trackIdx, samples) in state.pending) {
            val muxIdx = state.plan.trackMap[trackIdx] ?: continue
            for (sample in samples) {
                if (sample.timeUs > state.extendedEndUs) break
                val presUs = timeMapper.map(sample.timeUs, state.thresholdUs, state.globalOffsetUs)
                writeSample(muxIdx, sample.data, sample.data.remaining(), presUs, sample.flags)
                recordLastSample(state, muxIdx, presUs)
            }
        }
    }

    private fun writeCurrentSample(state: CopyState, muxIdx: Int, sampleTime: Long): Boolean {
        val sampleSize = readSample()
        if (sampleSize < 0) return false
        val presUs = timeMapper.map(sampleTime, state.thresholdUs, state.globalOffsetUs)
        writeSample(muxIdx, checkNotNull(buffer), sampleSize, presUs, extractor.sampleFlags)
        recordLastSample(state, muxIdx, presUs)
        return true
    }

    private fun recordLastSample(state: CopyState, muxIdx: Int, presUs: Long) {
        val relativeTime = presUs - state.globalOffsetUs
        state.lastSampleTimeByMuxerTrack[muxIdx] =
            maxOf(state.lastSampleTimeByMuxerTrack[muxIdx] ?: 0L, relativeTime)
    }

    /**
     * Positions the extractor at the segment start and returns the timestamp every
     * track is aligned to, or null when the source has nothing at or after [startUs].
     *
     * A previous-sync seek would include the keyframe before an unsnapped cut and
     * silently restore media the timeline discarded, so video starts at the next
     * sync frame. Audio is then re-seeked to that frame instead of to [startUs],
     * otherwise it would begin up to a GOP earlier than the picture.
     */
    private fun seekToSegmentStart(plan: SelectedTrackPlan, startUs: Long): Long? {
        val videoTracks = plan.trackMap.keys.filter { plan.isVideoTrackMap[it] == true }
        if (videoTracks.isEmpty()) {
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
            return startUs
        }
        val otherTracks = plan.trackMap.keys - videoTracks.toSet()
        otherTracks.forEach { extractor.unselectTrack(it) }
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
        val videoStartUs = if (extractor.sampleTrackIndex < 0) -1L else extractor.sampleTime
        otherTracks.forEach { extractor.selectTrack(it) }
        if (videoStartUs < 0) return null
        // Lands video exactly on that sync frame and audio at or just before it.
        extractor.seekTo(videoStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        return videoStartUs
    }

    /** Reads the current sample, growing the buffer when the extractor rejects it as too small. */
    private fun readSample(): Int {
        while (true) {
            val buf = checkNotNull(buffer)
            try {
                return extractor.readSampleData(buf, 0)
            } catch (e: IllegalArgumentException) {
                val grown = buf.capacity().toLong() * 2
                if (grown > MAX_BUFFER_BYTES) throw e
                buffer = ByteBuffer.allocateDirect(grown.toInt())
            }
        }
    }

    private fun writeSample(muxerTrack: Int, data: ByteBuffer, sampleSize: Int, presentationTimeUs: Long, sampleFlags: Int) {
        val bufferInfo = MediaCodec.BufferInfo()
        bufferInfo.presentationTimeUs = presentationTimeUs
        bufferInfo.offset = 0
        bufferInfo.size = sampleSize
        bufferInfo.flags = if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }
        muxerWriter.writeSampleData(muxerTrack, data, bufferInfo)
    }
}
