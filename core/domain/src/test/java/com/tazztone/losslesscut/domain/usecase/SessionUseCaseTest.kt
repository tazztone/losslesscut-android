package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.MediaClip
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
        val clips = listOf(mockk<MediaClip>())
        sessionUseCase.saveSession(clips)
        coVerify { repository.saveSession(clips) }
    }

    @Test
    public fun testRestoreSession(): Unit = runBlocking {
        val uri = "file:///test.mp4"
        val clips = listOf(mockk<MediaClip>())
        coEvery { repository.restoreSession(uri) } returns clips
        
        val result = sessionUseCase.restoreSession(uri)
        assertEquals(clips, result)
    }

    @Test
    public fun testHasSavedSession(): Unit = runBlocking {
        val uri = "file:///test.mp4"
        coEvery { repository.hasSavedSession(uri) } returns true
        
        val result = sessionUseCase.hasSavedSession(uri)
        assertTrue(result)
    }
}
