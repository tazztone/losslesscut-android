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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
@Suppress("LargeClass")
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

    private val _detectionPreviewRanges = MutableStateFlow<List<LongRange>>(emptyList())
    val detectionPreviewRanges: StateFlow<List<LongRange>> = _detectionPreviewRanges.asStateFlow()

    private val _visualDetectionProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val visualDetectionProgress: StateFlow<Pair<Int, Int>?> = _visualDetectionProgress.asStateFlow()
    val defaultVisualFrameStepFlow: Flow<Int> = preferences.defaultVisualFrameStepFlow

    private val _rawSilencePreviewRanges = MutableStateFlow<SilenceDetectionUseCase.DetectionResult?>(null)
    val rawSilencePreviewRanges: StateFlow<SilenceDetectionUseCase.DetectionResult?> =
        _rawSilencePreviewRanges.asStateFlow()

    private var hintsDismissed = false

    fun onUserInteraction() {
        if (hintsDismissed) return
        hintsDismissed = true
        viewModelScope.launch {
            _uiEvents.send(VideoEditingEvent.DismissHints)
        }
    }

    private val _sessionExists = MutableStateFlow(false)
    val sessionExists: StateFlow<Boolean> = _sessionExists.asStateFlow()

    private var currentPlaybackSpeed = 1.0f
    private var isPitchCorrectionEnabled = false

    private var editingSession: com.tazztone.losslesscut.domain.session.EditingSession =
        com.tazztone.losslesscut.domain.session.EditingSession()

    private val currentClips get() = editingSession.currentSnapshot.clips
    private val selectedClipIndex get() = editingSession.currentSnapshot.selectedClipIndex
    private val selectedSegmentId get() = editingSession.currentSnapshot.selectedSegmentId
    private var currentKeyframes: List<Long> = emptyList()
    
    private val exportController = ExportController(
        useCases.exportUseCase, useCases.snapshotUseCase, preferences
    )
    private val waveformController = WaveformController(
        repository, useCases.silenceDetectionUseCase, ioDispatcher
    )
    val waveformMaxAmplitude: StateFlow<Float> = waveformController.maxAmplitude
    private val stateMutex = Mutex()
    private val isExporting = AtomicBoolean(false)
    private val visualRequestGeneration = AtomicLong(0L)
    @Volatile
    private var visualRequestClipId: UUID? = null
    
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
                try {
                    val undoLimit = preferences.undoLimitFlow.first()
                    editingSession = com.tazztone.losslesscut.domain.session.EditingSession(undoLimit)
                    resetInternal()
                    preferences.applyAnalysisCachePolicy()
                    _uiState.value = VideoEditingUiState.Loading()
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

    fun selectClip(index: Int) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                if (index == selectedClipIndex || index !in currentClips.indices) return@withLock
                invalidateVisualDetection()
                editingSession.selectClip(index)
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
                if (index !in currentClips.indices) return@withLock
                invalidateVisualDetection()
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

    fun onOriginalClipsDeleted(deletedUris: List<Uri>) {
        if (deletedUris.isEmpty()) return
        val deletedUriStrings = deletedUris.map { it.toString() }.toSet()
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val remainingClips = currentClips.filterNot { it.uri in deletedUriStrings }
                if (remainingClips.isEmpty()) {
                    invalidateVisualDetection()
                    editingSession = com.tazztone.losslesscut.domain.session.EditingSession()
                    _uiState.value = VideoEditingUiState.Initial
                    _isDirty.value = false
                    _waveformData.value = null
                } else {
                    invalidateVisualDetection()
                    val newIndex = if (selectedClipIndex >= remainingClips.size) remainingClips.size - 1 else selectedClipIndex
                    editingSession.updateClipsList(remainingClips, newIndex)
                    _isDirty.value = true
                    loadClipDataInternal(newIndex)
                }
            }
        }
    }


    fun reorderClips(from: Int, to: Int) {
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

    fun selectSegment(id: UUID?) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                editingSession.selectSegment(id)
                updateStateInternal()
            }
        }
    }

    fun splitSegmentAt(positionMs: Long) {
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

    fun markSegmentDiscarded(id: UUID) {
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

    fun setInPoint(positionMs: Long, isLosslessMode: Boolean = true) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val clip = editingSession.currentSnapshot.selectedClip ?: return@withLock
                val effectivePos = if (isLosslessMode && currentKeyframes.isNotEmpty()) {
                    currentKeyframes.minByOrNull { kotlin.math.abs(it - positionMs) } ?: positionMs
                } else {
                    positionMs
                }

                val containingSeg = clip.segments.find { effectivePos >= it.startMs && effectivePos < it.endMs }
                if (containingSeg != null) {
                    editingSession.selectSegment(containingSeg.id)
                    editingSession.updateSegmentBounds(containingSeg.id, effectivePos, containingSeg.endMs)
                    editingSession.finishSegmentBoundsEdit()
                    _isDirty.value = true
                    updateStateInternal()
                    _uiEvents.send(VideoEditingEvent.SeekToPosition(effectivePos))
                } else {
                    val nextSeg = clip.segments.find { it.startMs > effectivePos }
                    if (nextSeg != null) {
                        editingSession.selectSegment(nextSeg.id)
                        editingSession.updateSegmentBounds(nextSeg.id, effectivePos, nextSeg.endMs)
                        editingSession.finishSegmentBoundsEdit()
                        _isDirty.value = true
                        updateStateInternal()
                        _uiEvents.send(VideoEditingEvent.SeekToPosition(effectivePos))
                    } else {
                        val futureKeyframes = currentKeyframes.filter { it > effectivePos }
                        val targetEndMs = when {
                            futureKeyframes.size >= NEW_SEGMENT_KEYFRAME_COUNT -> futureKeyframes[NEW_SEGMENT_KEYFRAME_INDEX]
                            futureKeyframes.isNotEmpty() -> futureKeyframes.last()
                            else -> effectivePos + DEFAULT_NEW_SEGMENT_DURATION_MS
                        }
                        val endMs = targetEndMs.coerceIn(
                            effectivePos + com.tazztone.losslesscut.domain.session.EditingSession.MIN_SEGMENT_DURATION_MS,
                            clip.durationMs
                        )
                        val newSegId = editingSession.addSegment(effectivePos, endMs)
                        if (newSegId != null) {
                            _isDirty.value = true
                            updateStateInternal()
                            _uiEvents.send(VideoEditingEvent.SeekToPosition(effectivePos))
                        } else {
                            _uiEvents.send(
                                VideoEditingEvent.ShowToast(
                                    UiText.StringResource(R.string.error_segment_too_small_split)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun setOutPoint(positionMs: Long, isLosslessMode: Boolean = true) {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val clip = editingSession.currentSnapshot.selectedClip ?: return@withLock
                val effectivePos = if (isLosslessMode && currentKeyframes.isNotEmpty()) {
                    currentKeyframes.minByOrNull { kotlin.math.abs(it - positionMs) } ?: positionMs
                } else {
                    positionMs
                }

                val containingSeg = clip.segments.find { effectivePos > it.startMs && effectivePos <= it.endMs }
                if (containingSeg != null) {
                    editingSession.selectSegment(containingSeg.id)
                    editingSession.updateSegmentBounds(containingSeg.id, containingSeg.startMs, effectivePos)
                    editingSession.finishSegmentBoundsEdit()
                    _isDirty.value = true
                    updateStateInternal()
                    _uiEvents.send(VideoEditingEvent.SeekToPosition(effectivePos))
                } else {
                    val prevSeg = clip.segments.filter { it.endMs < effectivePos }.maxByOrNull { it.endMs }
                    if (prevSeg != null) {
                        editingSession.selectSegment(prevSeg.id)
                        editingSession.updateSegmentBounds(prevSeg.id, prevSeg.startMs, effectivePos)
                        editingSession.finishSegmentBoundsEdit()
                        _isDirty.value = true
                        updateStateInternal()
                        _uiEvents.send(VideoEditingEvent.SeekToPosition(effectivePos))
                    } else {
                        val pastKeyframes = currentKeyframes.filter { it < effectivePos }
                        val targetStartMs = when {
                            pastKeyframes.size >= NEW_SEGMENT_KEYFRAME_COUNT ->
                                pastKeyframes[pastKeyframes.size - NEW_SEGMENT_KEYFRAME_COUNT]
                            pastKeyframes.isNotEmpty() -> pastKeyframes.first()
                            else -> effectivePos - DEFAULT_NEW_SEGMENT_DURATION_MS
                        }
                        val minDur = com.tazztone.losslesscut.domain.session.EditingSession.MIN_SEGMENT_DURATION_MS
                        val startMs = targetStartMs.coerceIn(
                            0L,
                            (effectivePos - minDur).coerceAtLeast(0L)
                        )
                        val newSegId = editingSession.addSegment(startMs, effectivePos)
                        if (newSegId != null) {
                            _isDirty.value = true
                            updateStateInternal()
                            _uiEvents.send(VideoEditingEvent.SeekToPosition(effectivePos))
                        } else {
                            _uiEvents.send(
                                VideoEditingEvent.ShowToast(
                                    UiText.StringResource(R.string.error_segment_too_small_split)
                                )
                            )
                        }
                    }
                }
            }
        }
    }



    fun updateSegmentBounds(
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

    fun commitSegmentBounds() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                editingSession.finishSegmentBoundsEdit()
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    fun undo() {
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

    fun redo() {
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

    fun resetClipSegments() {
        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                invalidateVisualDetection()
                if (editingSession.resetClipSegments()) {
                    _isDirty.value = true
                    updateStateInternal()
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

    private fun invalidateVisualDetection() {
        visualRequestGeneration.incrementAndGet()
        visualRequestClipId = null
        useCases.segmentDetector.cancelVisual()
        _detectionPreviewRanges.value = emptyList()
        _visualDetectionProgress.value = null
    }

    private fun isCurrentVisualRequest(requestId: Long, clipId: UUID): Boolean {
        return visualRequestGeneration.get() == requestId && visualRequestClipId == clipId &&
            currentClips.getOrNull(selectedClipIndex)?.id == clipId
    }

    private fun resetInternal() {
        invalidateVisualDetection()
        _uiState.value = VideoEditingUiState.Initial
        editingSession.setClips(emptyList(), 0)
        _isDirty.value = false
        _waveformData.value = null
        _rawSilencePreviewRanges.value = null
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

    fun previewSilenceSegments(
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

    fun previewVisualSegments(config: VisualDetectionConfig) {
        detectVisualSegments(config, reportProgress = true, allowDecode = true)
    }

    fun filterVisualSegments(config: VisualDetectionConfig) {
        detectVisualSegments(config, reportProgress = false, allowDecode = false)
    }

    private fun detectVisualSegments(
        config: VisualDetectionConfig,
        reportProgress: Boolean,
        allowDecode: Boolean
    ) {
        val requestId = visualRequestGeneration.incrementAndGet()
        val targetClipId = currentClips.getOrNull(selectedClipIndex)?.id
        visualRequestClipId = targetClipId
        viewModelScope.launch(ioDispatcher) {
            val clip = stateMutex.withLock {
                currentClips.getOrNull(selectedClipIndex)?.takeIf {
                    it.id == targetClipId && visualRequestGeneration.get() == requestId
                }
            } ?: return@launch
            if (reportProgress) {
                _detectionPreviewRanges.value = emptyList()
                _visualDetectionProgress.value = null
            }

            useCases.segmentDetector.detectVisual(
                scope = viewModelScope,
                uri = clip.uri,
                config = config,
                listener = object : VisualDetectionListener {
                    override fun onProgress(progress: Pair<Int, Int>?) {
                        if (reportProgress) publishVisualProgress(requestId, clip.id, progress)
                    }

                    override fun onComplete(ranges: List<LongRange>) {
                        publishVisualRanges(requestId, clip.id, ranges)
                    }

                    override fun onError(error: Throwable) {
                        Log.e("VideoEditingViewModel", "Unexpected error in visual analysis: ${error.message}", error)
                        publishVisualError(requestId, clip.id)
                    }
                },
                clip = clip,
                allowDecode = allowDecode
            )
        }
    }

    private fun publishVisualProgress(requestId: Long, clipId: UUID, progress: Pair<Int, Int>?) {
        viewModelScope.launch {
            stateMutex.withLock {
                if (!isCurrentVisualRequest(requestId, clipId)) return@withLock
                _visualDetectionProgress.value = progress
                updateStateInternal()
            }
        }
    }

    private fun publishVisualRanges(requestId: Long, clipId: UUID, ranges: List<LongRange>) {
        viewModelScope.launch {
            stateMutex.withLock {
                if (!isCurrentVisualRequest(requestId, clipId)) return@withLock
                _detectionPreviewRanges.value = ranges
                _rawSilencePreviewRanges.value = null
                _visualDetectionProgress.value = null
                updateStateInternal()
            }
        }
    }

    private fun publishVisualError(requestId: Long, clipId: UUID) {
        viewModelScope.launch {
            stateMutex.withLock {
                if (!isCurrentVisualRequest(requestId, clipId)) return@launch
                _detectionPreviewRanges.value = emptyList()
                _visualDetectionProgress.value = null
                updateStateInternal()
            }
            _uiEvents.send(
                VideoEditingEvent.ShowToast(
                    UiText.StringResource(R.string.error_visual_detection_failed)
                )
            )
        }
    }

    fun cancelVisualDetection() {
        invalidateVisualDetection()
        viewModelScope.launch { updateStateInternal() }
    }

    fun hasCachedAnalysis(): Boolean {
        return useCases.segmentDetector.hasCachedAnalysis()
    }



    fun clearSilencePreview() {
        waveformController.clearSilencePreview(viewModelScope) {
            viewModelScope.launch {
                stateMutex.withLock { updateStateInternal() }
            }
        }
    }

    fun applyDetection(
        mode: SilenceDetectionUseCase.DetectionMode = SilenceDetectionUseCase.DetectionMode.DISCARD_RANGES,
        minKeepSegmentDurationMs: Long = 10L
    ) {
        val ranges = _detectionPreviewRanges.value.toList()
        val targetClipId = currentClips.getOrNull(selectedClipIndex)?.id
        if (ranges.isEmpty() || targetClipId == null) return

        viewModelScope.launch(ioDispatcher) {
            stateMutex.withLock {
                val clip = currentClips.getOrNull(selectedClipIndex)?.takeIf { it.id == targetClipId }
                    ?: return@withLock
                
                val updatedClip = useCases.silenceDetectionUseCase.applyDetectionRanges(
                    clip, ranges, minKeepSegmentDurationMs, mode
                )
                
                editingSession.applySegments(updatedClip.segments)
                
                visualRequestGeneration.incrementAndGet()
                visualRequestClipId = null
                _detectionPreviewRanges.value = emptyList()
                _rawSilencePreviewRanges.value = null
                _isDirty.value = true
                updateStateInternal()
            }
        }
    }

    fun exportSegments(settings: ExportSettings) {
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
                            _uiEvents.send(
                                VideoEditingEvent.ExportComplete(
                                    success = true,
                                    count = result.count,
                                    deleteOriginalAfterExport = result.deleteOriginalAfterExport,
                                    sourceUris = result.sourceUris
                                )
                            )
                            _isDirty.value = false
                            clips.firstOrNull()?.uri?.let { useCases.sessionUseCase.deleteSession(it) }
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

    override fun onCleared() {
        super.onCleared()
        waveformController.cancelJobs()
    }

    fun extractSnapshot(positionMs: Long) {
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

    fun saveSession() {
        viewModelScope.launch(ioDispatcher) {
            val clips = stateMutex.withLock { currentClips }
            useCases.sessionUseCase.saveSession(clips)
        }
    }

    fun saveSessionIfDirty() {
        if (_isDirty.value) saveSession()
    }

    fun discardSession(onComplete: () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            val uri = stateMutex.withLock { currentClips.firstOrNull()?.uri }
            if (uri != null) useCases.sessionUseCase.deleteSession(uri)
            withContext(Dispatchers.Main.immediate) {
                clearDirty()
                onComplete()
            }
        }
    }

    fun checkSessionExists(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val exists = useCases.sessionUseCase.hasSavedSession(uri.toString())
            stateMutex.withLock {
                _sessionExists.value = exists
            }
        }
    }

    fun restoreSession(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _uiState.value = VideoEditingUiState.Loading()
                val validClips = useCases.sessionUseCase.restoreSession(uri.toString())

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

    private companion object {
        private const val NEW_SEGMENT_KEYFRAME_COUNT = 3
        private const val NEW_SEGMENT_KEYFRAME_INDEX = 2
        private const val DEFAULT_NEW_SEGMENT_DURATION_MS = 3000L
    }

}

data class VideoEditingUseCases @Inject constructor(
    val clipManagementUseCase: ClipManagementUseCase,
    val exportUseCase: ExportUseCase,
    val snapshotUseCase: ExtractSnapshotUseCase,
    val silenceDetectionUseCase: SilenceDetectionUseCase,
    val sessionUseCase: SessionUseCase,
    val segmentDetector: SegmentDetectorUseCase
)

data class ExportSettings(
    val keepAudio: Boolean,
    val keepVideo: Boolean,
    val rotationOverride: Int?,
    val mergeSegments: Boolean,
    val selectedTracks: List<Int>? = null,
    val deleteOriginalAfterExport: Boolean = false
)
