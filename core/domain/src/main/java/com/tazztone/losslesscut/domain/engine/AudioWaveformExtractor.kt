package com.tazztone.losslesscut.domain.engine

import android.content.Context
import android.net.Uri

interface AudioWaveformExtractor {
    suspend fun extract(
        context: Context, 
        uri: Uri, 
        bucketCount: Int = 1000,
        onProgress: ((FloatArray) -> Unit)? = null
    ): FloatArray?
}
