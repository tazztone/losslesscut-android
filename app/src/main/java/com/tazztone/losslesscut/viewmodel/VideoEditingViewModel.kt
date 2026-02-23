package com.tazztone.losslesscut.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.data.MediaClip
import com.tazztone.losslesscut.data.MediaTrack
import com.tazztone.losslesscut.data.SegmentAction
import com.tazztone.losslesscut.data.TrimSegment
import com.tazztone.losslesscut.data.VideoEditingRepository
import com.tazztone.losslesscut.di.IoDispatcher
import com.tazztone.losslesscut.domain.usecase.*
import com.tazztone.losslesscut.utils.TimeUtils
import com.tazztone.losslesscut.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

sealed class VideoEditingUiState {
    object Initial : VideoEditingUiState()
    data class Loading(val progress: Int = 0, val message: UiText? = null) : VideoEditingUiState()
    data class Success(
        val clips: List<MediaClip>,
        val selectedClipIndex: Int = 0,
        val keyframes: List<Long>,
        val segments: List<TrimSegment>,
        val selectedSegmentId: UUID? = null,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val videoFps: Float = 30f,
        val isAudioOnly: Boolean = false,
        val hasAudioTrack: Boolean = true,
        val isSnapshotInProgress: Boolean = false,
        val silencePreviewRanges: List<LongRange> = emptyList(),
        val availableTracks: List<MediaTrack> = emptyList()
    ) : VideoEditingUiState()
    data class Error(val error: UiText) : VideoEditingUiState()
}

sealed class VideoEditingEvent {
    data class ShowToast(val message: UiText) : VideoEditingEvent()
    data class ExportComplete(val success: Boolean, val count: Int = 0) : VideoEditingEvent()
    object SessionRestored : VideoEditingEvent()
}

@HiltViewModel
class VideoEditingViewModel @Inject constructor(
    private val repository: VideoEditingRepository,
    private val preferences: AppPreferences,
    private val clipManagementUseCase: ClipManagementUseCase,
    private val exportUseCase: ExportUseCase,
    private val snapshotUseCase: ExtractSnapshotUseCase,
    private val silenceDetectionUseCase: SilenceDetectionUseCase,
    private val sessionUseCase: SessionUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<VideoEditingUiState>(VideoEditingUiState.Initial)
    val uiState: StateFlow<VideoEditingUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<VideoEditingEvent>()
    val uiEvents: SharedFlow<VideoEditingEvent> = _uiEvents.asSharedFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _waveformData = MutableStateFlow<FloatArray?>(null)
    val waveformData: StateFlow<FloatArray?> = _waveformData.asStateFlow()

    private val _silencePreviewRanges = MutableStateFlow<List<LongRange>>(emptyList())
    val silencePreviewRanges: StateFlow<List<LongRange>> = _silencePreviewRanges.asStateFlow()

    private var currentClips = listOf<MediaClip>()
    private var selectedClipIndex = 0
    private var currentKeyframes: List<Long> = emptyList()
    private var history = mutableListOf<List<MediaClip>>()
    private var redoStack = mutableListOf<List<MediaClip>>()
    private var selectedSegmentId: UUID? = null
    
    private val keyframeCache = object : LinkedHashMap<String, List<Long>>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Long>>?): Boolean {
            return size > 20
        }
    }
    private val isSnapshotInProgress = AtomicBoolean(false)
    private var waveformJob: Job? = null
    private var silencePreviewJob: Job? = null
    
    private var undoLimit = 30

    companion object {
        const val MIN_SEGMENT_DURATION_MS = 100L
    }

    fun initialize(uris: List<Uri>) {
        if (_uiState.value is VideoEditingUiState.Success || _uiState.value is VideoEditingUiState.Loading) {
            reset()
        }
        
        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading()
            try {
                repository.evictOldCacheFiles()
                val result = clipManagementUseCase.createClips(uris)
                result.fold(
                    onSuccess = { clips ->
                        currentClips = clips
                        selectedClipIndex = 0
                        loadClipData(selectedClipIndex)
                    },
                    onFailure = { e ->
                        _uiState.value = VideoEditingUiState.Error(
                            UiText.StringResource(R.string.error_load_video, e.message ?: "Unknown error")
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = VideoEditingUiState.Error(
                    UiText.StringResource(R.string.error_load_video, e.message ?: "Unknown error")
                )
            }
        }
    }

    private fun loadClipData(index: Int) {
        val clip = currentClips[index]
        viewModelScope.launch {
            val cacheKey = clip.uri.toString()
            val kfs = keyframeCache[cacheKey] ?: repository.getKeyframes(clip.uri)
            keyframeCache[cacheKey] = kfs
            currentKeyframes = kfs
            
            extractWaveform(clip)
            updateState()
        }
    }

    private fun extractWaveform(clip: MediaClip) {
        waveformJob?.cancel()
        waveformJob = viewModelScope.launch {
            _waveformData.value = null
            val cacheKey = "waveform_${clip.uri.toString().hashCode()}.bin"
            val cached = repository.loadWaveformFromCache(cacheKey)
            if (cached != null) {
                _waveformData.value = cached
                return@launch
            }
            
            repository.extractWaveform(clip.uri)
                ?.let { waveform ->
                    _waveformData.value = waveform
                    repository.saveWaveformToCache(cacheKey, waveform)
                }
        }
    }

    fun selectClip(index: Int) {
        if (index == selectedClipIndex || index !in currentClips.indices) return
        selectedClipIndex = index
        loadClipData(index)
    }

    fun addClips(uris: List<Uri>) {
        viewModelScope.launch {
            val result = clipManagementUseCase.createClips(uris)
            result.fold(
                onSuccess = { newClips ->
                    saveToHistory()
                    currentClips = currentClips + newClips
                    updateState()
                },
                onFailure = { e ->
                    _uiEvents.emit(VideoEditingEvent.ShowToast(
                        UiText.StringResource(R.string.error_load_video, e.message ?: "Unknown error")
                    ))
                }
            )
        }
    }

    fun removeClip(index: Int) {
        if (currentClips.size <= 1) {
            viewModelScope.launch { 
                _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_cannot_delete_last))) 
            }
            return
        }
        saveToHistory()
        val newList = currentClips.toMutableList()
        newList.removeAt(index)
        currentClips = newList
        if (selectedClipIndex >= currentClips.size) {
            selectedClipIndex = currentClips.size - 1
        }
        loadClipData(selectedClipIndex)
    }

    fun reorderClips(from: Int, to: Int) {
        if (from == to || from !in currentClips.indices || to !in currentClips.indices) return
        
        saveToHistory()
        currentClips = clipManagementUseCase.reorderClips(currentClips, from, to)
        
        // Update selectedIndex
        if (selectedClipIndex == from) {
            selectedClipIndex = to
        } else if (from < selectedClipIndex && to >= selectedClipIndex) {
            selectedClipIndex--
        } else if (from > selectedClipIndex && to <= selectedClipIndex) {
            selectedClipIndex++
        }
        
        _isDirty.value = true
        updateState()
    }

    fun selectSegment(id: UUID?) {
        selectedSegmentId = id
        updateState()
    }

    fun splitSegmentAt(positionMs: Long) {
        val currentClip = currentClips[selectedClipIndex]
        val updatedClip = clipManagementUseCase.splitSegment(currentClip, positionMs, MIN_SEGMENT_DURATION_MS)
        
        if (updatedClip == null) {
            viewModelScope.launch { 
                _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_segment_too_small_split))) 
            }
            return
        }

        saveToHistory()
        currentClips = currentClips.toMutableList().apply { this[selectedClipIndex] = updatedClip }
        _isDirty.value = true
        updateState()
    }

    fun markSegmentDiscarded(id: UUID) {
        val currentClip = currentClips.getOrNull(selectedClipIndex) ?: return
        val updatedClip = clipManagementUseCase.markSegmentDiscarded(currentClip, id)
        
        if (updatedClip == null) {
            viewModelScope.launch {
                _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_cannot_discard_last)))
            }
            return
        }

        saveToHistory()
        currentClips = currentClips.toMutableList().apply { this[selectedClipIndex] = updatedClip }
        _isDirty.value = true
        updateState()
    }

    fun updateSegmentBounds(id: UUID, start: Long, end: Long) {
        val currentClip = currentClips[selectedClipIndex]
        val updatedClip = clipManagementUseCase.updateSegmentBounds(currentClip, id, start, end)
        currentClips = currentClips.toMutableList().apply { this[selectedClipIndex] = updatedClip }
        updateState()
    }

    fun commitSegmentBounds() {
        saveToHistory()
        _isDirty.value = true
    }

    fun undo() {
        if (history.isNotEmpty()) {
            redoStack.add(currentClips.map { it.copy(segments = it.segments.toList()) })
            currentClips = history.removeAt(history.size - 1)
            if (selectedClipIndex >= currentClips.size) {
                selectedClipIndex = currentClips.size - 1
            }
            updateState()
            loadClipData(selectedClipIndex)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            history.add(currentClips.map { it.copy(segments = it.segments.toList()) })
            currentClips = redoStack.removeAt(redoStack.size - 1)
            if (selectedClipIndex >= currentClips.size) {
                selectedClipIndex = currentClips.size - 1
            }
            updateState()
            loadClipData(selectedClipIndex)
        }
    }

    fun reset() {
        _uiState.value = VideoEditingUiState.Initial
        currentClips = emptyList()
        selectedClipIndex = 0
        history.clear()
        redoStack.clear()
        _isDirty.value = false
        _waveformData.value = null
        _silencePreviewRanges.value = emptyList()
    }

    private fun saveToHistory() {
        history.add(currentClips.map { it.copy(segments = it.segments.toList()) })
        if (history.size > undoLimit) history.removeAt(0)
        redoStack.clear()
    }

    private fun updateState() {
        val clip = currentClips.getOrNull(selectedClipIndex) ?: return
        _uiState.value = VideoEditingUiState.Success(
            clips = currentClips,
            selectedClipIndex = selectedClipIndex,
            keyframes = currentKeyframes,
            segments = clip.segments,
            selectedSegmentId = selectedSegmentId,
            canUndo = history.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            videoFps = clip.fps,
            isAudioOnly = clip.isAudioOnly,
            hasAudioTrack = clip.audioMime != null,
            isSnapshotInProgress = isSnapshotInProgress.get(),
            silencePreviewRanges = _silencePreviewRanges.value,
            availableTracks = clip.availableTracks
        )
    }

    fun previewSilenceSegments(threshold: Float, minSilenceMs: Long, paddingMs: Long, minSegmentMs: Long) {
        val waveform = _waveformData.value ?: return
        val clip = currentClips.getOrNull(selectedClipIndex) ?: return
        
        silencePreviewJob?.cancel()
        silencePreviewJob = viewModelScope.launch {
            val ranges = silenceDetectionUseCase.findSilence(
                waveform, threshold, minSilenceMs, clip.durationMs, paddingMs, minSegmentMs
            )
            _silencePreviewRanges.value = ranges
            updateState()
        }
    }

    fun clearSilencePreview() {
        silencePreviewJob?.cancel()
        _silencePreviewRanges.value = emptyList()
        updateState()
    }

    fun applySilenceDetection() {
        val ranges = _silencePreviewRanges.value
        if (ranges.isEmpty()) return
        
        saveToHistory()
        val clip = currentClips[selectedClipIndex]
        
        // Logic for applying silence detection (kept here as it involves multi-clip/multi-segment orchestration)
        // or could be moved to another use case.
        val newSegments = mutableListOf<TrimSegment>()
        clip.segments.forEach { seg ->
            if (seg.action == SegmentAction.DISCARD) {
                newSegments.add(seg)
                return@forEach
            }
            val segRanges = mutableListOf<LongRange>()
            segRanges.add(seg.startMs..seg.endMs)
            
            ranges.forEach { silenceRange ->
                val it = segRanges.iterator()
                val added = mutableListOf<LongRange>()
                while (it.hasNext()) {
                    val curr = it.next()
                    val overlapStart = maxOf(curr.first, silenceRange.first)
                    val overlapEnd = minOf(curr.last, silenceRange.last)
                    
                    if (overlapStart < overlapEnd && (overlapEnd - overlapStart) >= MIN_SEGMENT_DURATION_MS) {
                        it.remove()
                        if (overlapStart > curr.first + MIN_SEGMENT_DURATION_MS) {
                            added.add(curr.first..overlapStart)
                        }
                        if (overlapEnd < curr.last - MIN_SEGMENT_DURATION_MS) {
                            added.add(overlapEnd..curr.last)
                        }
                    }
                }
                segRanges.addAll(added)
                segRanges.sortBy { it.first }
            }
            
            var currentPos = seg.startMs
            segRanges.forEach { keepRange ->
                if (keepRange.first > currentPos + MIN_SEGMENT_DURATION_MS) {
                    newSegments.add(seg.copy(id = UUID.randomUUID(), startMs = currentPos, endMs = keepRange.first, action = SegmentAction.DISCARD))
                }
                newSegments.add(seg.copy(id = UUID.randomUUID(), startMs = keepRange.first, endMs = keepRange.last, action = SegmentAction.KEEP))
                currentPos = keepRange.last
            }
            if (currentPos < seg.endMs - MIN_SEGMENT_DURATION_MS) {
                newSegments.add(seg.copy(id = UUID.randomUUID(), startMs = currentPos, endMs = seg.endMs, action = SegmentAction.DISCARD))
            }
        }
        
        currentClips = currentClips.toMutableList().apply {
            this[selectedClipIndex] = clip.copy(segments = newSegments.sortedBy { it.startMs })
        }
        
        _silencePreviewRanges.value = emptyList()
        _isDirty.value = true
        updateState()
    }

    fun exportSegments(isLossless: Boolean, keepAudio: Boolean, keepVideo: Boolean, rotationOverride: Int?, mergeSegments: Boolean, selectedTracks: List<Int>? = null) {
        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading()
            exportUseCase.execute(
                clips = currentClips,
                selectedClipIndex = selectedClipIndex,
                isLossless = isLossless,
                keepAudio = keepAudio,
                keepVideo = keepVideo,
                rotationOverride = rotationOverride,
                mergeSegments = mergeSegments,
                selectedTracks = selectedTracks
            ) { result ->
                when (result) {
                    is ExportUseCase.Result.Progress -> {
                        _uiState.value = VideoEditingUiState.Loading(result.percentage, result.message)
                    }
                    is ExportUseCase.Result.Success -> {
                        _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.export_success, result.count)))
                        _uiEvents.emit(VideoEditingEvent.ExportComplete(true, result.count))
                        _isDirty.value = false
                        updateState()
                    }
                    is ExportUseCase.Result.Failure -> {
                        _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.DynamicString(result.error)))
                        _uiEvents.emit(VideoEditingEvent.ExportComplete(false))
                        updateState()
                    }
                }
            }
        }
    }

    fun extractSnapshot(positionMs: Long) {
        viewModelScope.launch {
            if (!isSnapshotInProgress.compareAndSet(false, true)) return@launch
            updateState()
            
            val clip = currentClips[selectedClipIndex]
            val result = snapshotUseCase.execute(clip.uri, positionMs)
            
            when (result) {
                is ExtractSnapshotUseCase.Result.Success -> {
                    _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.snapshot_saved, result.fileName)))
                }
                is ExtractSnapshotUseCase.Result.Failure -> {
                    _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.snapshot_failed)))
                }
            }
            
            isSnapshotInProgress.set(false)
            updateState()
        }
    }

    fun saveSession() {
        viewModelScope.launch {
            sessionUseCase.saveSession(currentClips)
        }
    }

    suspend fun hasSavedSession(uri: Uri): Boolean {
        return sessionUseCase.hasSavedSession(uri)
    }

    fun restoreSession(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = VideoEditingUiState.Loading()
                val validClips = sessionUseCase.restoreSession(uri)

                if (validClips.isNullOrEmpty()) {
                    _uiEvents.emit(VideoEditingEvent.ShowToast(
                        UiText.StringResource(R.string.error_restore_failed_files_missing)
                    ))
                    updateState()
                    return@launch
                }

                currentClips = validClips
                selectedClipIndex = 0
                _isDirty.value = true
                loadClipData(selectedClipIndex)
                _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.session_restored)))
                _uiEvents.emit(VideoEditingEvent.SessionRestored)
            } catch (e: Exception) {
                Log.e("VideoEditingViewModel", "Failed to restore session", e)
                _uiEvents.emit(VideoEditingEvent.ShowToast(
                    UiText.StringResource(R.string.error_restore_failed, e.message ?: "")
                ))
                updateState()
            }
        }
    }
}
