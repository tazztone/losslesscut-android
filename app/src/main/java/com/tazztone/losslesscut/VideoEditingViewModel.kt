package com.tazztone.losslesscut

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class SegmentAction { KEEP, DISCARD }

data class TrimSegment(
    val id: UUID = UUID.randomUUID(),
    val startMs: Long,
    val endMs: Long,
    val action: SegmentAction = SegmentAction.KEEP
)

sealed class VideoEditingUiState {
    object Initial : VideoEditingUiState()
    object Loading : VideoEditingUiState()
    data class Success(
        val videoUri: Uri,
        val videoFileName: String,
        val keyframes: List<Long>,
        val segments: List<TrimSegment>,
        val selectedSegmentId: UUID? = null,
        val canUndo: Boolean = false,
        val videoFps: Float = 30f
    ) : VideoEditingUiState()
    data class Error(val message: String) : VideoEditingUiState()
}

class VideoEditingViewModel(
    application: Application,
    private val engine: LosslessEngineInterface = LosslessEngineImpl,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<VideoEditingUiState>(VideoEditingUiState.Initial)
    val uiState: StateFlow<VideoEditingUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    private var currentVideoUri: Uri? = null
    private var videoFileName: String = "video.mp4"
    private var videoDurationMs: Long = 0
    private var videoFps: Float = 30f
    private var currentKeyframes: List<Long> = emptyList()
    private var history = mutableListOf<List<TrimSegment>>()
    private var currentSegments = listOf<TrimSegment>()
    private var selectedSegmentId: UUID? = null

    fun initialize(videoUri: Uri?) {
        if (videoUri == null) {
            _uiState.value = VideoEditingUiState.Error("Invalid video URI")
            return
        }
        currentVideoUri = videoUri
        
        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading
            try {
                val context = getApplication<Application>()
                val keyframes = engine.probeKeyframes(context, videoUri)
                currentKeyframes = keyframes
                
                val metadata = StorageUtils.getVideoMetadata(getApplication(), videoUri)
                val fileName = metadata.fileName
                val durationMs = metadata.durationMs
                videoFileName = fileName
                videoDurationMs = durationMs

                videoFps = extractVideoFps(context, videoUri)

                // Initial segment covering the whole video
                currentSegments = listOf(TrimSegment(startMs = 0, endMs = videoDurationMs))
                history.clear()
                history.add(currentSegments.map { it.copy() }) // Push initial state

                updateSuccessState()
            } catch (e: Exception) {
                val msg = getApplication<Application>().getString(R.string.error_load_video, e.message)
                _uiState.value = VideoEditingUiState.Error(msg)
            }
        }
    }

    private suspend fun extractVideoFps(context: Context, videoUri: Uri): Float = kotlinx.coroutines.withContext(ioDispatcher) {
        var fps = 30f
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val fpsStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val extractedFps = fpsStr?.toFloatOrNull()
            if (extractedFps != null && extractedFps > 0f) {
                fps = extractedFps
            } else {
                val extractor = android.media.MediaExtractor()
                extractor.setDataSource(context, videoUri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                    if (mime?.startsWith("video/") == true && format.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)) {
                        val frameRate = format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE).toFloat()
                        if (frameRate > 0f) {
                            fps = frameRate
                        }
                        break
                    }
                }
                extractor.release()
            }
        } catch (e: Exception) {
            android.util.Log.w("VideoEditingViewModel", "Failed to extract FPS", e)
        } finally {
            retriever.release()
        }
        fps
    }

    private fun updateSuccessState() {
        
        _uiState.value = VideoEditingUiState.Success(
            videoUri = currentVideoUri!!,
            videoFileName = videoFileName,
            keyframes = currentKeyframes,
            segments = currentSegments.map { it.copy() }, // Deep copy
            selectedSegmentId = selectedSegmentId,
            canUndo = history.size > 1,
            videoFps = videoFps
        )
    }

    private fun pushToHistory() {
        val newState = currentSegments.map { it.copy() }
        
        // Prevent storing duplicate consecutive states
        if (history.isNotEmpty() && history.last() == newState) return
        
        history.add(newState)
        
        val limit = AppPreferences.getUndoLimit(getApplication<Application>())
        while (history.size > limit) {
            history.removeAt(0)
        }
    }

    fun undo() {
        if (history.size > 1) {
            history.removeAt(history.size - 1) // Remove current
            currentSegments = history.last().map { it.copy() }
            selectedSegmentId = null
            updateSuccessState()
        }
    }

    fun selectSegment(id: UUID?) {
        selectedSegmentId = id
        updateSuccessState()
    }

    fun splitSegmentAt(timeMs: Long) {
        val segmentToSplit = currentSegments.find { timeMs >= it.startMs && timeMs <= it.endMs } ?: return
        
        val index = currentSegments.indexOf(segmentToSplit)
        val newSegments = currentSegments.toMutableList()
        
        val left = segmentToSplit.copy(id = UUID.randomUUID(), endMs = timeMs)
        val right = segmentToSplit.copy(id = UUID.randomUUID(), startMs = timeMs + 1)
        
        newSegments.removeAt(index)
        newSegments.add(index, left)
        newSegments.add(index + 1, right)
        
        currentSegments = newSegments
        selectedSegmentId = right.id
        pushToHistory()
        updateSuccessState()
    }

    fun toggleSegmentAction(id: UUID) {
        val segment = currentSegments.find { it.id == id } ?: return
        if (segment.action == SegmentAction.KEEP) {
            val keepCount = currentSegments.count { it.action == SegmentAction.KEEP }
            if (keepCount <= 1) {
                viewModelScope.launch { _uiEvents.emit(getApplication<Application>().getString(R.string.error_cannot_delete_last)) }
                return
            }
        }
        
        currentSegments = currentSegments.map {
            if (it.id == id) {
                it.copy(action = if (it.action == SegmentAction.KEEP) SegmentAction.DISCARD else SegmentAction.KEEP)
            } else {
                it
            }
        }
        pushToHistory()
        updateSuccessState()
    }

    fun deleteSegment(id: UUID) {
        val segment = currentSegments.find { it.id == id } ?: return
        if (segment.action == SegmentAction.KEEP) {
            val keepCount = currentSegments.count { it.action == SegmentAction.KEEP }
            if (keepCount <= 1) {
                viewModelScope.launch { _uiEvents.emit(getApplication<Application>().getString(R.string.error_cannot_delete_last)) }
                return
            }
        }
        
        currentSegments = currentSegments.map {
            if (it.id == id) it.copy(action = SegmentAction.DISCARD) else it
        }
        pushToHistory()
        updateSuccessState()
    }

    fun updateSegmentBounds(id: UUID, startMs: Long, endMs: Long) {
        currentSegments = currentSegments.map {
            if (it.id == id) it.copy(startMs = startMs, endMs = endMs) else it
        }
        updateSuccessState()
    }

    fun commitSegmentBounds() {
        pushToHistory()
        updateSuccessState()
    }

    fun exportSegments(isLossless: Boolean, keepAudio: Boolean = true, keepVideo: Boolean = true, rotationOverride: Int? = null) {
        val uri = currentVideoUri ?: return
        val segmentsToExport = currentSegments.filter { it.action == SegmentAction.KEEP }
        if (segmentsToExport.isEmpty()) {
            _uiState.value = VideoEditingUiState.Error(getApplication<Application>().getString(R.string.error_no_segments_export))
            return
        }

        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading
            
            if (isLossless) {
                var successCount = 0
                val errors = mutableListOf<String>()

                val context = getApplication<Application>()
                for ((index, segment) in segmentsToExport.withIndex()) {
                    val outputUri = StorageUtils.createVideoOutputUri(context, "clip_${System.currentTimeMillis()}_$index.mp4")
                    if (outputUri == null) {
                        errors.add(context.getString(R.string.error_create_file))
                        continue
                    }

                    val result = engine.executeLosslessCut(context, uri, outputUri, segment.startMs, segment.endMs, keepAudio, keepVideo, rotationOverride)
                    result.fold(
                        onSuccess = { successCount++ },
                        onFailure = { errors.add(context.getString(R.string.error_segment_failed, index, it.message)) }
                    )
                }

                if (errors.isEmpty() && successCount > 0) {
                    _uiEvents.emit(context.getString(R.string.export_success, successCount))
                    updateSuccessState()
                } else if (successCount > 0) {
                    _uiEvents.emit(context.getString(R.string.export_partial_success, successCount, errors.joinToString()))
                    updateSuccessState()
                } else {
                    _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_export_failed, errors.joinToString()))
                }
            } else {
                _uiEvents.emit(getApplication<Application>().getString(R.string.precise_mode_coming_soon))
                updateSuccessState()
            }
        }
    }

    fun extractSnapshot(currentPositionMs: Long) {
        val uri = currentVideoUri ?: return
        viewModelScope.launch(ioDispatcher) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                _uiState.value = VideoEditingUiState.Loading
                val context = getApplication<Application>()
                retriever.setDataSource(context, uri)
                
                // Retriever times are in microseconds
                val bitmap = retriever.getFrameAtTime(currentPositionMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                
                if (bitmap != null) {
                    val formatSetting = AppPreferences.getSnapshotFormat(context)
                    val isJpeg = formatSetting == "JPEG"
                    val ext = if (isJpeg) "jpeg" else "png"
                    val compressFormat = if (isJpeg) android.graphics.Bitmap.CompressFormat.JPEG else android.graphics.Bitmap.CompressFormat.PNG
                    val quality = if (isJpeg) AppPreferences.getJpgQuality(context) else 100

                    val fileName = "snapshot_${System.currentTimeMillis()}.$ext"
                    val outputUri = StorageUtils.createImageOutputUri(context, fileName)
                    if (outputUri != null) {
                        val resolver = context.contentResolver
                        val outputStream = resolver.openOutputStream(outputUri)
                        if (outputStream != null) {
                            bitmap.compress(compressFormat, quality, outputStream)
                            outputStream.close()
                            StorageUtils.finalizeImage(context, outputUri)
                            _uiEvents.emit(context.getString(R.string.snapshot_saved, fileName))
                        } else {
                            _uiEvents.emit(context.getString(R.string.snapshot_failed))
                        }
                    } else {
                         _uiEvents.emit(context.getString(R.string.snapshot_failed))
                    }
                    bitmap.recycle()
                } else {
                    _uiEvents.emit(context.getString(R.string.snapshot_failed))
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoEditingViewModel", "Snapshot error", e)
                _uiEvents.emit("Failed to create snapshot.")
            } finally {
                retriever.release()
                updateSuccessState()
            }
        }
    }
}
