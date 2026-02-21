package com.tazztone.losslesscut

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tazztone.losslesscut.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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

@HiltViewModel
class VideoEditingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: LosslessEngineInterface,
    private val storageUtils: StorageUtils,
    private val preferences: AppPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<VideoEditingUiState>(VideoEditingUiState.Initial)
    val uiState: StateFlow<VideoEditingUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private var currentVideoUri: Uri? = null
    private var videoFileName: String = "video.mp4"
    private var videoDurationMs: Long = 0
    private var videoFps: Float = 30f
    private var currentKeyframes: List<Long> = emptyList()
    private var history = mutableListOf<List<TrimSegment>>()
    private var currentSegments = listOf<TrimSegment>()
    private var selectedSegmentId: UUID? = null
    
    private var undoLimit = 30
    
    companion object {
        const val MIN_SEGMENT_DURATION_MS = 100L
    }

    init {
        viewModelScope.launch {
            preferences.undoLimitFlow.collect {
                undoLimit = it
            }
        }
    }

    fun initialize(videoUri: Uri?) {
        if (videoUri == null) {
            _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_invalid_video_uri_msg))
            return
        }
        currentVideoUri = videoUri
        
        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading
            try {
                val keyframes = engine.probeKeyframes(context, videoUri)
                currentKeyframes = keyframes
                
                val metadata = storageUtils.getVideoMetadata(videoUri)
                val fileName = metadata.fileName
                val durationMs = metadata.durationMs
                videoFileName = fileName
                videoDurationMs = durationMs
 
                videoFps = extractVideoFps(context, videoUri)
 
                // Initial segment covering the whole video
                currentSegments = listOf(TrimSegment(startMs = 0, endMs = videoDurationMs))
                history.clear()
                history.add(currentSegments.map { it.copy() }) // Push initial state
                _isDirty.value = false
 
                updateSuccessState()
            } catch (e: Exception) {
                val msg = context.getString(R.string.error_load_video, e.message)
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
        
        while (history.size > undoLimit) {
            history.removeAt(0)
        }
        _isDirty.value = true
    }

    fun undo() {
        if (history.size > 1) {
            history.removeAt(history.size - 1) // Remove current
            currentSegments = history.last().map { it.copy() }
            selectedSegmentId = null
            updateSuccessState()
            if (history.size <= 1) {
                _isDirty.value = false
            }
        }
    }

    fun selectSegment(id: UUID?) {
        selectedSegmentId = id
        updateSuccessState()
    }

    fun splitSegmentAt(timeMs: Long) {
        val segmentToSplit = currentSegments.find { timeMs >= it.startMs && timeMs <= it.endMs } ?: return
        
        // Ensure both resulting segments have at least MIN_SEGMENT_DURATION_MS
        if (timeMs - segmentToSplit.startMs < MIN_SEGMENT_DURATION_MS || 
            segmentToSplit.endMs - timeMs < MIN_SEGMENT_DURATION_MS) {
            viewModelScope.launch { 
                _uiEvents.emit(context.getString(R.string.error_segment_too_small_split)) 
            }
            return
        }

        val index = currentSegments.indexOf(segmentToSplit)
        val newSegments = currentSegments.toMutableList()
        
        val left = segmentToSplit.copy(id = UUID.randomUUID(), endMs = timeMs)
        val right = segmentToSplit.copy(id = UUID.randomUUID(), startMs = timeMs) // Removed +1 to avoid sub-millisecond gaps if they ever arise, or just to keep it clean
        
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
                viewModelScope.launch { _uiEvents.emit(context.getString(R.string.error_cannot_delete_last)) }
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
                viewModelScope.launch { _uiEvents.emit(context.getString(R.string.error_cannot_delete_last)) }
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
            if (it.id == id) {
                // Final safety check to prevent zero or sub-minimum segments
                if (endMs - startMs < MIN_SEGMENT_DURATION_MS) {
                    val current = it
                    if (startMs != current.startMs) {
                        // Left handle was moved
                        it.copy(startMs = (endMs - MIN_SEGMENT_DURATION_MS).coerceAtLeast(0))
                    } else {
                        // Right handle was moved
                        it.copy(endMs = startMs + MIN_SEGMENT_DURATION_MS)
                    }
                } else {
                    it.copy(startMs = startMs, endMs = endMs)
                }
            } else {
                it
            }
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
            _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_no_segments_export))
            return
        }

        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading
            
            if (isLossless) {
                var successCount = 0
                val errors = mutableListOf<String>()
 
                for ((index, segment) in segmentsToExport.withIndex()) {
                    val outputUri = storageUtils.createVideoOutputUri("clip_${System.currentTimeMillis()}_$index.mp4")
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
                    _isDirty.value = false
                    updateSuccessState()
                } else if (successCount > 0) {
                    _uiEvents.emit(context.getString(R.string.export_partial_success, successCount, errors.joinToString()))
                    updateSuccessState()
                } else {
                    _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_export_failed, errors.joinToString()))
                }
            } else {
                _uiEvents.emit(context.getString(R.string.precise_mode_coming_soon))
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
                retriever.setDataSource(context, uri)
                
                // Retriever times are in microseconds
                val bitmap = retriever.getFrameAtTime(currentPositionMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                
                if (bitmap != null) {
                    val formatSetting = preferences.snapshotFormatFlow.first()
                    val isJpeg = formatSetting == "JPEG"
                    val ext = if (isJpeg) "jpeg" else "png"
                    val compressFormat = if (isJpeg) android.graphics.Bitmap.CompressFormat.JPEG else android.graphics.Bitmap.CompressFormat.PNG
                    val quality = if (isJpeg) preferences.jpgQualityFlow.first() else 100

                    val fileName = "snapshot_${System.currentTimeMillis()}.$ext"
                    val outputUri = storageUtils.createImageOutputUri(fileName)
                    if (outputUri != null) {
                        val resolver = context.contentResolver
                        val outputStream = resolver.openOutputStream(outputUri)
                        if (outputStream != null) {
                            bitmap.compress(compressFormat, quality, outputStream)
                            outputStream.close()
                            storageUtils.finalizeImage(outputUri)
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
                _uiEvents.emit(context.getString(R.string.error_snapshot_failed_generic))
            } finally {
                retriever.release()
                updateSuccessState()
            }
        }
    }
}
