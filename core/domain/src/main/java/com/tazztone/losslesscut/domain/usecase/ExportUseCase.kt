package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.*
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExportUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    sealed class Result {
        data class Success(val count: Int) : Result()
        data class Failure(val error: String) : Result()
        data class Progress(val percentage: Int, val message: String) : Result()
    }

    suspend fun execute(
        clips: List<MediaClip>,
        selectedClipIndex: Int,
        isLossless: Boolean,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        mergeSegments: Boolean,
        selectedTracks: List<Int>? = null,
        onStatus: suspend (Result) -> Unit
    ) = withContext(ioDispatcher) {
        try {
            if (mergeSegments) {
                val segments = clips.flatMap { clip ->
                    clip.segments.filter { it.action == SegmentAction.KEEP }
                        .map { seg -> clip.copy(segments = listOf(seg)) }
                }

                if (segments.isEmpty()) {
                    onStatus(Result.Failure("No tracks found to merge"))
                    return@withContext
                }

                onStatus(Result.Progress(0, "Merging segments..."))
                ensureActive()

                val firstClip = clips[selectedClipIndex]
                val extension = if (!keepVideo) "m4a" else "mp4"
                val baseNameOnly = firstClip.fileName.substringBeforeLast(".")
                val outputUri = repository.createMediaOutputUri("${baseNameOnly}_merged.$extension", !keepVideo)

                if (outputUri == null) {
                    onStatus(Result.Failure("Failed to create output file"))
                    return@withContext
                }

                val result = repository.executeLosslessMerge(
                    outputUri, segments, keepAudio, keepVideo, rotationOverride, selectedTracks
                )

                result.fold(
                    onSuccess = { onStatus(Result.Success(1)) },
                    onFailure = { onStatus(Result.Failure(it.message ?: "Unknown merge error")) }
                )
            } else {
                val selectedClip = clips[selectedClipIndex]
                val segments = selectedClip.segments.filter { it.action == SegmentAction.KEEP }

                if (segments.isEmpty()) {
                    onStatus(Result.Failure("No tracks found to cut"))
                    return@withContext
                }

                var successCount = 0
                val errors = mutableListOf<String>()

                for ((index, segment) in segments.withIndex()) {
                    ensureActive()
                    val progress = ((index.toFloat() / segments.size) * 100).toInt()
                    onStatus(Result.Progress(
                        progress,
                        "Saving segment ${index + 1} of ${segments.size}"
                    ))

                    val extension = if (!keepVideo) "m4a" else "mp4"
                    val timeSuffix = "_${TimeUtils.formatFilenameDuration(segment.startMs)}-${TimeUtils.formatFilenameDuration(segment.endMs)}"
                    val baseNameOnly = selectedClip.fileName.substringBeforeLast(".")
                    val outputUri = repository.createMediaOutputUri("$baseNameOnly$timeSuffix.$extension", !keepVideo)

                    if (outputUri == null) {
                        errors.add("Failed to create output file for segment ${index + 1}")
                        continue
                    }

                    val result = repository.executeLosslessCut(
                        selectedClip.uri.toString(), outputUri, segment.startMs, segment.endMs,
                        keepAudio, keepVideo, rotationOverride ?: selectedClip.rotation, selectedTracks
                    )
                    result.fold(
                        onSuccess = { successCount++ },
                        onFailure = { errors.add("Segment ${index + 1} failed: ${it.message}") }
                    )
                }

                if (errors.isEmpty() && successCount > 0) {
                    onStatus(Result.Success(successCount))
                } else if (errors.isNotEmpty()) {
                    onStatus(Result.Failure(errors.joinToString("\n")))
                }
            }
        } catch (e: Exception) {
            onStatus(Result.Failure(e.message ?: "Unknown export error"))
        }
    }
}
