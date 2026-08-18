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
        val sessionId = "session-primary"

        assertTrue(repository.saveSession(sessionId, clips).isSuccess)
        
        val sessionFile = File(context.noBackupFilesDir, "editing_sessions/session_$sessionId.json")
        assertTrue("Session file should exist", sessionFile.exists())
        assertFalse("Session must not be stored in evictable cacheDir", File(context.cacheDir, sessionFile.name).exists())

        val recentSessions = repository.listSavedSessions()
        assertEquals(clip.uri, recentSessions.first { it.uri == clip.uri }.uri)
        assertEquals(1, recentSessions.first { it.uri == clip.uri }.clipCount)
        assertEquals(sessionId, recentSessions.first { it.uri == clip.uri }.sessionId)

        repository.deleteSession(sessionId)
        assertFalse(repository.hasSavedSession(sessionId))
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

        repository.saveSession("session-a", listOf(clip))
        repository.saveSession("session-b", listOf(clip.copy(uri = "BB")))

        assertTrue(repository.hasSavedSession("session-a"))
        assertTrue(repository.hasSavedSession("session-b"))
    }

    @Test
    fun recentSessions_areCappedAtFive() = runTest {
        val clips = (1..6).map { index ->
            MediaClip(
                uri = "content://mock/session-$index.mp4",
                fileName = "session-$index.mp4",
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
        }

        clips.forEachIndexed { index, clip -> repository.saveSession("session-$index", listOf(clip)) }

        val sessions = repository.listSavedSessions()
        assertEquals(5, sessions.size)
        assertFalse(sessions.any { it.uri == clips.first().uri })
        assertTrue(sessions.any { it.uri == clips.last().uri })
    }

    @Test
    fun corruptRecentSessionIndex_isRecoveredOnNextSave() = runTest {
        val sessionsDir = File(context.noBackupFilesDir, "editing_sessions")
        sessionsDir.mkdirs()
        File(sessionsDir, "sessions_index.json").writeText("not valid json")

        val clip = MediaClip(
            uri = "content://mock/recovered.mp4",
            fileName = "recovered.mp4",
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

        repository.saveSession("session-recovered", listOf(clip))

        assertEquals(listOf(clip.uri), repository.listSavedSessions().map { it.uri })
    }

    @Test
    fun reorderAndRemoveFirstClip_keepsStableSessionIdentity() = runTest {
        val clips = listOf(
            MediaClip(uri = "content://mock/first", fileName = "first.mp4", durationMs = 1000L, width = 1920, height = 1080, videoMime = "video/mp4", audioMime = "audio/aac", sampleRate = 44100, channelCount = 2, fps = 30f, rotation = 0, isAudioOnly = false),
            MediaClip(uri = "content://mock/second", fileName = "second.mp4", durationMs = 1000L, width = 1920, height = 1080, videoMime = "video/mp4", audioMime = "audio/aac", sampleRate = 44100, channelCount = 2, fps = 30f, rotation = 0, isAudioOnly = false)
        )

        repository.saveSession("stable-session", clips)
        repository.saveSession("stable-session", listOf(clips[1]))

        val sessions = repository.listSavedSessions()
        assertEquals(1, sessions.size)
        assertEquals("stable-session", sessions.single().sessionId)
        assertEquals("content://mock/second", sessions.single().uri)
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
