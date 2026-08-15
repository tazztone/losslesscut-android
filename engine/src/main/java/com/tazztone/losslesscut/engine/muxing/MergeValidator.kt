package com.tazztone.losslesscut.engine.muxing

import android.media.MediaFormat
import java.io.IOException
import javax.inject.Inject

/**
 * Validates that multiple clips are compatible for lossless merging.
 */
class MergeValidator @Inject constructor() {

    private companion object {
        const val MAX_CSD_INDEX = 10
        const val FORMAT_FLOAT_TOLERANCE = 0.01
    }

    fun validateCodec(clipUri: String, currentMime: String?, expectedMime: String?, trackType: String) {
        if (expectedMime != null && !areMimeTypesCompatible(currentMime, expectedMime)) {
            throw IOException("Codec mismatch for $trackType: expected $expectedMime, got $currentMime in $clipUri")
        }
    }

    fun validateTrack(
        clipUri: String,
        current: MediaFormat?,
        expected: MediaFormat?,
        trackType: String,
        required: Boolean
    ) {
        if (!required) return
        if ((current == null) != (expected == null)) {
            throw IOException("Track presence mismatch for $trackType in $clipUri")
        }
        if (current == null || expected == null) return

        validateCodec(
            clipUri,
            current.getString(MediaFormat.KEY_MIME),
            expected.getString(MediaFormat.KEY_MIME),
            trackType
        )

        val keys = if (trackType == "video") {
            listOf(
                MediaFormat.KEY_WIDTH,
                MediaFormat.KEY_HEIGHT,
                MediaFormat.KEY_FRAME_RATE,
                "profile",
                "level",
                "color-standard",
                "color-transfer",
                "color-range"
            )
        } else {
            listOf(MediaFormat.KEY_SAMPLE_RATE, MediaFormat.KEY_CHANNEL_COUNT, "profile")
        }
        keys.forEach { key -> compareNumericKey(clipUri, current, expected, key, trackType) }

        for (index in 0..MAX_CSD_INDEX) {
            compareCodecSpecificData(clipUri, current, expected, "csd-$index", trackType)
        }
    }

    private fun compareNumericKey(
        clipUri: String,
        current: MediaFormat,
        expected: MediaFormat,
        key: String,
        trackType: String
    ) {
        val currentHasKey = current.containsKey(key)
        val expectedHasKey = expected.containsKey(key)
        if (currentHasKey != expectedHasKey) {
            throw IOException("Format key $key mismatch for $trackType in $clipUri")
        }
        if (!currentHasKey) return

        val currentValue = numericValue(current, key)
        val expectedValue = numericValue(expected, key)
        if (kotlin.math.abs(currentValue - expectedValue) > FORMAT_FLOAT_TOLERANCE) {
            throw IOException("Format key $key mismatch for $trackType in $clipUri")
        }
    }

    private fun numericValue(format: MediaFormat, key: String): Double {
        return try {
            format.getInteger(key).toDouble()
        } catch (_: RuntimeException) {
            format.getFloat(key).toDouble()
        }
    }

    @Suppress("ThrowsCount")
    private fun compareCodecSpecificData(
        clipUri: String,
        current: MediaFormat,
        expected: MediaFormat,
        key: String,
        trackType: String
    ) {
        val currentHasKey = current.containsKey(key)
        val expectedHasKey = expected.containsKey(key)
        if (currentHasKey != expectedHasKey) {
            throw IOException("Codec data $key mismatch for $trackType in $clipUri")
        }
        if (!currentHasKey) return

        val currentBuffer = current.getByteBuffer(key)?.duplicate()
        val expectedBuffer = expected.getByteBuffer(key)?.duplicate()
        if (currentBuffer == null || expectedBuffer == null || currentBuffer.remaining() != expectedBuffer.remaining()) {
            throw IOException("Codec data $key mismatch for $trackType in $clipUri")
        }
        while (currentBuffer.hasRemaining()) {
            if (currentBuffer.get() != expectedBuffer.get()) {
                throw IOException("Codec data $key mismatch for $trackType in $clipUri")
            }
        }
    }

    private fun areMimeTypesCompatible(mime1: String?, mime2: String?): Boolean {
        return mime1 != null && mime1.equals(mime2, ignoreCase = true)
    }
}
