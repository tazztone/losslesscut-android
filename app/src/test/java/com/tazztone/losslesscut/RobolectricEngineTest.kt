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
    private val engine = LosslessEngineImpl

    @Test
    fun probeKeyframes_withInvalidUri_returnsEmptyList() = runBlocking {
        val invalidUri = Uri.parse("content://invalid/video.mp4")
        val keyframes = engine.probeKeyframes(context, invalidUri)
        assertTrue("Keyframes should be empty for invalid URI", keyframes.isEmpty())
    }

    @Test
    fun executeLosslessCut_withInvalidUri_returnsFailure() = runBlocking {
        val invalidUri = Uri.parse("content://invalid/video.mp4")
        val outputUri = Uri.parse("content://invalid/output.mp4")
        val result = engine.executeLosslessCut(context, invalidUri, outputUri, 0, 1000)
        assertTrue("Lossless cut should fail for invalid URI", result.isFailure)
    }

    // TODO [Robolectric Limitation/Requirement]:
    // To truly test the extraction and muxing logic, we need to provide a real (or valid mock)
    // multimedia file to the MediaExtractor in the Robolectric environment.
    // This often involves ShadowMediaExtractor or placing a file in src/test/resources.
}
