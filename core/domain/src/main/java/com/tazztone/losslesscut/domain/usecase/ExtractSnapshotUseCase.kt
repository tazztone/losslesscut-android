package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

public class ExtractSnapshotUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    public sealed class Result {
        public data class Success(val fileName: String) : Result()
        public data class Failure(val error: String) : Result()
    }

    public suspend fun execute(
        uri: String, 
        positionMs: Long, 
        format: String, 
        quality: Int
    ): Result = withContext(ioDispatcher) {
        var outputUri: String? = null
        var committed = false
        try {
            val bitmapBytes = repository.getFrameAt(uri, positionMs, format, quality)
            if (bitmapBytes != null) {
                val ext = if (format.equals("PNG", ignoreCase = true)) "png" else "jpg"
                val fileName = "snapshot_${System.currentTimeMillis()}.$ext"
                val createdOutputUri = repository.createImageOutputUri(fileName)
                outputUri = createdOutputUri

                if (createdOutputUri != null) {
                    val success = repository.writeSnapshot(bitmapBytes, createdOutputUri)
                    if (success) {
                        repository.finalizeImage(createdOutputUri)
                        committed = true
                        Result.Success(fileName)
                    } else {
                        Result.Failure("Failed to write snapshot")
                    }
                } else {
                    Result.Failure("Failed to create snapshot output file")
                }
            } else {
                Result.Failure("Failed to extract frame")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Unknown snapshot error")
        } finally {
            val failedOutputUri = outputUri
            if (failedOutputUri != null && !committed) {
                withContext(NonCancellable) {
                    try {
                        repository.deleteOutput(failedOutputUri)
                    } catch (_: Exception) {
                        // Preserve the original extraction/write result if cleanup fails.
                    }
                }
            }
        }
    }
}
