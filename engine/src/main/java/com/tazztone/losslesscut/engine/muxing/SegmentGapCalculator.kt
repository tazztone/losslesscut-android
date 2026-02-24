package com.tazztone.losslesscut.engine.muxing

/**
 * Calculates gaps between segments to avoid timestamp overlaps in merged files.
 */
object SegmentGapCalculator {
    private const val AUDIO_FRAME_SIZE = 1024.0
    private const val MICROSECONDS_IN_SECOND = 1_000_000.0
    private const val VIDEO_FRAME_DURATION_DEFAULT_US = 33333L

    fun calculateGapUs(audioSampleRate: Int, videoFps: Float): Long {
        val audioFrameDurationUs = (AUDIO_FRAME_SIZE * MICROSECONDS_IN_SECOND / audioSampleRate).toLong()
        val videoFrameDurationUs = if (videoFps > 0) {
            (MICROSECONDS_IN_SECOND / videoFps).toLong()
        } else {
            VIDEO_FRAME_DURATION_DEFAULT_US
        }
        return maxOf(audioFrameDurationUs, videoFrameDurationUs)
    }
}
