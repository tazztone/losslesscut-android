package com.tazztone.losslesscut.domain.model

import java.security.MessageDigest

public object HashUtils {

    private val HEX_ARRAY: CharArray = "0123456789abcdef".toCharArray()

    /**
     * Generates a SHA-256 hash string for the given input.
     */
    public fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = HEX_ARRAY[v ushr 4]
            hexChars[i * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }
}
