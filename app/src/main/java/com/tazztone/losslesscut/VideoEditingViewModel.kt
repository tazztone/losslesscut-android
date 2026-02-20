package com.tazztone.losslesscut

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.Stack

enum class SegmentAction { KEEP, DISCARD }

data class TrimSegment(
    val id: UUID = UUID.randomUUID(),
    var startMs: Long,
    var endMs: Long,
    var action: SegmentAction = SegmentAction.KEEP
)

sealed class VideoEditingUiState {
    object Initial : VideoEditingUiState()
    object Loading : VideoEditingUiState()
    data class Success(
        val videoUri: Uri,
        val videoFileName: String,
        val keyframes: List<Float>,
        val segments: List<TrimSegment>,
        val selectedSegmentId: UUID? = null,
        val canUndo: Boolean = false
    ) : VideoEditingUiState()
    data class Error(val message: String) : VideoEditingUiState()
    data class EventMessage(val message: String) : VideoEditingUiState()
}

class VideoEditingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<VideoEditingUiState>(VideoEditingUiState.Initial)
    val uiState: StateFlow<VideoEditingUiState> = _uiState.asStateFlow()

    private var currentVideoUri: Uri? = null
    private var videoDurationMs: Long = 0
    private var history = Stack<List<TrimSegment>>()
    private var currentSegments = listOf<TrimSegment>()
    private var selectedSegmentId: UUID? = null

    fun initialize(context: Context, videoUri: Uri?) {
        if (videoUri == null) {
            _uiState.value = VideoEditingUiState.Error("Invalid video URI")
            return
        }
        currentVideoUri = videoUri
        
        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading
            try {
                val keyframes = LosslessEngine.probeKeyframes(context, videoUri)
                
                var fileName = "video.mp4"
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, videoUri)
                    fileName = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?: videoUri.lastPathSegment ?: "video.mp4"
                    
                    val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    videoDurationMs = durationStr?.toLong() ?: 0
                } catch (e: Exception) {
                    android.util.Log.w("ViewModel", "Failed to extract metadata: ${e.message}")
                } finally {
                    retriever.release()
                }

                // Initial segment covering the whole video
                currentSegments = listOf(TrimSegment(startMs = 0, endMs = videoDurationMs))
                history.clear()
                pushToHistory() // Push initial state

                updateSuccessState(keyframes.map { it.toFloat() })
            } catch (e: Exception) {
                _uiState.value = VideoEditingUiState.Error("Failed to load video: ${e.message}")
            }
        }
    }

    private fun updateSuccessState(keyframes: List<Float> = emptyList()) {
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
            history.removeElementAt(0)
        }
        history.push(currentSegments.map { it.copy() })
    }

    fun undo() {
        if (history.size > 1) {
            history.pop() // Remove current
            currentSegments = history.peek().map { it.copy() }
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
        currentSegments.find { it.id == id }?.let {
            it.action = if (it.action == SegmentAction.KEEP) SegmentAction.DISCARD else SegmentAction.KEEP
            pushToHistory()
            updateSuccessState()
        }
    }

    fun deleteSegment(id: UUID) {
        val segment = currentSegments.find { it.id == id } ?: return
        segment.action = SegmentAction.DISCARD
        pushToHistory()
        updateSuccessState()
    }

    fun updateSegmentBounds(id: UUID, startMs: Long, endMs: Long) {
        currentSegments.find { it.id == id }?.let {
            it.startMs = startMs
            it.endMs = endMs
            updateSuccessState()
        }
    }

    fun commitSegmentBounds() {
        pushToHistory()
        updateSuccessState()
    }

    fun acknowledgeMessage() {
        updateSuccessState()
    }

    fun exportSegments(context: Context, isLossless: Boolean, keepAudio: Boolean = true, keepVideo: Boolean = true, rotationOverride: Int? = null) {
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

                for ((index, segment) in segmentsToExport.withIndex()) {
                    val outputUri = StorageUtils.createVideoOutputUri(context, "clip_${System.currentTimeMillis()}_$index.mp4")
                    if (outputUri == null) {
                        errors.add("Failed to create file")
                        continue
                    }

                    val result = LosslessEngine.executeLosslessCut(context, uri, outputUri, segment.startMs, segment.endMs, keepAudio, keepVideo, rotationOverride)
                    result.fold(
                        onSuccess = { successCount++ },
                        onFailure = { errors.add("Segment $index failed: ${it.message}") }
                    )
                }

                if (errors.isEmpty() && successCount > 0) {
                    _uiState.value = VideoEditingUiState.EventMessage("Successfully exported $successCount clip(s) to Movies/LosslessCut")
                } else if (successCount > 0) {
                    _uiState.value = VideoEditingUiState.EventMessage("Exported $successCount clips. Errors: ${errors.joinToString()}")
                } else {
                    _uiState.value = VideoEditingUiState.Error("Export failed: ${errors.joinToString()}")
                }
            } else {
                _uiState.value = VideoEditingUiState.Error("Precise mode coming in v2.0")
            }
        }
    }
}
