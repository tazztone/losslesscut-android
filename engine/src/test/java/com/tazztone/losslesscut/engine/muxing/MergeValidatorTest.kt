package com.tazztone.losslesscut.engine.muxing

import android.media.MediaFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

    @Test(expected = IOException::class)
    fun `validateCodec rejects dolby-vision when expected is hevc`() {
        validator.validateCodec("uri", "video/dolby-vision", "video/hevc", "video")
    }

    @Test(expected = IOException::class)
    fun `validateCodec rejects hevc when expected is dolby-vision`() {
        validator.validateCodec("uri", "video/hevc", "video/dolby-vision", "video")
    }

    @Test
    fun `validateCodec succeeds when expected is null`() {
        validator.validateCodec("uri", "video/hevc", null, "video")
    }

    @Test
    fun `validateCodec succeeds when mimes match with different casing`() {
        validator.validateCodec("uri", "VIDEO/AVC", "video/avc", "video")
    }

    @Test(expected = IOException::class)
    fun `validateCodec throws when current is null but expected is not null`() {
        validator.validateCodec("uri", null, "video/hevc", "video")
    }

    @Test(expected = IOException::class)
    fun `validateTrack rejects video dimension mismatch`() {
        val expected = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        val current = MediaFormat.createVideoFormat("video/avc", 1280, 1080)

        validator.validateTrack("uri", current, expected, "video", required = true)
    }

    @Test(expected = IOException::class)
    fun `validateTrack rejects audio sample rate mismatch`() {
        val expected = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2)
        val current = MediaFormat.createAudioFormat("audio/mp4a-latm", 48000, 2)

        validator.validateTrack("uri", current, expected, "audio", required = true)
    }

    @Test
    fun `validateTrack accepts matching video format`() {
        val expected = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        val current = MediaFormat.createVideoFormat("video/avc", 1920, 1080)

        validator.validateTrack("uri", current, expected, "video", required = true)
    }
}
