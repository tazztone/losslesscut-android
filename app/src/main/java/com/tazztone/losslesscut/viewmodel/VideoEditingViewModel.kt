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
import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.usecase.IVisualSegmentDetector
import com.tazztone.losslesscut.domain.model.VisualDetectionConfig
import com.tazztone.losslesscut.domain.model.VisualStrategy
import com.tazztone.losslesscut.domain.usecase.VisualSegmentFilter
import com.tazztone.losslesscut.domain.usecase.SessionUseCase
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import com.tazztone.losslesscut.domain.usecase.SegmentDetectorUseCase
import com.tazztone.losslesscut.domain.usecase.VisualDetectionListener
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

    private val _detectionPreviewRanges = MutableStateFlow<List<LongRange>>(emptyList())
    public val detectionPreviewRanges: StateFlow<List<LongRange>> = _detectionPreviewRanges.asStateFlow()

    private val _visualDetectionProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    public val visualDetectionProgress: StateFlow<Pair<Int, Int>?> = _visualDetectionProgress.asStateFlow()

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

    public val editingSession: com.tazztone.losslesscut.domain.session.EditingSession =
        com.tazztone.losslesscut.domain.session.EditingSession(historyLimit = 30)

    private val currentClips get() = editingSession.currentSnapshot.clips
    private val selectedClipIndex get() = editingSession.currentSnapshot.selectedClipIndex
    private val selectedSegmentId get() = editingSession.currentSnapshot.selectedSegmentId
    private var currentKeyframes: List<Long> = emptyList()
    
    private val sessionController = SessionController(useCases.sessionUseCase, ioDispatcher)
    private val exportController = ExportController(
        useCases.exportUseCase, useCases.snapshotUseCase, preferences
    )
    private val waveformController = WaveformController(
        repository, useCases.silenceDetectionUseCase, ioDispatcher
    )
    public val waveformMaxAmplitude: StateFlow<Float> = waveformController.maxAmplitude
    private val stateMutex = Mutex()
    private val isExporting = AtomicBoolean(false)
    
    private val keyframeCache = ConcurrentHashMap<String, List<Long>>()
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
                    _detectionPreviewRanges.value = ranges
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
                    val result = useCases.clipManagementUseCase.createClips(uris.map { it.toString() })
                    result.fold(
                        onSuccess = { clips ->
                            editingSession.setClips(clips, 0)
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
        viewModelScope.launch(ioDispatcher) {
            val autoExtract = preferences.autoExtractWaveformsFlow.firstOrNull() ?: true
            if (autoExtract) {
                waveformController.extractWaveform(viewModelScope, clip)
            }
        }
    }

    public fun selectClip(index: Int) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                if (index == selectedClipIndex || index !in currentClips.indices) return@withLock
                editingSession.selectClip(index)
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
                        val updated = currentClips + newClips
                        editingSession.updateClipsList(updated, selectedClipIndex)
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
                val newList = currentClips.toMutableList()
                newList.removeAt(index)
                val newIndex = when {
                    index < selectedClipIndex -> selectedClipIndex - 1
                    selectedClipIndex >= newList.size -> newList.size - 1
                    else -> selectedClipIndex
                }
                editingSession.updateClipsList(newList, newIndex)
                _isDirty.value = true
                loadClipDataInternal(selectedClipIndex)
            }
        }
    }

    public fun reorderClips(from: Int, to: Int) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val success = editingSession.reorderClips(from, to)
                if (success) {
                    _isDirty.value = true
                    updateStateInternal()
                }
            }
        }
    }

    public fun selectSegment(id: UUID?) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                editingSession.selectSegment(id)
                updateStateInternal()
            }
        }
    }

    public fun splitSegmentAt(positionMs: Long) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val success = editingSession.splitSegmentAt(positionMs)
                if (!success) {
                    _uiEvents.send(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_segment_too_small_split))) 
                    return@withLock
                }
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    public fun markSegmentDiscarded(id: UUID) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val success = editingSession.toggleSegmentDiscard(id)
                if (!success) {
                    _uiEvents.send(
                        VideoEditingEvent.ShowToast(
                            UiText.StringResource(R.string.error_cannot_discard_last)
                        )
                    )
                    return@withLock
                }
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    public fun updateSegmentBounds(
        id: UUID,
        start: Long,
        end: Long,
        coalesceHistory: Boolean = false
    ) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                editingSession.updateSegmentBounds(id, start, end, coalesceHistory = coalesceHistory)
                updateStateInternal()
            }
        }
    }

    public fun commitSegmentBounds() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                editingSession.finishSegmentBoundsEdit()
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    public fun undo() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                if (editingSession.undo()) {
                    _isDirty.value = true
                    updateStateInternal()
                    loadClipDataInternal(selectedClipIndex)
                }
            }
        }
    }

    public fun redo() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                if (editingSession.redo()) {
                    _isDirty.value = true
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
        editingSession.setClips(emptyList(), 0)
        _isDirty.value = false
        _waveformData.value = null
        _detectionPreviewRanges.value = emptyList()
        _rawSilencePreviewRanges.value = null
        _visualDetectionProgress.value = null
        useCases.segmentDetector.cancelVisual()
        _sessionExists.value = false
        currentPlaybackSpeed = 1.0f
        isPitchCorrectionEnabled = false
        waveformController.clearInternal()
        useCases.segmentDetector.clearCache()
    }

    private fun updateStateInternal() {
        val snapshot = editingSession.currentSnapshot
        _uiState.value = VideoEditingStateMapper.mapToState(
            MapStateInput(
                currentClips = snapshot.clips,
                selectedClipIndex = snapshot.selectedClipIndex,
                currentKeyframes = currentKeyframes,
                selectedSegmentId = snapshot.selectedSegmentId,
                canUndo = snapshot.canUndo,
                canRedo = snapshot.canRedo,
                isSnapshotInProgress = exportController.isSnapshotInProgress.value,
                detectionPreviewRanges = _detectionPreviewRanges.value,
                playbackSpeed = currentPlaybackSpeed,
                isPitchCorrectionEnabled = isPitchCorrectionEnabled,
                currentState = _uiState.value
            )
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
            val params = WaveformController.SilenceDetectionParams(
                threshold, minSilenceMs, paddingStartMs, paddingEndMs, minSegmentMs, clip
            )
            waveformController.previewSilenceSegments(viewModelScope, params) {
                viewModelScope.launch {
                    stateMutex.withLock { updateStateInternal() }
                }
            }
        }
    }

    public fun previewVisualSegments(config: VisualDetectionConfig) {
        detectVisualSegments(config, reportProgress = true)
    }

    public fun filterVisualSegments(config: VisualDetectionConfig) {
        detectVisualSegments(config, reportProgress = false)
    }

    private fun detectVisualSegments(config: VisualDetectionConfig, reportProgress: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val clip = stateMutex.withLock { currentClips.getOrNull(selectedClipIndex) } ?: return@launch
            if (reportProgress) {
                _detectionPreviewRanges.value = emptyList()
            }

            useCases.segmentDetector.detectVisual(
                scope = viewModelScope,
                uri = clip.uri,
                config = config,
                listener = object : VisualDetectionListener {
                    override fun onProgress(progress: Pair<Int, Int>?) {
                        if (reportProgress) publishVisualProgress(progress)
                    }

                    override fun onComplete(ranges: List<LongRange>) {
                        publishVisualRanges(ranges)
                    }

                    override fun onError(error: Throwable) {
                        Log.e("VideoEditingViewModel", "Unexpected error in visual analysis: ${error.message}", error)
                        publishVisualError()
                    }
                }
            )
        }
    }

    private fun publishVisualProgress(progress: Pair<Int, Int>?) {
        viewModelScope.launch {
            stateMutex.withLock {
                _visualDetectionProgress.value = progress
                updateStateInternal()
            }
        }
    }

    private fun publishVisualRanges(ranges: List<LongRange>) {
        viewModelScope.launch {
            stateMutex.withLock {
                _detectionPreviewRanges.value = ranges
                _rawSilencePreviewRanges.value = null
                updateStateInternal()
            }
        }
    }

    private fun publishVisualError() {
        viewModelScope.launch {
            _uiEvents.send(
                VideoEditingEvent.ShowToast(
                    UiText.StringResource(R.string.error_visual_detection_failed)
                )
            )
            stateMutex.withLock {
                _detectionPreviewRanges.value = emptyList()
                updateStateInternal()
            }
        }
    }

    public fun cancelVisualDetection() {
        useCases.segmentDetector.cancelVisual()
        _visualDetectionProgress.value = null
        viewModelScope.launch { updateStateInternal() }
    }

    public fun hasCachedAnalysis(): Boolean {
        return useCases.segmentDetector.hasCachedAnalysis()
    }



    public fun clearSilencePreview() {
        waveformController.clearSilencePreview(viewModelScope) {
            stateMutex.withLock { updateStateInternal() }
        }
    }

    public fun applyDetection(
        mode: SilenceDetectionUseCase.DetectionMode = SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES,
        minKeepSegmentDurationMs: Long = 10L
    ) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val ranges = _detectionPreviewRanges.value
                if (ranges.isEmpty()) return@withLock
                
                val clip = currentClips.getOrNull(selectedClipIndex) ?: return@withLock
                
                val updatedClip = useCases.silenceDetectionUseCase.applyDetectionRanges(
                    clip, ranges, minKeepSegmentDurationMs, mode
                )
                
                editingSession.applySegments(updatedClip.segments)
                
                _detectionPreviewRanges.value = emptyList()
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
                    editingSession.setClips(validClips, 0)
                    editingSession.markDirty()
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
    public val visualSegmentDetector: IVisualSegmentDetector,
    public val segmentDetector: SegmentDetectorUseCase
)

public data class ExportSettings(
    public val isLossless: Boolean,
    public val keepAudio: Boolean,
    public val keepVideo: Boolean,
    public val rotationOverride: Int?,
    public val mergeSegments: Boolean,
    public val selectedTracks: List<Int>? = null
)
