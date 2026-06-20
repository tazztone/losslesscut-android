package com.tazztone.losslesscut.engine.muxing

import java.io.IOException
import javax.inject.Inject

/**
 * Validates that multiple clips are compatible for lossless merging.
 */
class MergeValidator @Inject constructor() {

    fun validateCodec(clipUri: String, currentMime: String?, expectedMime: String?, trackType: String) {
        if (expectedMime != null && !areMimeTypesCompatible(currentMime, expectedMime)) {
            throw IOException("Codec mismatch for $trackType: expected $expectedMime, got $currentMime in $clipUri")
        }
    }

    private fun areMimeTypesCompatible(mime1: String?, mime2: String?): Boolean {
        if (mime1.equals(mime2, ignoreCase = true)) return true
        if (mime1 == null || mime2 == null) return false
        val m1 = mime1.lowercase()
        val m2 = mime2.lowercase()
        return (m1 == "video/hevc" && m2 == "video/dolby-vision") ||
               (m1 == "video/dolby-vision" && m2 == "video/hevc")
    }
}
