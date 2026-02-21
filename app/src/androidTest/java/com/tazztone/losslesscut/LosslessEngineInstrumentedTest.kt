package com.tazztone.losslesscut

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Instrumented test, which will execute on an Android device.
 *
 * This test suite addresses the limitations of Robolectric testing for MediaExtractor
 * and MediaMuxer, which require native underlying framework code to run accurately.
 * 
 * Note: These tests require 'test_video.mp4' to be present in 'app/src/androidTest/assets/'.
 */
@RunWith(AndroidJUnit4::class)
class LosslessEngineInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val engine = LosslessEngineImpl

    @Test
    fun executeLosslessCut_validVideo_succeeds() = runBlocking {
        val testFile = copyAssetToCache("test_video.mp4") ?: return@runBlocking
        val inputUri = Uri.fromFile(testFile)
        
        val outputFileName = "test_output_${System.currentTimeMillis()}.mp4"
        val outputUri = StorageUtils.createVideoOutputUri(context, outputFileName)
        
        assertNotNull("Output URI should not be null", outputUri)
        
        // Cut the first 1 second
        val result = engine.executeLosslessCut(
            context = context,
            videoUri = inputUri,
            outputUri = outputUri!!,
            startMs = 0,
            endMs = 1000,
            keepAudio = true,
            keepVideo = true
        )
        
        assertTrue("Lossless cut should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        
        // Verify output exists and has some data
        val metadata = StorageUtils.getVideoMetadata(context, outputUri)
        assertTrue("Output video should have a duration > 0", metadata.durationMs > 0)
        
        // Clean up
        testFile.delete()
    }

    @Test
    fun probeKeyframes_validVideo_returnsNonEmptyList() = runBlocking {
        val testFile = copyAssetToCache("test_video.mp4") ?: return@runBlocking
        val inputUri = Uri.fromFile(testFile)
        
        val keyframes = engine.probeKeyframes(context, inputUri)
        
        assertNotNull("Keyframes list should not be null", keyframes)
        assertFalse("Keyframes list should not be empty for a valid video", keyframes.isEmpty())
        assertTrue("First keyframe should typically be 0", keyframes.contains(0L))
        
        // Clean up
        testFile.delete()
    }

    private fun copyAssetToCache(assetName: String): File? {
        val assetManager = context.assets
        val cacheFile = File(context.cacheDir, assetName)
        
        return try {
            assetManager.open(assetName).use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            cacheFile
        } catch (e: IOException) {
            android.util.Log.e("LosslessEngineTest", "Failed to copy asset: $assetName", e)
            null
        }
    }
}
