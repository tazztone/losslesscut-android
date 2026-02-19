package com.tazztone.losslesscut

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // Specify SDK to avoid warnings and ensure consistent behavior
class RobolectricEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun probeKeyframes_withInvalidUri_returnsEmptyList() = runBlocking {
        val invalidUri = Uri.parse("content://invalid/video.mp4")
        val keyframes = LosslessEngine.probeKeyframes(context, invalidUri)
        assertTrue("Keyframes should be empty for invalid URI", keyframes.isEmpty())
    }

    @Test
    fun executeLosslessCut_withInvalidUri_returnsFalse() = runBlocking {
        val invalidUri = Uri.parse("content://invalid/video.mp4")
        val outputFile = File(context.cacheDir, "output.mp4")
        val success = LosslessEngine.executeLosslessCut(context, invalidUri, outputFile, 0, 1000)
        assertFalse("Lossless cut should fail for invalid URI", success)
    }

    // TODO [Robolectric Limitation/Requirement]:
    // To truly test the extraction and muxing logic, we need to provide a real (or valid mock)
    // multimedia file to the MediaExtractor in the Robolectric environment.
    // This often involves ShadowMediaExtractor or placing a file in src/test/resources.
}
