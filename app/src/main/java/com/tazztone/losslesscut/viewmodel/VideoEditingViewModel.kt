package com.tazztone.losslesscut.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.MediaTrack
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.model.UiText
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.usecase.ClipManagementUseCase
import com.tazztone.losslesscut.domain.usecase.ExportUseCase
import com.tazztone.losslesscut.domain.usecase.ExtractSnapshotUseCase
import com.tazztone.losslesscut.domain.usecase.SessionUseCase
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
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
        val availableTracks: List<MediaTrack> = emptyList(),
        val playbackSpeed: Float = 1.0f,
        val isPitchCorrectionEnabled: Boolean = false
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
    private val repository: IVideoEditingRepository,
    private val preferences: AppPreferences,
    private val useCases: VideoEditingUseCases,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<VideoEditingUiState>(VideoEditingUiState.Initial)
    val uiState: StateFlow<VideoEditingUiState> = _uiState.asStateFlow()

    private val _uiEvents = Channel<VideoEditingEvent>(Channel.BUFFERED)
    val uiEvents: Flow<VideoEditingEvent> = _uiEvents.receiveAsFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    fun clearDirty() {
        _isDirty.value = false
    }

    private val _waveformData = MutableStateFlow<FloatArray?>(null)
    val waveformData: StateFlow<FloatArray?> = _waveformData.asStateFlow()

    private val _silencePreviewRanges = MutableStateFlow<List<LongRange>>(emptyList())
    val silencePreviewRanges: StateFlow<List<LongRange>> = _silencePreviewRanges.asStateFlow()

    private val _sessionExists = MutableStateFlow(false)
    val sessionExists: StateFlow<Boolean> = _sessionExists.asStateFlow()

    private var currentPlaybackSpeed = 1.0f
    private var isPitchCorrectionEnabled = false

    private var currentClips = listOf<MediaClip>()
    private var selectedClipIndex = 0
    private var currentKeyframes: List<Long> = emptyList()
    private var selectedSegmentId: UUID? = null
    
    private val historyManager = HistoryManager(limit = 30)
    private val sessionController = SessionController(useCases.sessionUseCase, ioDispatcher)
    private val exportController = ExportController(
        useCases.exportUseCase, useCases.snapshotUseCase, preferences, ioDispatcher
    )
    private val clipController = ClipController(
        useCases.clipManagementUseCase, MIN_SEGMENT_DURATION_MS
    )
    private val stateMutex = Mutex()
    
    private val keyframeCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<Long>>(20, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Long>>?): Boolean {
                return size > 20
            }
        }
    )
    private var waveformJob: Job? = null
    private var silencePreviewJob: Job? = null

    companion object {
        const val MIN_SEGMENT_DURATION_MS = 100L
    }

    fun setPlaybackParameters(speed: Float, pitchCorrection: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                currentPlaybackSpeed = speed
                isPitchCorrectionEnabled = pitchCorrection
                updateStateInternal()
            }
        }
    }

    fun initialize(uris: List<Uri>) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                resetInternal()
                _uiState.value = VideoEditingUiState.Loading()
                try {
                    repository.evictOldCacheFiles()
                    val result = useCases.clipManagementUseCase.createClips(uris.map { it.toString() })
                    result.fold(
                        onSuccess = { clips ->
                            currentClips = clips
                            selectedClipIndex = 0
                            loadClipDataInternal(selectedClipIndex)
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
    }

    private suspend fun loadClipDataInternal(index: Int) {
        val clip = currentClips.getOrNull(index) ?: return
        val cacheKey = clip.uri
        val kfs = keyframeCache[cacheKey] ?: repository.getKeyframes(clip.uri)
        keyframeCache[cacheKey] = kfs
        currentKeyframes = kfs
        
        extractWaveformInternal(clip)
        updateStateInternal()
    }

    private fun extractWaveformInternal(clip: MediaClip) {
        waveformJob?.cancel()
        waveformJob = viewModelScope.launch(ioDispatcher) {
            _waveformData.value = null
            val cacheKey = "waveform_${clip.uri.hashCode()}.bin"
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
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                if (index == selectedClipIndex || index !in currentClips.indices) return@withLock
                selectedClipIndex = index
                loadClipDataInternal(selectedClipIndex)
            }
        }
    }

    fun addClips(uris: List<Uri>) {
        viewModelScope.launch(ioDispatcher) {
            val result = useCases.clipManagementUseCase.createClips(uris.map { it.toString() })
            result.fold(
                onSuccess = { newClips ->
                    stateMutex.withLock {
                        historyManager.save(currentClips)
                        currentClips = currentClips + newClips
                        updateStateInternal()
                    }
                },
                onFailure = { e ->
                    _uiEvents.send(VideoEditingEvent.ShowToast(
                        UiText.StringResource(R.string.error_load_video, e.message ?: "Unknown error")
                    ))
                }
            )
        }
    }

    fun removeClip(index: Int) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                if (currentClips.size <= 1) {
                    _uiEvents.send(
                        VideoEditingEvent.ShowToast(
                            UiText.StringResource(R.string.error_cannot_delete_last)
                        )
                    ) 
                    return@withLock
                }
                historyManager.save(currentClips)
                val newList = currentClips.toMutableList()
                newList.removeAt(index)
                currentClips = newList
                if (selectedClipIndex >= currentClips.size) {
                    selectedClipIndex = currentClips.size - 1
                }
                loadClipDataInternal(selectedClipIndex)
            }
        }
    }

    fun reorderClips(from: Int, to: Int) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                if (from == to || from !in currentClips.indices || to !in currentClips.indices) return@withLock
                
                historyManager.save(currentClips)
                currentClips = clipController.reorderClips(
                    currentClips, from, to
                )
                
                // Update selectedIndex
                if (selectedClipIndex == from) {
                    selectedClipIndex = to
                } else if (from < selectedClipIndex && to >= selectedClipIndex) {
                    selectedClipIndex--
                } else if (from > selectedClipIndex && to <= selectedClipIndex) {
                    selectedClipIndex++
                }
                
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    fun selectSegment(id: UUID?) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                selectedSegmentId = id
                updateStateInternal()
            }
        }
    }

    fun splitSegmentAt(positionMs: Long) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val currentClip = currentClips.getOrNull(selectedClipIndex) ?: return@withLock
                val updatedClip = clipController.splitSegment(
                    currentClip, positionMs
                )
                
                if (updatedClip == null) {
                    _uiEvents.send(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_segment_too_small_split))) 
                    return@withLock
                }

                historyManager.save(currentClips)
                currentClips = currentClips.toMutableList().apply { this[selectedClipIndex] = updatedClip }
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    fun markSegmentDiscarded(id: UUID) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val currentClip = currentClips.getOrNull(selectedClipIndex) ?: return@withLock
                val updatedClip = clipController.markSegmentDiscarded(
                    currentClip, id
                )
                
                if (updatedClip == null) {
                    _uiEvents.send(
                        VideoEditingEvent.ShowToast(
                            UiText.StringResource(R.string.error_cannot_discard_last)
                        )
                    )
                    return@withLock
                }

                historyManager.save(currentClips)
                currentClips = currentClips.toMutableList().apply { this[selectedClipIndex] = updatedClip }
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    fun updateSegmentBounds(id: UUID, start: Long, end: Long) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val currentClip = currentClips.getOrNull(selectedClipIndex) ?: return@withLock
                val updatedClip = clipController.updateSegmentBounds(currentClip, id, start, end)
                currentClips = currentClips.toMutableList().apply { this[selectedClipIndex] = updatedClip }
                updateStateInternal()
            }
        }
    }

    fun commitSegmentBounds() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                historyManager.save(currentClips)
                _isDirty.value = true
            }
        }
    }

    fun undo() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val undone = historyManager.undo(currentClips)
                if (undone != null) {
                    currentClips = undone
                    if (selectedClipIndex >= currentClips.size) {
                        selectedClipIndex = currentClips.size - 1
                    }
                    updateStateInternal()
                    loadClipDataInternal(selectedClipIndex)
                }
            }
        }
    }

    fun redo() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val redone = historyManager.redo(currentClips)
                if (redone != null) {
                    currentClips = redone
                    if (selectedClipIndex >= currentClips.size) {
                        selectedClipIndex = currentClips.size - 1
                    }
                    updateStateInternal()
                    loadClipDataInternal(selectedClipIndex)
                }
            }
        }
    }

    fun reset() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                resetInternal()
            }
        }
    }

    private fun resetInternal() {
        _uiState.value = VideoEditingUiState.Initial
        currentClips = emptyList()
        selectedClipIndex = 0
        historyManager.clear()
        _isDirty.value = false
        _waveformData.value = null
        _silencePreviewRanges.value = emptyList()
        _sessionExists.value = false
        currentPlaybackSpeed = 1.0f
        isPitchCorrectionEnabled = false
    }

    private fun updateStateInternal() {
        val clip = currentClips.getOrNull(selectedClipIndex)
        if (clip == null) {
            if (_uiState.value is VideoEditingUiState.Success) {
                _uiState.value = VideoEditingUiState.Initial
            }
            return
        }
        _uiState.value = VideoEditingUiState.Success(
            clips = currentClips,
            selectedClipIndex = selectedClipIndex,
            keyframes = currentKeyframes,
            segments = clip.segments,
            selectedSegmentId = selectedSegmentId,
            canUndo = historyManager.canUndo,
            canRedo = historyManager.canRedo,
            videoFps = clip.fps,
            isAudioOnly = clip.isAudioOnly,
            hasAudioTrack = clip.audioMime != null,
            isSnapshotInProgress = exportController.isSnapshotInProgress,
            silencePreviewRanges = _silencePreviewRanges.value,
            availableTracks = clip.availableTracks,
            playbackSpeed = currentPlaybackSpeed,
            isPitchCorrectionEnabled = isPitchCorrectionEnabled
        )
    }

    fun previewSilenceSegments(threshold: Float, minSilenceMs: Long, paddingMs: Long, minSegmentMs: Long) {
        val waveform = _waveformData.value ?: return
        
        silencePreviewJob?.cancel()
        silencePreviewJob = viewModelScope.launch(ioDispatcher) {
            val clip = stateMutex.withLock { currentClips.getOrNull(selectedClipIndex) } ?: return@launch
            val ranges = useCases.silenceDetectionUseCase.findSilence(
                waveform, threshold, minSilenceMs, clip.durationMs, paddingMs, minSegmentMs
            )
            stateMutex.withLock {
                _silencePreviewRanges.value = ranges
                updateStateInternal()
            }
        }
    }

    fun clearSilencePreview() {
        silencePreviewJob?.cancel()
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                _silencePreviewRanges.value = emptyList()
                updateStateInternal()
            }
        }
    }

    fun applySilenceDetection() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val ranges = _silencePreviewRanges.value
                if (ranges.isEmpty()) return@withLock
                
                historyManager.save(currentClips)
                val clip = currentClips.getOrNull(selectedClipIndex) ?: return@withLock
                
                val updatedClip = useCases.silenceDetectionUseCase.applySilenceDetection(
                    clip, ranges, MIN_SEGMENT_DURATION_MS
                )
                
                currentClips = currentClips.toMutableList().apply {
                    this[selectedClipIndex] = updatedClip
                }
                
                _silencePreviewRanges.value = emptyList()
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    fun exportSegments(settings: ExportSettings) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = VideoEditingUiState.Loading()
            
            val (clips, clipIndex) = stateMutex.withLock { 
                currentClips to selectedClipIndex 
            }
            
            exportController.exportSegments(clips, clipIndex, settings).collect { result ->
                when (result) {
                    is ExportUseCase.Result.Progress -> {
                        _uiState.value = VideoEditingUiState.Loading(
                            result.percentage, 
                            UiText.DynamicString(result.message)
                        )
                    }
                    is ExportUseCase.Result.Success -> {
                        _uiEvents.send(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.export_success, result.count)))
                        _uiEvents.send(VideoEditingEvent.ExportComplete(true, result.count))
                        _isDirty.value = false
                        stateMutex.withLock { updateStateInternal() }
                    }
                    is ExportUseCase.Result.Failure -> {
                        _uiEvents.send(VideoEditingEvent.ShowToast(UiText.DynamicString(result.error)))
                        _uiEvents.send(VideoEditingEvent.ExportComplete(false))
                        stateMutex.withLock { updateStateInternal() }
                    }
                }
            }
        }
    }

    fun extractSnapshot(positionMs: Long) {
        viewModelScope.launch(ioDispatcher) {
            val clip = stateMutex.withLock { 
                updateStateInternal()
                currentClips.getOrNull(selectedClipIndex) 
            }
            if (clip == null) {
                stateMutex.withLock { updateStateInternal() }
                return@launch
            }
            
            val result = exportController.extractSnapshot(clip, positionMs)
            
            when (result) {
                is ExtractSnapshotUseCase.Result.Success -> {
                    _uiEvents.send(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.snapshot_saved, result.fileName)))
                }
                is ExtractSnapshotUseCase.Result.Failure -> {
                    _uiEvents.send(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.snapshot_failed)))
                }
                null -> {} // already in progress
            }
            
            stateMutex.withLock { updateStateInternal() }
        }
    }

    fun saveSession() {
        viewModelScope.launch(ioDispatcher) {
            val clips = stateMutex.withLock { currentClips }
            sessionController.saveSession(clips)
        }
    }

    fun checkSessionExists(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val exists = sessionController.checkSessionExists(uri.toString())
            stateMutex.withLock {
                _sessionExists.value = exists
            }
        }
    }

    fun restoreSession(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _uiState.value = VideoEditingUiState.Loading()
                val validClips = sessionController.restoreSession(uri.toString())

                if (validClips.isNullOrEmpty()) {
                    _uiEvents.send(VideoEditingEvent.ShowToast(
                        UiText.StringResource(R.string.error_restore_failed_files_missing)
                    ))
                    stateMutex.withLock { updateStateInternal() }
                    return@launch
                }

                stateMutex.withLock {
                    currentClips = validClips
                    selectedClipIndex = 0
                    _isDirty.value = true
                    loadClipDataInternal(selectedClipIndex)
                }
                _uiEvents.send(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.session_restored)))
                _uiEvents.send(VideoEditingEvent.SessionRestored)
            } catch (e: Exception) {
                Log.e("VideoEditingViewModel", "Failed to restore session", e)
                _uiEvents.send(VideoEditingEvent.ShowToast(
                    UiText.StringResource(R.string.error_restore_failed, e.message ?: "")
                ))
                stateMutex.withLock { updateStateInternal() }
            }
        }
    }
}

data class VideoEditingUseCases @Inject constructor(
    val clipManagementUseCase: ClipManagementUseCase,
    val exportUseCase: ExportUseCase,
    val snapshotUseCase: ExtractSnapshotUseCase,
    val silenceDetectionUseCase: SilenceDetectionUseCase,
    val sessionUseCase: SessionUseCase
)

data class ExportSettings(
    val isLossless: Boolean,
    val keepAudio: Boolean,
    val keepVideo: Boolean,
    val rotationOverride: Int?,
    val mergeSegments: Boolean,
    val selectedTracks: List<Int>? = null
)
