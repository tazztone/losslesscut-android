package com.tazztone.losslesscut

import android.content.Context
import android.app.Application
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
        val canUndo: Boolean = false
    ) : VideoEditingUiState()
    data class Error(val message: String) : VideoEditingUiState()
}

class VideoEditingViewModel(
    application: Application,
    private val engine: LosslessEngineInterface = LosslessEngineImpl
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<VideoEditingUiState>(VideoEditingUiState.Initial)
    val uiState: StateFlow<VideoEditingUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    private var currentVideoUri: Uri? = null
    private var videoDurationMs: Long = 0
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
                
                val metadata = StorageUtils.getVideoMetadata(context, videoUri)
                val fileName = metadata.first
                videoDurationMs = metadata.second

                // Initial segment covering the whole video
                currentSegments = listOf(TrimSegment(startMs = 0, endMs = videoDurationMs))
                history.clear()
                history.add(currentSegments.map { it.copy() }) // Push initial state

                updateSuccessState(keyframes)
            } catch (e: Exception) {
                _uiState.value = VideoEditingUiState.Error("Failed to load video: ${e.message}")
            }
        }
    }

    private fun updateSuccessState(keyframes: List<Long> = emptyList()) {
        val currentState = _uiState.value
        val kf = if (currentState is VideoEditingUiState.Success) currentState.keyframes else keyframes
        
        _uiState.value = VideoEditingUiState.Success(
            videoUri = currentVideoUri!!,
            videoFileName = (currentState as? VideoEditingUiState.Success)?.videoFileName ?: "video.mp4",
            keyframes = kf,
            segments = currentSegments.map { it.copy() }, // Deep copy
            selectedSegmentId = selectedSegmentId,
            canUndo = history.size > 1
        )
    }

    private fun pushToHistory() {
        if (history.size >= 30) {
            history.removeAt(0)
        }
        history.add(currentSegments.map { it.copy() })
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
            _uiState.value = VideoEditingUiState.Error("No segments to export")
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
                        errors.add("Failed to create file")
                        continue
                    }

                    val result = engine.executeLosslessCut(context, uri, outputUri, segment.startMs, segment.endMs, keepAudio, keepVideo, rotationOverride)
                    result.fold(
                        onSuccess = { successCount++ },
                        onFailure = { errors.add("Segment $index failed: ${it.message}") }
                    )
                }

                if (errors.isEmpty() && successCount > 0) {
                    _uiEvents.emit("Successfully exported $successCount clip(s) to Movies/LosslessCut")
                    _uiState.value = VideoEditingUiState.Success(currentVideoUri!!, "video.mp4", _uiState.value.let { if (it is VideoEditingUiState.Success) it.keyframes else emptyList() }, currentSegments, selectedSegmentId, history.size > 1)
                } else if (successCount > 0) {
                    _uiEvents.emit("Exported $successCount clips. Errors: ${errors.joinToString()}")
                    _uiState.value = VideoEditingUiState.Success(currentVideoUri!!, "video.mp4", _uiState.value.let { if (it is VideoEditingUiState.Success) it.keyframes else emptyList() }, currentSegments, selectedSegmentId, history.size > 1)
                } else {
                    _uiState.value = VideoEditingUiState.Error("Export failed: ${errors.joinToString()}")
                }
            } else {
                _uiEvents.emit("Precise mode coming in v2.0")
                _uiState.value = VideoEditingUiState.Success(currentVideoUri!!, "video.mp4", _uiState.value.let { if (it is VideoEditingUiState.Success) it.keyframes else emptyList() }, currentSegments, selectedSegmentId, history.size > 1)
            }
        }
    }
}
