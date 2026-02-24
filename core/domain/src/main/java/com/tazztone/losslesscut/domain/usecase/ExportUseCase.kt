package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.*
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    data class Params(
        val clips: List<MediaClip>,
        val selectedClipIndex: Int,
        val isLossless: Boolean,
        val keepAudio: Boolean,
        val keepVideo: Boolean,
        val rotationOverride: Int?,
        val mergeSegments: Boolean,
        val selectedTracks: List<Int>? = null
    )

    fun execute(params: Params): Flow<Result> = flow {
        try {
            if (params.mergeSegments) {
                mergeSegments(params).collect { emit(it) }
            } else {
                cutSegments(params).collect { emit(it) }
            }
        } catch (e: Exception) {
            emit(Result.Failure(e.message ?: "Unknown export error"))
        }
    }.flowOn(ioDispatcher)

    private fun mergeSegments(params: Params): Flow<Result> = flow {
        val segments = params.clips.flatMap { clip ->
            clip.segments.filter { it.action == SegmentAction.KEEP }
                .map { seg -> clip.copy(segments = listOf(seg)) }
        }

        if (segments.isEmpty()) {
            emit(Result.Failure("No tracks found to merge"))
            return@flow
        }

        emit(Result.Progress(0, "Merging segments..."))
        
        val firstClip = params.clips[params.selectedClipIndex]
        val extension = if (!params.keepVideo) "m4a" else "mp4"
        val baseName = firstClip.fileName.substringBeforeLast(".")
        val outputUri = repository.createMediaOutputUri("${baseName}_merged.$extension", !params.keepVideo)

        if (outputUri == null) {
            emit(Result.Failure("Failed to create output file"))
            return@flow
        }

        val result = repository.executeLosslessMerge(
            outputUri, segments, params.keepAudio, params.keepVideo, 
            params.rotationOverride, params.selectedTracks
        )

        result.fold(
            onSuccess = { emit(Result.Success(1)) },
            onFailure = { emit(Result.Failure(it.message ?: "Unknown merge error")) }
        )
    }

    private fun cutSegments(params: Params): Flow<Result> = flow {
        val selectedClip = params.clips[params.selectedClipIndex]
        val segments = selectedClip.segments.filter { it.action == SegmentAction.KEEP }

        if (segments.isEmpty()) {
            emit(Result.Failure("No tracks found to cut"))
            return@flow
        }

        var successCount = 0
        val errors = mutableListOf<String>()

        for ((index, segment) in segments.withIndex()) {
            val progress = ((index.toFloat() / segments.size) * 100).toInt()
            emit(Result.Progress(progress, "Saving segment ${index + 1} of ${segments.size}"))

            val extension = if (!params.keepVideo) "m4a" else "mp4"
            val timeSuffix = "_${TimeUtils.formatFilenameDuration(segment.startMs)}" +
                    "-${TimeUtils.formatFilenameDuration(segment.endMs)}"
            val baseName = selectedClip.fileName.substringBeforeLast(".")
            val outputUri = repository.createMediaOutputUri("$baseName$timeSuffix.$extension", !params.keepVideo)

            if (outputUri == null) {
                errors.add("Failed to create output file for segment ${index + 1}")
                continue
            }

            val result = repository.executeLosslessCut(
                selectedClip.uri, outputUri, segment.startMs, segment.endMs,
                params.keepAudio, params.keepVideo, 
                params.rotationOverride ?: selectedClip.rotation, params.selectedTracks
            )
            result.fold(
                onSuccess = { successCount++ },
                onFailure = { errors.add("Segment ${index + 1} failed: ${it.message}") }
            )
        }

        if (errors.isEmpty() && successCount > 0) {
            emit(Result.Success(successCount))
        } else if (errors.isNotEmpty()) {
            emit(Result.Failure(errors.joinToString("\n")))
        }
    }
}
