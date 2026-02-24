package com.tazztone.losslesscut.domain.engine

interface AudioWaveformExtractor {
    suspend fun extract(
        uri: String, 
        bucketCount: Int = 1000,
        onProgress: ((FloatArray) -> Unit)? = null
    ): FloatArray?
}
