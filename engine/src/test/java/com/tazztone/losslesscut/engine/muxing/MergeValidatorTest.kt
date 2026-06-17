package com.tazztone.losslesscut.engine.muxing

import org.junit.Test
import java.io.IOException

class MergeValidatorTest {

    private val validator = MergeValidator()

    @Test
    fun `validateCodec succeeds when mimes match`() {
        validator.validateCodec("uri", "video/avc", "video/avc", "video")
    }

    @Test(expected = IOException::class)
    fun `validateCodec throws when mimes mismatch`() {
        validator.validateCodec("uri", "video/hevc", "video/avc", "video")
    }

    @Test
    fun `validateCodec succeeds when expected is hevc and current is dolby-vision`() {
        validator.validateCodec("uri", "video/dolby-vision", "video/hevc", "video")
    }

    @Test
    fun `validateCodec succeeds when expected is dolby-vision and current is hevc`() {
        validator.validateCodec("uri", "video/hevc", "video/dolby-vision", "video")
    }

    @Test
    fun `validateCodec succeeds when expected is null`() {
        validator.validateCodec("uri", "video/hevc", null, "video")
    }
}
