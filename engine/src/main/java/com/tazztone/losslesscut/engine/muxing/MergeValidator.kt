package com.tazztone.losslesscut.engine.muxing

import java.io.IOException

/**
 * Validates that multiple clips are compatible for lossless merging.
 */
class MergeValidator {

    fun validateCodec(clipUri: String, currentMime: String?, expectedMime: String?, trackType: String) {
        if (expectedMime != null && currentMime != expectedMime) {
            throw IOException("Codec mismatch for $trackType: expected $expectedMime, got $currentMime in $clipUri")
        }
    }
}
