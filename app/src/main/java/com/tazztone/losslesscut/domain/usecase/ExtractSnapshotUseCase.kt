package com.tazztone.losslesscut.domain.usecase

import android.graphics.Bitmap
import android.util.Log
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.data.VideoEditingRepository
import com.tazztone.losslesscut.di.IoDispatcher
import com.tazztone.losslesscut.utils.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExtractSnapshotUseCase @Inject constructor(
    private val repository: VideoEditingRepository,
    private val preferences: AppPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    sealed class Result {
        data class Success(val fileName: String) : Result()
        data class Failure(val error: String) : Result()
    }

    suspend fun execute(uri: android.net.Uri, positionMs: Long): Result = withContext(ioDispatcher) {
        try {
            val bitmap = repository.getFrameAt(uri, positionMs)
            if (bitmap != null) {
                val format = preferences.snapshotFormatFlow.first()
                val quality = preferences.jpgQualityFlow.first()
                val ext = if (format == "PNG") "png" else "jpg"
                val fileName = "snapshot_${System.currentTimeMillis()}.$ext"
                val outputUri = repository.createImageOutputUri(fileName)

                if (outputUri != null) {
                    val success = repository.writeSnapshot(bitmap, outputUri, format, quality)
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
