package com.tazztone.losslesscut.domain.usecase

import android.util.Log
import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
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
        try {
            val bitmapBytes = repository.getFrameAt(uri, positionMs)
            if (bitmapBytes != null) {
                val ext = if (format == "PNG") "png" else "jpg"
                val fileName = "snapshot_${System.currentTimeMillis()}.$ext"
                val outputUri = repository.createImageOutputUri(fileName)

                if (outputUri != null) {
                    val success = repository.writeSnapshot(bitmapBytes, outputUri, format, quality)
                    if (success) {
                        repository.finalizeImage(outputUri)
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
        } catch (e: Exception) {
            Log.e("ExtractSnapshotUseCase", "Snapshot failed", e)
            Result.Failure(e.message ?: "Unknown snapshot error")
        }
    }
}
