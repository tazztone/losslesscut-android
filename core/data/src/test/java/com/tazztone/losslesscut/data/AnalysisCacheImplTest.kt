package com.tazztone.losslesscut.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.VisualStrategy
import com.tazztone.losslesscut.domain.model.WaveformResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnalysisCacheImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var cache: AnalysisCacheImpl

    private val sampleClip = MediaClip(
        id = UUID.randomUUID(),
        uri = "content://media/external/video/media/100",
        fileName = "test.mp4",
        durationMs = 10000L,
        width = 1920,
        height = 1080,
        videoMime = "video/mp4",
        audioMime = "audio/aac",
        sampleRate = 44100,
        channelCount = 2,
        fps = 30f,
        rotation = 0,
        isAudioOnly = false
    )

    @Before
    fun setUp() {
        cache = AnalysisCacheImpl(context)
        cache.clearCache()
    }

    @Test
    fun waveform_saveAndGet_worksCorrectly() {
        val waveform = WaveformResult(
            rawAmplitudes = floatArrayOf(0.1f, 0.5f, 0.9f),
            maxAmplitude = 0.9f,
            durationUs = 10_000_000L
        )

        cache.saveWaveform(sampleClip, waveform)
        val loaded = cache.getWaveform(sampleClip)

        assertNotNull(loaded)
        assertEquals(waveform.maxAmplitude, loaded!!.maxAmplitude, 0.001f)
        assertEquals(waveform.durationUs, loaded.durationUs)
        assertArrayEquals(waveform.rawAmplitudes, loaded.rawAmplitudes, 0.001f)
    }

    @Test
    fun waveform_persistsAcrossCacheInstances() {
        val waveform = WaveformResult(
            rawAmplitudes = floatArrayOf(0.1f, 0.5f, 0.9f),
            maxAmplitude = 0.9f,
            durationUs = 10_000_000L
        )

        cache.saveWaveform(sampleClip, waveform)

        val reloadedCache = AnalysisCacheImpl(context)
        val loaded = reloadedCache.getWaveform(sampleClip)

        assertNotNull(loaded)
        assertArrayEquals(waveform.rawAmplitudes, loaded!!.rawAmplitudes, 0.001f)
    }

    @Test
    fun frameAnalysis_saveAndGet_worksCorrectly() {
        val analysis = listOf(
            FrameAnalysis(timeMs = 0L, meanLuma = 10.0, blurVariance = 100.0, sceneDistance = 5, freezeDiff = 0.1),
            FrameAnalysis(timeMs = 1000L, meanLuma = 250.0, blurVariance = 50.0, sceneDistance = null, freezeDiff = null)
        )

        cache.saveFrameAnalysis(sampleClip, 5, analysis)
        val loaded = cache.getFrameAnalysis(sampleClip, 5)

        assertNotNull(loaded)
        assertEquals(2, loaded!!.size)
        assertEquals(10.0, loaded[0].meanLuma, 0.001)
        assertEquals(5, loaded[0].sceneDistance)
        assertNull(loaded[1].sceneDistance)
    }

    @Test
    fun corruptFile_deletesFileAndReturnsNull() {
        cache.saveWaveform(
            sampleClip,
            WaveformResult(floatArrayOf(0.1f), 0.1f, 1000L)
        )
        val corruptFile = File(context.noBackupFilesDir, "analysis_cache")
            .listFiles()
            ?.single { it.name.startsWith("waveform_") }
            ?: error("Expected waveform cache entry")
        FileOutputStream(corruptFile).use { out ->
            out.write(byteArrayOf(0, 0, 0, 99, 1, 2, 3)) // invalid version and truncated content
        }

        val loaded = cache.getWaveform(sampleClip)
        assertNull(loaded)
        assertTrue("Corrupt cache entry should be removed", !corruptFile.exists())
    }

    @Test
    fun lruAndExpiryEviction_prunesCorrectly() {
        val waveform = WaveformResult(
            rawAmplitudes = FloatArray(10_000) { 0.5f },
            maxAmplitude = 0.5f,
            durationUs = 10_000_000L
        )

        // Set small capacity (10 KB = ~10_000 bytes)
        cache.updateCachePolicy(maxSizeBytes = 10_000L, maxAgeDays = 30)
        cache.saveWaveform(sampleClip, waveform)

        // The entry itself is larger than the cap, so eviction must remove it.
        val usage = cache.getCacheUsageBytes()
        assertTrue("Cache usage $usage should be under capacity", usage <= 10_000L)
    }

    @Test
    fun expiry_prunesOldEntries() {
        cache.saveWaveform(
            sampleClip,
            WaveformResult(floatArrayOf(0.1f), 0.1f, 1000L)
        )
        val entry = File(context.noBackupFilesDir, "analysis_cache")
            .listFiles()
            ?.single { it.name.startsWith("waveform_") }
            ?: error("Expected waveform cache entry")
        entry.setLastModified(System.currentTimeMillis() - 2 * 86_400_000L)

        cache.updateCachePolicy(maxSizeBytes = 250L * 1024L * 1024L, maxAgeDays = 1)

        assertTrue("Expired cache entry should be removed", !entry.exists())
    }

    @Test
    fun clearCache_doesNotTouchSessionFiles() {
        val sessionFile = File(context.noBackupFilesDir, "editing_sessions/session_test.json")
        sessionFile.parentFile?.mkdirs()
        sessionFile.writeText("{}")

        cache.saveWaveform(
            sampleClip,
            WaveformResult(floatArrayOf(0.1f), 0.1f, 1000L)
        )
        cache.clearCache()

        assertTrue("Session file must not be deleted by cache clear", sessionFile.exists())
        assertEquals(0L, cache.getCacheUsageBytes())

        sessionFile.delete()
    }
}
