package com.tazztone.losslesscut.domain.usecase

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

public class ExtractSnapshotUseCaseTest {

    private val repository: IVideoEditingRepository = mockk<IVideoEditingRepository>(relaxed = true)
    private val extractSnapshotUseCase: ExtractSnapshotUseCase = ExtractSnapshotUseCase(repository, Dispatchers.Unconfined)

    @Test
    public fun executeSuccessShouldReturnSuccessResult(): Unit = runBlocking {
        val uri = "file:///test.mp4"
        val bytes = ByteArray(10)
        coEvery { repository.getFrameAt(uri, 1000L) } returns bytes
        coEvery { repository.createImageOutputUri(any()) } returns "file:///out.jpg"
        coEvery { repository.writeSnapshot(any(), any(), any(), any()) } returns true

        val result = extractSnapshotUseCase.execute(uri, 1000L, "JPEG", 90)
        
        assertTrue(result is ExtractSnapshotUseCase.Result.Success)
        coVerify { repository.finalizeImage("file:///out.jpg") }
    }

    @Test
    public fun executeFailureFrameExtractionShouldReturnFailure(): Unit = runBlocking {
        val uri = "file:///test.mp4"
        coEvery { repository.getFrameAt(uri, 1000L) } returns null

        val result = extractSnapshotUseCase.execute(uri, 1000L, "JPEG", 90)
        
        assertTrue(result is ExtractSnapshotUseCase.Result.Failure)
        assertEquals("Failed to extract frame", (result as ExtractSnapshotUseCase.Result.Failure).error)
    }

    @Test
    public fun executeFailureOutputCreationShouldReturnFailure(): Unit = runBlocking {
        val uri = "file:///test.mp4"
        coEvery { repository.getFrameAt(uri, 1000L) } returns ByteArray(10)
        coEvery { repository.createImageOutputUri(any()) } returns null

        val result = extractSnapshotUseCase.execute(uri, 1000L, "JPEG", 90)
        
        assertTrue(result is ExtractSnapshotUseCase.Result.Failure)
        assertEquals("Failed to create snapshot output file", (result as ExtractSnapshotUseCase.Result.Failure).error)
    }

    @Test
    public fun executeFailureWriteShouldReturnFailure(): Unit = runBlocking {
        val uri = "file:///test.mp4"
        coEvery { repository.getFrameAt(uri, 1000L) } returns ByteArray(10)
        coEvery { repository.createImageOutputUri(any()) } returns "file:///out.jpg"
        coEvery { repository.writeSnapshot(any(), any(), any(), any()) } returns false

        val result = extractSnapshotUseCase.execute(uri, 1000L, "JPEG", 90)
        
        assertTrue(result is ExtractSnapshotUseCase.Result.Failure)
        assertEquals("Failed to write snapshot", (result as ExtractSnapshotUseCase.Result.Failure).error)
    }
}
