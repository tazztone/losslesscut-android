package com.tazztone.losslesscut.domain.model

public data class WaveformResult(
    val rawAmplitudes: FloatArray,
    val maxAmplitude: Float,
    val durationUs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WaveformResult

        if (!rawAmplitudes.contentEquals(other.rawAmplitudes)) return false
        if (maxAmplitude != other.maxAmplitude) return false
        if (durationUs != other.durationUs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rawAmplitudes.contentHashCode()
        result = 31 * result + maxAmplitude.hashCode()
        result = 31 * result + durationUs.hashCode()
        return result
    }
}
