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
import com.tazztone.losslesscut.domain.usecase.IVisualSegmentDetector
import com.tazztone.losslesscut.domain.model.VisualDetectionConfig
import com.tazztone.losslesscut.domain.usecase.SessionUseCase
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
public class VideoEditingViewModel @Inject constructor(
    private val repository: IVideoEditingRepository,
    private val preferences: AppPreferences,
    private val useCases: VideoEditingUseCases,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<VideoEditingUiState>(VideoEditingUiState.Initial)
    public val uiState: StateFlow<VideoEditingUiState> = _uiState.asStateFlow()

    private val _uiEvents = Channel<VideoEditingEvent>(Channel.BUFFERED)
    public val uiEvents: Flow<VideoEditingEvent> = _uiEvents.receiveAsFlow()

    private val _isDirty = MutableStateFlow(false)
    public val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    public fun clearDirty() {
        _isDirty.value = false
    }

    private val _waveformData = MutableStateFlow<FloatArray?>(null)
    public val waveformData: StateFlow<FloatArray?> = _waveformData.asStateFlow()

    private val _silencePreviewRanges = MutableStateFlow<List<LongRange>>(emptyList())
    public val silencePreviewRanges: StateFlow<List<LongRange>> = _silencePreviewRanges.asStateFlow()

    private val _rawSilencePreviewRanges = MutableStateFlow<SilenceDetectionUseCase.DetectionResult?>(null)
    public val rawSilencePreviewRanges: StateFlow<SilenceDetectionUseCase.DetectionResult?> = 
        _rawSilencePreviewRanges.asStateFlow()

    private var hintsDismissed = false

    public fun onUserInteraction() {
        if (hintsDismissed) return
        hintsDismissed = true
        viewModelScope.launch {
            _uiEvents.send(VideoEditingEvent.DismissHints)
        }
    }

    private val _sessionExists = MutableStateFlow(false)
    public val sessionExists: StateFlow<Boolean> = _sessionExists.asStateFlow()

    private var currentPlaybackSpeed = 1.0f
    private var isPitchCorrectionEnabled = false

    private var currentClips = listOf<MediaClip>()
    private var selectedClipIndex = 0
    private var currentKeyframes: List<Long> = emptyList()
    private var selectedSegmentId: UUID? = null
    
    private val historyManager = HistoryManager(limit = 30)
    private val sessionController = SessionController(useCases.sessionUseCase, ioDispatcher)
    private val exportController = ExportController(
        useCases.exportUseCase, useCases.snapshotUseCase, preferences
    )
    private val clipController = ClipController(useCases.clipManagementUseCase)
    private val waveformController = WaveformController(
        repository, useCases.silenceDetectionUseCase, ioDispatcher
    )
    public val waveformMaxAmplitude: StateFlow<Float> = waveformController.maxAmplitude
    private val stateMutex = Mutex()
    private val isExporting = AtomicBoolean(false)
    
    private val keyframeCache = ConcurrentHashMap<String, List<Long>>()
    private var lastMinSegmentMs: Long = ClipController.MIN_SEGMENT_DURATION_MS
    
    init {
        // Collect reactive state from controllers
        viewModelScope.launch {
            exportController.isSnapshotInProgress
                .collect {
                    updateStateInternal()
                }
        }
        viewModelScope.launch {
            waveformController.waveformData
                .collect { data ->
                    _waveformData.value = data
                    updateStateInternal()
                }
        }
        viewModelScope.launch {
            waveformController.silencePreviewRanges
                .collect { ranges ->
                    _silencePreviewRanges.value = ranges
                    updateStateInternal()
                }
        }
        viewModelScope.launch {
            waveformController.rawSilencePreviewRanges
                .collect { rawResult ->
                    _rawSilencePreviewRanges.value = rawResult
                }
        }
    }

    // MIN_SEGMENT_DURATION_MS moved to ClipController

    public fun setPlaybackParameters(speed: Float, pitchCorrection: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                currentPlaybackSpeed = speed
                isPitchCorrectionEnabled = pitchCorrection
                updateStateInternal()
            }
        }
    }

    public fun initialize(uris: List<Uri>) {
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
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
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
        
        // Atomic check-and-compute to prevent redundant work
        val kfs = keyframeCache[cacheKey] ?: run {
            val fetched = repository.getKeyframes(clip.uri)
            keyframeCache[cacheKey] = fetched
            fetched
        }
        currentKeyframes = kfs
        
        extractWaveformInternal(clip)
        updateStateInternal()
    }

    private fun extractWaveformInternal(clip: MediaClip) {
        waveformController.extractWaveform(viewModelScope, clip)
    }

    public fun selectClip(index: Int) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                if (index == selectedClipIndex || index !in currentClips.indices) return@withLock
                selectedClipIndex = index
                loadClipDataInternal(selectedClipIndex)
            }
        }
    }

    public fun addClips(uris: List<Uri>) {
        viewModelScope.launch(ioDispatcher) {
            val result = useCases.clipManagementUseCase.createClips(uris.map { it.toString() })
            result.fold(
                onSuccess = { newClips ->
                    stateMutex.withLock {
                        historyManager.save(currentClips)
                        currentClips = currentClips + newClips
                        _isDirty.value = true
                        updateStateInternal()
                    }
                },
                onFailure = { e ->
                    _uiEvents.send(VideoEditingEvent.ShowToast(
                        UiText.StringResource(R.string.error_load_video, e.message ?: "Unknown error")
                    ))
                }
            )
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                // Handled by scope
            }
        }
    }

    public fun removeClip(index: Int) {
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
            
                if (index < selectedClipIndex) {
                    selectedClipIndex--
                } else if (selectedClipIndex >= currentClips.size) {
                    selectedClipIndex = currentClips.size - 1
                }
            
                loadClipDataInternal(selectedClipIndex)
            }
        }
    }

    public fun reorderClips(from: Int, to: Int) {
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

    public fun selectSegment(id: UUID?) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                selectedSegmentId = id
                updateStateInternal()
            }
        }
    }

    public fun splitSegmentAt(positionMs: Long) {
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

    public fun markSegmentDiscarded(id: UUID) {
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

    public fun updateSegmentBounds(id: UUID, start: Long, end: Long) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val currentClip = currentClips.getOrNull(selectedClipIndex) ?: return@withLock
                val updatedClip = clipController.updateSegmentBounds(currentClip, id, start, end)
                currentClips = currentClips.toMutableList().apply { this[selectedClipIndex] = updatedClip }
                updateStateInternal()
            }
        }
    }

    public fun commitSegmentBounds() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                historyManager.save(currentClips)
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    public fun undo() {
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

    public fun redo() {
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

    public fun reset() {
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
        selectedSegmentId = null
        historyManager.clear()
        _isDirty.value = false
        _waveformData.value = null
        _silencePreviewRanges.value = emptyList()
        _rawSilencePreviewRanges.value = null
        _sessionExists.value = false
        currentPlaybackSpeed = 1.0f
        isPitchCorrectionEnabled = false
        waveformController.clearInternal()
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
            isSnapshotInProgress = exportController.isSnapshotInProgress.value,
            silencePreviewRanges = _silencePreviewRanges.value,
            availableTracks = clip.availableTracks,
            playbackSpeed = currentPlaybackSpeed,
            isPitchCorrectionEnabled = isPitchCorrectionEnabled
        )
    }

    public fun previewSilenceSegments(
        threshold: Float,
        minSilenceMs: Long,
        paddingStartMs: Long,
        paddingEndMs: Long,
        minSegmentMs: Long
    ) {
        if (_waveformData.value == null) {
            viewModelScope.launch {
                _uiEvents.send(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_waveform_not_ready)))
            }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val clip = stateMutex.withLock { currentClips.getOrNull(selectedClipIndex) } ?: return@launch
            lastMinSegmentMs = minSegmentMs
            val params = WaveformController.SilenceDetectionParams(
                threshold, minSilenceMs, paddingStartMs, paddingEndMs, minSegmentMs, clip
            )
            waveformController.previewSilenceSegments(viewModelScope, params) {
                stateMutex.withLock { updateStateInternal() }
            }
        }
    }

    public fun previewVisualSegments(config: VisualDetectionConfig) {
        viewModelScope.launch(ioDispatcher) {
            val clip = stateMutex.withLock { currentClips.getOrNull(selectedClipIndex) } ?: return@launch
            lastMinSegmentMs = config.minSegmentDurationMs

            _uiState.value = VideoEditingUiState.Loading(
                UiText.StringResource(R.string.analyzing_video)
            )

            try {
                val ranges = useCases.visualSegmentDetector.detect(clip.uri, config)
                _silencePreviewRanges.value = ranges
                _rawSilencePreviewRanges.value = null // Visual detection doesn't have intermediate stages yet
            } catch (e: Exception) {
                _uiEvents.send(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_visual_detection_failed)))
                _silencePreviewRanges.value = emptyList()
            } finally {
                stateMutex.withLock { updateStateInternal() }
            }
        }
    }

    public fun clearSilencePreview() {
        waveformController.clearSilencePreview(viewModelScope) {
            stateMutex.withLock { updateStateInternal() }
        }
    }

    public fun applySilenceDetection() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val ranges = _silencePreviewRanges.value
                if (ranges.isEmpty()) return@withLock
                
                historyManager.save(currentClips)
                val clip = currentClips.getOrNull(selectedClipIndex) ?: return@withLock
                
                val updatedClip = useCases.silenceDetectionUseCase.applySilenceDetection(
                    clip, ranges, lastMinSegmentMs
                )
                
                currentClips = currentClips.toMutableList().apply {
                    this[selectedClipIndex] = updatedClip
                }
                
                _silencePreviewRanges.value = emptyList()
                _rawSilencePreviewRanges.value = null
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    public fun exportSegments(settings: ExportSettings) {
        if (!isExporting.compareAndSet(false, true)) return

        viewModelScope.launch(ioDispatcher) {
            try {
                val (clips, clipIndex) = stateMutex.withLock {
                    _uiState.value = VideoEditingUiState.Loading()
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
                            _uiEvents.send(
                                VideoEditingEvent.ShowToast(
                                    UiText.StringResource(
                                        R.string.export_success,
                                        result.count
                                    )
                                )
                            )
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
            } finally {
                isExporting.set(false)
            }
        }
    }

    public override fun onCleared() {
        super.onCleared()
        waveformController.cancelJobs()
    }

    public fun extractSnapshot(positionMs: Long) {
        viewModelScope.launch(ioDispatcher) {
            val clip = stateMutex.withLock { 
                currentClips.getOrNull(selectedClipIndex) 
            }
            if (clip == null) {
                stateMutex.withLock { updateStateInternal() }
                return@launch
            }
            
            // Note: exportController.isSnapshotInProgress is observed to update UI state
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

    public fun saveSession() {
        viewModelScope.launch(ioDispatcher) {
            val clips = stateMutex.withLock { currentClips }
            sessionController.saveSession(clips)
        }
    }

    public fun checkSessionExists(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val exists = sessionController.checkSessionExists(uri.toString())
            stateMutex.withLock {
                _sessionExists.value = exists
            }
        }
    }

    public fun restoreSession(uri: Uri) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e("VideoEditingViewModel", "Failed to restore session", e)
                _uiEvents.send(VideoEditingEvent.ShowToast(
                    UiText.StringResource(R.string.error_restore_failed, e.message ?: "")
                ))
                stateMutex.withLock { updateStateInternal() }
            }
        }
    }
}

public data class VideoEditingUseCases @Inject constructor(
    public val clipManagementUseCase: ClipManagementUseCase,
    public val exportUseCase: ExportUseCase,
    public val snapshotUseCase: ExtractSnapshotUseCase,
    public val silenceDetectionUseCase: SilenceDetectionUseCase,
    public val sessionUseCase: SessionUseCase,
    public val visualSegmentDetector: IVisualSegmentDetector
)

public data class ExportSettings(
    public val isLossless: Boolean,
    public val keepAudio: Boolean,
    public val keepVideo: Boolean,
    public val rotationOverride: Int?,
    public val mergeSegments: Boolean,
    public val selectedTracks: List<Int>? = null
)
