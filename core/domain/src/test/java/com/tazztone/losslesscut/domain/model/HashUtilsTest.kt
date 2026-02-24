package com.tazztone.losslesscut.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HashUtilsTest {

    @Test
    fun testSha256_consistency() {
        val input = "test_input"
        val hash1 = HashUtils.sha256(input)
        val hash2 = HashUtils.sha256(input)
        assertEquals(hash1, hash2)
    }

    @Test
    fun testSha256_uniqueness() {
        val hash1 = HashUtils.sha256("input1")
        val hash2 = HashUtils.sha256("input2")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun testSha256_length() {
        val hash = HashUtils.sha256("test")
        assertEquals(64, hash.length) // SHA-256 hex string is 64 chars
    }
}
