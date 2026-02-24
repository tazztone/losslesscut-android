package com.tazztone.losslesscut.domain.engine

public interface AudioWaveformExtractor {
    public suspend fun extract(
        uri: String, 
        bucketCount: Int = 1000,
        onProgress: ((FloatArray) -> Unit)? = null
    ): FloatArray?
}
