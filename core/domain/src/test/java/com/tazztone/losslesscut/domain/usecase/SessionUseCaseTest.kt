package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SessionSummary
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

public class SessionUseCaseTest {

    private val repository: IVideoEditingRepository = mockk<IVideoEditingRepository>(relaxed = true)
    private val sessionUseCase: SessionUseCase = SessionUseCase(repository, Dispatchers.Unconfined)

    @Test
    public fun testSaveSession(): Unit = runBlocking {
        val sessionId = "session-1"
        val clips = listOf(mockk<MediaClip>())
        sessionUseCase.saveSession(sessionId, clips)
        coVerify { repository.saveSession(sessionId, clips) }
    }

    @Test
    public fun testRestoreSession(): Unit = runBlocking {
        val sessionId = "session-1"
        val clips = listOf(mockk<MediaClip>())
        val restoreResult = com.tazztone.losslesscut.domain.model.SessionRestoreResult(clips)
        coEvery { repository.restoreSession(sessionId) } returns restoreResult
        
        val result = sessionUseCase.restoreSession(sessionId)
        assertEquals(restoreResult, result)
    }

    @Test
    public fun testHasSavedSession(): Unit = runBlocking {
        val sessionId = "session-1"
        coEvery { repository.hasSavedSession(sessionId) } returns true
        
        val result = sessionUseCase.hasSavedSession(sessionId)
        assertTrue(result)
    }

    @Test
    public fun testListAndDeleteSessions(): Unit = runBlocking {
        val sessions = listOf(SessionSummary("file:///test.mp4", "test.mp4", 1, 1L))
        coEvery { repository.listSavedSessions() } returns sessions

        assertEquals(sessions, sessionUseCase.listSavedSessions())
        sessionUseCase.deleteSession(sessions.single().sessionId)
        coVerify { repository.deleteSession(sessions.single().sessionId) }
    }
}
