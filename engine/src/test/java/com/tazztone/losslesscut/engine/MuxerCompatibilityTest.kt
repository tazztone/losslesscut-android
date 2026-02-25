package com.tazztone.losslesscut.engine

import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
internal class MuxerCompatibilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var outputFile: File

    @Before
    fun setup() {
        outputFile = tempFolder.newFile("output.mp4")
    }

    @Test
    fun `adding audio track with video keys might fail in some environments`() {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        
        // Simulate a MediaFormat returned by Extractor that might have "stray" video keys
        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
        
        // Stray video keys that shouldn't be here
        audioFormat.setInteger(MediaFormat.KEY_WIDTH, 1920)
        audioFormat.setInteger(MediaFormat.KEY_HEIGHT, 1080)
        
        try {
            val trackIndex = muxer.addTrack(audioFormat)
            assertNotNull(trackIndex)
        } catch (e: Exception) {
            // If this fails, we've reproduced the issue where extra keys cause addTrack to throw
            println("Caught expected exception: ${e.message}")
            throw e
        } finally {
            muxer.release()
        }
    }

    @Test
    fun `adding audio track to a WEBM muxer - just to compare`() {
        // Some formats like Opus prefer WEBM or OGG depending on Android version
        val webmFile = tempFolder.newFile("output.webm")
        val muxer = MediaMuxer(webmFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
        
        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, 2)
        // MediaMuxer WEBM output format often doesn't support Opus on older Android, but SDK 33 should.
        
        try {
            val trackIndex = muxer.addTrack(audioFormat)
            assertNotNull(trackIndex)
        } catch (e: Exception) {
            println("WEBM Opus failed: ${e.message}")
        } finally {
            muxer.release()
        }
    }
}
