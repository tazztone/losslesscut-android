package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {

    private val sessionUseCase = mockk<SessionUseCase>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var sessionController: SessionController

    @Before
    fun setUp() {
        sessionController = SessionController(sessionUseCase, testDispatcher)
    }

    @Test
    fun `saveSession calls sessionUseCase saveSession`() = runTest(testDispatcher) {
        val clip = MediaClip(
            id = UUID.randomUUID(),
            uri = "content://mock/video1.mp4",
            fileName = "test.mp4",
            durationMs = 10000L,
            width = 1920,
            height = 1080,
            videoMime = "video/mp4",
            audioMime = "audio/mp4",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = false
        )
        val clips = listOf(clip)

        sessionController.saveSession(clips)

        coVerify { sessionUseCase.saveSession(clips) }
    }

    @Test
    fun `restoreSession calls sessionUseCase restoreSession and returns result`() = runTest(testDispatcher) {
        val uri = "content://mock/uri"
        val clip = MediaClip(
            id = UUID.randomUUID(),
            uri = uri,
            fileName = "test.mp4",
            durationMs = 10000L,
            width = 1920,
            height = 1080,
            videoMime = "video/mp4",
            audioMime = "audio/mp4",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = false
        )
        val expectedClips = listOf(clip)

        coEvery { sessionUseCase.restoreSession(uri) } returns expectedClips

        val result = sessionController.restoreSession(uri)

        coVerify { sessionUseCase.restoreSession(uri) }
        assertEquals(expectedClips, result)
    }

    @Test
    fun `checkSessionExists calls sessionUseCase hasSavedSession and returns true`() = runTest(testDispatcher) {
        val uri = "content://mock/uri"

        coEvery { sessionUseCase.hasSavedSession(uri) } returns true

        val result = sessionController.checkSessionExists(uri)

        coVerify { sessionUseCase.hasSavedSession(uri) }
        assertTrue(result)
    }

    @Test
    fun `checkSessionExists calls sessionUseCase hasSavedSession and returns false`() = runTest(testDispatcher) {
        val uri = "content://mock/uri"

        coEvery { sessionUseCase.hasSavedSession(uri) } returns false

        val result = sessionController.checkSessionExists(uri)

        coVerify { sessionUseCase.hasSavedSession(uri) }
        assertFalse(result)
    }
}
