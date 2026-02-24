package com.tazztone.losslesscut.engine.muxing

import java.io.IOException
import javax.inject.Inject

/**
 * Validates that multiple clips are compatible for lossless merging.
 */
class MergeValidator @Inject constructor() {

    fun validateCodec(clipUri: String, currentMime: String?, expectedMime: String?, trackType: String) {
        if (expectedMime != null && currentMime != expectedMime) {
            throw IOException("Codec mismatch for $trackType: expected $expectedMime, got $currentMime in $clipUri")
        }
    }
}
