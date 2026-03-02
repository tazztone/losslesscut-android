package com.tazztone.losslesscut.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.domain.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.domain.engine.ILosslessEngine
import com.tazztone.losslesscut.domain.model.MediaClip
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
        repository = VideoEditingRepositoryImpl(
            context,
            engine,
            storageUtils,
            waveformExtractor,
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
        
        val sessionId = clip.uri.hashCode().toString()
        val sessionFile = File(context.cacheDir, "session_$sessionId.json")
        assertTrue("Session file should exist", sessionFile.exists())
    }

    @Test
    fun saveAndLoadWaveformCache_v2_worksCorrectly() = runTest {
        val cacheKey = "waveform_test"
        val waveform = WaveformResult(
            rawAmplitudes = floatArrayOf(0.1f, 0.2f, 0.3f),
            maxAmplitude = 0.3f,
            durationUs = 1000000L
        )

        repository.saveWaveformToCache(cacheKey, waveform)
        
        val loaded = repository.loadWaveformFromCache(cacheKey)
        
        assertNotNull(loaded)
        assertEquals(waveform.durationUs, loaded!!.durationUs)
        assertEquals(waveform.maxAmplitude, loaded!!.maxAmplitude, 0.001f)
        assertArrayEquals(waveform.rawAmplitudes, loaded!!.rawAmplitudes, 0.001f)
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
