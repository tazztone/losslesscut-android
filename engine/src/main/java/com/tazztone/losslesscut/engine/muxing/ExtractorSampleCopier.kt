package com.tazztone.losslesscut.engine.muxing

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer

/**
 * Main copier loop that reads from MediaExtractor and writes to MuxerWriter.
 */
class ExtractorSampleCopier(
    private val extractor: MediaExtractor,
    private val muxerWriter: MuxerWriter,
    private val timeMapper: SampleTimeMapper
) {

    private class SampleParams(
        val plan: SelectedTrackPlan,
        val buffer: ByteBuffer,
        val size: Int,
        val time: Long,
        val globalOffsetUs: Long
    )

    /**
     * Copies samples from [extractor] to [muxerWriter] for the specified time range.
     * @return Track index to last sample time map (relative Us).
     */
    suspend fun copy(
        plan: SelectedTrackPlan,
        startUs: Long,
        endUs: Long,
        buffer: ByteBuffer,
        globalOffsetUs: Long = 0L
    ): Map<Int, Long> {
        val lastSampleTimeByMuxerTrack = mutableMapOf<Int, Long>()
        val trackStartUs = mutableMapOf<Int, Long>()
        var effectiveStartUs = -1L

        for (extractorTrackIdx in plan.trackMap.keys) {
            extractor.selectTrack(extractorTrackIdx)
        }

        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        var hasMore = true
        while (hasMore) {
            currentCoroutineContext().ensureActive()
            val sampleSize = extractor.readSampleData(buffer, 0)
            val sampleTime = extractor.sampleTime
            
            if (sampleSize < 0 || sampleTime > endUs) {
                hasMore = false
            } else {
                val params = SampleParams(
                    plan = plan,
                    buffer = buffer,
                    size = sampleSize,
                    time = sampleTime,
                    globalOffsetUs = globalOffsetUs
                )
                effectiveStartUs = processSample(
                    params = params,
                    currentEffectiveStartUs = effectiveStartUs,
                    lastSampleTimeByMuxerTrack = lastSampleTimeByMuxerTrack,
                    trackStartUs = trackStartUs
                )
                hasMore = extractor.advance()
            }
        }
        return lastSampleTimeByMuxerTrack
    }

    private fun processSample(
        params: SampleParams,
        currentEffectiveStartUs: Long,
        lastSampleTimeByMuxerTrack: MutableMap<Int, Long>,
        trackStartUs: MutableMap<Int, Long>
    ): Long {
        var effectiveStartUs = currentEffectiveStartUs
        val trackIdx = extractor.sampleTrackIndex
        val muxIdx = params.plan.trackMap[trackIdx] ?: return effectiveStartUs

        if (effectiveStartUs == -1L && isPrimaryTrack(trackIdx)) {
            effectiveStartUs = params.time
        }

        if (effectiveStartUs != -1L) {
            val presUs = timeMapper.map(params.time, effectiveStartUs, params.globalOffsetUs)
            val startUs = trackStartUs.getOrPut(muxIdx) { presUs }
            val finalPresUs = maxOf(startUs, presUs)
            
            writeSample(muxIdx, params.buffer, params.size, finalPresUs)

            val relativeTime = finalPresUs - params.globalOffsetUs
            val currentMax = lastSampleTimeByMuxerTrack[muxIdx] ?: 0L
            lastSampleTimeByMuxerTrack[muxIdx] = maxOf(currentMax, relativeTime)
        }
        return effectiveStartUs
    }

    private fun isPrimaryTrack(trackIdx: Int): Boolean {
        val format = extractor.getTrackFormat(trackIdx)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        return mime.startsWith("video/") || mime.startsWith("audio/")
    }

    private fun writeSample(
        muxerTrack: Int,
        buffer: ByteBuffer,
        sampleSize: Int,
        presentationTimeUs: Long
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        bufferInfo.presentationTimeUs = presentationTimeUs
        bufferInfo.offset = 0
        bufferInfo.size = sampleSize
        
        var flags = 0
        if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        bufferInfo.flags = flags

        muxerWriter.writeSampleData(muxerTrack, buffer, bufferInfo)
    }
}
