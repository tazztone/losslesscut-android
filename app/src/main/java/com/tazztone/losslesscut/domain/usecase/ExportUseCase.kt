package com.tazztone.losslesscut.domain.usecase

import android.net.Uri
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.data.MediaClip
import com.tazztone.losslesscut.data.SegmentAction
import com.tazztone.losslesscut.data.VideoEditingRepository
import com.tazztone.losslesscut.di.IoDispatcher
import com.tazztone.losslesscut.utils.TimeUtils
import com.tazztone.losslesscut.utils.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExportUseCase @Inject constructor(
    private val repository: VideoEditingRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    sealed class Result {
        data class Success(val count: Int) : Result()
        data class Failure(val error: String) : Result()
        data class Progress(val percentage: Int, val message: UiText) : Result()
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

                onStatus(Result.Progress(0, UiText.StringResource(R.string.export_merging)))
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
                        UiText.StringResource(R.string.export_saving_segment, index + 1, segments.size)
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
                        selectedClip.uri, outputUri, segment.startMs, segment.endMs,
                        keepAudio, keepVideo, rotationOverride, selectedTracks
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
