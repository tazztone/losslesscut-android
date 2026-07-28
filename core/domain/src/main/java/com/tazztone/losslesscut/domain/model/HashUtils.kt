package com.tazztone.losslesscut.domain.model

import java.security.MessageDigest

public object HashUtils {

    private val HEX_ARRAY: CharArray = "0123456789abcdef".toCharArray()
    private const val HEX_SHIFT = 4
    private const val HEX_MASK = 0x0F
    private const val BYTE_MASK = 0xFF

    /**
     * Generates a SHA-256 hash string for the given input.
     */
    public fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and BYTE_MASK
            hexChars[i * 2] = HEX_ARRAY[v ushr HEX_SHIFT]
            hexChars[i * 2 + 1] = HEX_ARRAY[v and HEX_MASK]
        }
        return String(hexChars)
    }
}
