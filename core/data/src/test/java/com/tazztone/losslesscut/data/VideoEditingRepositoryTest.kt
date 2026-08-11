package com.tazztone.losslesscut.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.domain.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.domain.engine.ILosslessEngine
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.HashUtils
import com.tazztone.losslesscut.domain.model.WaveformResult
import com.tazztone.losslesscut.utils.StorageUtils
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VideoEditingRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val engine = mockk<ILosslessEngine>()
    private val storageUtils = mockk<StorageUtils>()
    private val waveformExtractor = mockk<AudioWaveformExtractor>()
    private val ioDispatcher = Dispatchers.Unconfined

    private lateinit var repository: VideoEditingRepositoryImpl

    @Before
    fun setUp() {
        val analysisCache = AnalysisCacheImpl(context)
        repository = VideoEditingRepositoryImpl(
            context,
            engine,
            storageUtils,
            waveformExtractor,
            analysisCache,
            ioDispatcher
        )
    }

    @Test
    fun saveAndRestoreSession_worksCorrectly() = runTest {
        val clip = MediaClip(
            id = UUID.randomUUID(),
            uri = "content://mock/1.mp4",
            fileName = "1.mp4",
            durationMs = 1000L,
            width = 1920, height = 1080,
            videoMime = "video/mp4", audioMime = "audio/aac",
            sampleRate = 44100, channelCount = 2,
            fps = 30f, rotation = 0, isAudioOnly = false
        )
        val clips = listOf(clip)

        repository.saveSession(clips)
        
        val sessionId = HashUtils.sha256(clip.uri)
        val sessionFile = File(context.cacheDir, "session_$sessionId.json")
        assertTrue("Session file should exist", sessionFile.exists())

        val recentSessions = repository.listSavedSessions()
        assertEquals(clip.uri, recentSessions.first { it.uri == clip.uri }.uri)
        assertEquals(1, recentSessions.first { it.uri == clip.uri }.clipCount)

        repository.deleteSession(clip.uri)
        assertFalse(repository.hasSavedSession(clip.uri))
        assertFalse(repository.listSavedSessions().any { it.uri == clip.uri })
    }

    @Test
    fun sessionIdsDoNotCollideForDifferentUris() = runTest {
        val clip = MediaClip(
            uri = "Aa",
            fileName = "sample.mp4",
            durationMs = 1000L,
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

        repository.saveSession(listOf(clip))
        repository.saveSession(listOf(clip.copy(uri = "BB")))

        assertNotEquals(HashUtils.sha256("Aa"), HashUtils.sha256("BB"))
        assertTrue(repository.hasSavedSession("Aa"))
        assertTrue(repository.hasSavedSession("BB"))
    }

    @Test
    fun getWaveform_cacheHitAndMiss_worksCorrectly() = runTest {
        val clip = MediaClip(
            id = UUID.randomUUID(),
            uri = "content://mock/1.mp4",
            fileName = "1.mp4",
            durationMs = 1000L,
            width = 1920, height = 1080,
            videoMime = "video/mp4", audioMime = "audio/aac",
            sampleRate = 44100, channelCount = 2,
            fps = 30f, rotation = 0, isAudioOnly = false
        )
        val waveform = WaveformResult(
            rawAmplitudes = floatArrayOf(0.1f, 0.2f, 0.3f),
            maxAmplitude = 0.3f,
            durationUs = 1000000L
        )

        // Mock extractor on miss
        coEvery { waveformExtractor.extract(clip.uri, any()) } returns waveform

        // First call - cache miss
        val firstResult = repository.getWaveform(clip)
        assertNotNull(firstResult)
        assertArrayEquals(waveform.rawAmplitudes, firstResult!!.rawAmplitudes, 0.001f)

        // Second call - cache hit (should not call extractor again)
        coEvery { waveformExtractor.extract(clip.uri, any()) } throws IllegalStateException("Should not be called")
        val secondResult = repository.getWaveform(clip)
        assertNotNull(secondResult)
        assertArrayEquals(waveform.rawAmplitudes, secondResult!!.rawAmplitudes, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun createClipFromUri_unsupportedVideo_throwsException() = runTest {
        val uri = "content://mock/unsupported.mp4"
        val meta = com.tazztone.losslesscut.domain.engine.MediaMetadata(
            durationMs = 1000L,
            width = 1920, height = 1080,
            videoMime = "video/x-vnd.on2.vp9",
            audioMime = "audio/aac",
            sampleRate = 44100, channelCount = 2,
            fps = 30f, rotation = 0, tracks = emptyList()
        )
        
        coEvery { engine.getMediaMetadata(uri) } returns Result.success(meta)
        
        repository.createClipFromUri(uri)
    }
}
