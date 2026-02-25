package com.tazztone.losslesscut.domain.engine

import kotlinx.coroutines.flow.Flow

public interface AudioDecoder {
    public suspend fun decode(uri: String): Flow<PcmData>

    public data class PcmData(
        val buffer: ByteArray,
        val size: Int,
        val timeUs: Long,
        val durationUs: Long,
        val sampleRate: Int,
        val channelCount: Int,
        val isEndOfStream: Boolean = false
    )
}
