package com.tazztone.losslesscut.engine

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tazztone.losslesscut.domain.engine.ILosslessEngine
import com.tazztone.losslesscut.domain.engine.MediaMetadata
import com.tazztone.losslesscut.di.TestEngineEntryPoint
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.TrimSegment
import dagger.hilt.EntryPoints
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LosslessEngineRealDeviceTest {

    @get:Rule
    val timeout = Timeout(120, TimeUnit.SECONDS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    
    // Resolve ILosslessEngine from Hilt EntryPoint to respect compile-time boundaries
    private val engine: ILosslessEngine by lazy {
        EntryPoints.get(context, TestEngineEntryPoint::class.java).getLosslessEngine()
    }

    @Before
    fun setUp() {
        val externalDir = context.externalCacheDir
        assertTrue("External cache dir must exist", externalDir != null)
        val testFile = File(externalDir, "IMG_2441.MOV")
        
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        
        // Query the size of the source file in /sdcard/Download
        val sizePfd = uiAutomation.executeShellCommand("stat -c %s /sdcard/Download/IMG_2441.MOV")
        var expectedSize = ParcelFileDescriptor.AutoCloseInputStream(sizePfd).use { inputStream ->
            inputStream.bufferedReader().readText().trim().toLongOrNull()
        }
        
        if (expectedSize == null) {
            // Fallback if stat is not supported or returns unexpected output format
            val lsPfd = uiAutomation.executeShellCommand("ls -l /sdcard/Download/IMG_2441.MOV")
            val lsOutput = ParcelFileDescriptor.AutoCloseInputStream(lsPfd).use { inputStream ->
                inputStream.bufferedReader().readText().trim()
            }
            val tokens = lsOutput.split("\\s+".toRegex())
            expectedSize = tokens.firstOrNull { it.toLongOrNull() != null }?.toLong()
        }
        
        assertTrue("Source file /sdcard/Download/IMG_2441.MOV must exist on the device", expectedSize != null && expectedSize > 0)
        
        if (!testFile.exists() || testFile.length() != expectedSize) {
            println("Copying test file from shared storage to app cache... expected size: $expectedSize")
            val copyPfd = uiAutomation.executeShellCommand("cp /sdcard/Download/IMG_2441.MOV ${testFile.absolutePath}")
            ParcelFileDescriptor.AutoCloseInputStream(copyPfd).use { inputStream ->
                val buffer = ByteArray(8192)
                while (inputStream.read(buffer) != -1) {
                    // Drain the stream
                }
            }
            assertTrue("Copy failed or file size mismatch. Expected: $expectedSize, Got: ${testFile.length()}", testFile.exists() && testFile.length() == expectedSize)
            println("Successfully copied test file to ${testFile.absolutePath}")
        } else {
            println("Test file already exists and size matches: $expectedSize")
        }
    }

    @Test
    fun testDolbyVisionHLGCutOnRealDevice() {
        runBlocking {
            val externalDir = context.externalCacheDir
            assertTrue("External cache dir must exist", externalDir != null)
            
            val testFile = File(externalDir, "IMG_2441.MOV")
            assertTrue("Test video file must exist in external cache: ${testFile.absolutePath}", testFile.exists() && testFile.length() > 0)
            
            // 2. Perform a 3-second cut (0 to 3000ms)
            val outputFile = File(externalDir, "IMG_2441_cut.mp4")
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            // Generate content URIs using target app FileProvider (fully registered in :app manifest)
            val authority = "com.tazztone.losslesscut.provider"
            val inputUri = androidx.core.content.FileProvider.getUriForFile(context, authority, testFile)
            val outputUri = androidx.core.content.FileProvider.getUriForFile(context, authority, outputFile)
            
            val inputUriString = inputUri.toString()
            val outputUriString = outputUri.toString()
            
            println("Starting lossless cut from $inputUriString to $outputUriString")
            val result = engine.executeLosslessCut(
                inputUri = inputUriString,
                outputUri = outputUriString,
                startMs = 0,
                endMs = 3000,
                keepAudio = true,
                keepVideo = true,
                rotationOverride = null,
                selectedTracks = null
            )
            
            assertTrue("Lossless cut failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertTrue("Output file must exist and have content", outputFile.exists() && outputFile.length() > 0)
            
            // 3. Verify color/HDR and track metadata from output file
            val metadataResult = engine.getMediaMetadata(outputUriString)
            assertTrue("Metadata retrieval failed: ${metadataResult.exceptionOrNull()?.message}", metadataResult.isSuccess)
            
            val metadata = metadataResult.getOrThrow()
            println("Output video duration: ${metadata.durationMs}ms")
            println("Output video size: ${metadata.width}x${metadata.height}")
            println("Output video MIME: ${metadata.videoMime}")
            
            // Assertions
            assertTrue("Output file duration should be around 3 seconds", metadata.durationMs in 2800..3200)
            assertEquals(3840, metadata.width)
            assertEquals(2160, metadata.height)
            
            // Check if Dolby Vision or HEVC MIME was retained
            val isMimeValid = metadata.videoMime == "video/dolby-vision" || metadata.videoMime == "video/hevc"
            assertTrue("Video MIME should be either video/dolby-vision or video/hevc, got: ${metadata.videoMime}", isMimeValid)
            
            // Clean up files
            outputFile.delete()
        }
    }

    @Test
    fun testDolbyVisionHLGMergeOnRealDevice() {
        runBlocking {
            val externalDir = context.externalCacheDir
            assertTrue("External cache dir must exist", externalDir != null)
            
            val testFile = File(externalDir, "IMG_2441.MOV")
            assertTrue("Test video file must exist in external cache: ${testFile.absolutePath}", testFile.exists() && testFile.length() > 0)
            
            val outputFile = File(externalDir, "IMG_2441_merge.mp4")
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            val authority = "com.tazztone.losslesscut.provider"
            val inputUri = androidx.core.content.FileProvider.getUriForFile(context, authority, testFile)
            val outputUri = androidx.core.content.FileProvider.getUriForFile(context, authority, outputFile)
            
            val inputUriString = inputUri.toString()
            val outputUriString = outputUri.toString()
            
            // Create segments for merging
            val clips = listOf(
                MediaClip(
                    uri = inputUriString,
                    fileName = "IMG_2441.MOV",
                    durationMs = 10000,
                    width = 3840,
                    height = 2160,
                    videoMime = "video/dolby-vision",
                    audioMime = "audio/mp4a-latm",
                    sampleRate = 48000,
                    channelCount = 2,
                    fps = 30f,
                    rotation = 0,
                    isAudioOnly = false,
                    segments = listOf(TrimSegment(startMs = 0, endMs = 2000))
                ),
                MediaClip(
                    uri = inputUriString,
                    fileName = "IMG_2441.MOV",
                    durationMs = 10000,
                    width = 3840,
                    height = 2160,
                    videoMime = "video/dolby-vision",
                    audioMime = "audio/mp4a-latm",
                    sampleRate = 48000,
                    channelCount = 2,
                    fps = 30f,
                    rotation = 0,
                    isAudioOnly = false,
                    segments = listOf(TrimSegment(startMs = 4000, endMs = 6000))
                )
            )
            
            println("Starting lossless merge from $inputUriString to $outputUriString")
            val result = engine.executeLosslessMerge(
                outputUri = outputUriString,
                clips = clips,
                keepAudio = true,
                keepVideo = true,
                rotationOverride = null,
                selectedTracks = null
            )
            
            assertTrue("Lossless merge failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertTrue("Output file must exist and have content", outputFile.exists() && outputFile.length() > 0)
            outputFile.delete()
        }
    }
}
