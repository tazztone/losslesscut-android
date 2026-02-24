package com.tazztone.losslesscut.domain.model

import java.security.MessageDigest

public object HashUtils {
    /**
     * Generates a SHA-256 hash string for the given input.
     */
    public fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
