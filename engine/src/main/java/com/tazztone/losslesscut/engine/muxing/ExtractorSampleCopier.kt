package com.tazztone.losslesscut.engine.muxing

import android.media.MediaCodec
import android.media.MediaExtractor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer

/**
 * Main copier loop that reads from MediaExtractor and writes to MuxerWriter.
 */
class ExtractorSampleCopier(
    private val extractor: MediaExtractor,
    private val muxerWriter: MuxerWriter,
    private val timeMapper: SampleTimeMapper
) {

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
        var effectiveStartUs = -1L

        for (extractorTrackIdx in plan.trackMap.keys) {
            extractor.selectTrack(extractorTrackIdx)
        }

        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        var hasMore = true
        while (currentCoroutineContext().isActive && hasMore) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            val sampleTime = extractor.sampleTime
            
            if (sampleSize < 0 || sampleTime > endUs) {
                hasMore = false
            } else {
                val muxIdx = plan.trackMap[extractor.sampleTrackIndex]
                if (muxIdx != null) {
                    if (effectiveStartUs == -1L) effectiveStartUs = sampleTime
                    val presUs = timeMapper.map(sampleTime, effectiveStartUs, globalOffsetUs)
                    writeSample(muxIdx, buffer, sampleSize, presUs)
                    
                    val relativeTime = presUs - globalOffsetUs
                    val currentMax = lastSampleTimeByMuxerTrack[muxIdx] ?: 0L
                    lastSampleTimeByMuxerTrack[muxIdx] = maxOf(currentMax, relativeTime)
                }
                hasMore = extractor.advance()
            }
        }
        return lastSampleTimeByMuxerTrack
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
