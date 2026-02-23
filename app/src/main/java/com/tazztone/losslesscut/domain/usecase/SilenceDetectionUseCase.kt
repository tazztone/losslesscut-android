package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.data.VideoEditingRepository
import com.tazztone.losslesscut.di.IoDispatcher
import com.tazztone.losslesscut.utils.DetectionUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SilenceDetectionUseCase @Inject constructor(
    private val repository: VideoEditingRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun findSilence(
        waveform: FloatArray,
        threshold: Float,
        minSilenceMs: Long,
        totalDurationMs: Long,
        paddingMs: Long,
        minSegmentMs: Long
    ): List<LongRange> = withContext(ioDispatcher) {
        DetectionUtils.findSilence(
            waveform = waveform,
            threshold = threshold,
            minSilenceMs = minSilenceMs,
            totalDurationMs = totalDurationMs,
            paddingMs = paddingMs,
            minSegmentMs = minSegmentMs
        )
    }
}
