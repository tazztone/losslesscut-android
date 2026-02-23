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
import com.tazztone.losslesscut.di.IoDispatcher
import com.tazztone.losslesscut.utils.TimeUtils
import com.tazztone.losslesscut.utils.DetectionUtils
import com.tazztone.losslesscut.utils.UiText
import com.tazztone.losslesscut.data.VideoEditingRepository
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
                // Initialize with multiple clips
                val clips = uris.map { uri ->
                    repository.createClipFromUri(uri).getOrThrow()
                }
                
                currentClips = clips
                selectedClipIndex = 0
                loadClipData(selectedClipIndex)
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
            // Get keyframes
            val cacheKey = clip.uri.toString()
            val kfs = keyframeCache[cacheKey] ?: repository.getKeyframes(clip.uri)
            keyframeCache[cacheKey] = kfs
            currentKeyframes = kfs
            
            // Extract waveform
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
            try {
                val newClips = uris.map { uri ->
                    repository.createClipFromUri(uri).getOrThrow()
                }
                saveToHistory()
                currentClips = currentClips + newClips
                updateState()
            } catch (e: Exception) {
                _uiEvents.emit(VideoEditingEvent.ShowToast(
                    UiText.StringResource(R.string.error_load_video, e.message ?: "Unknown error")
                ))
            }
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
        val list = currentClips.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        
        // Update selectedIndex
        if (selectedClipIndex == from) {
            selectedClipIndex = to
        } else if (from < selectedClipIndex && to >= selectedClipIndex) {
            selectedClipIndex--
        } else if (from > selectedClipIndex && to <= selectedClipIndex) {
            selectedClipIndex++
        }
        
        currentClips = list
        _isDirty.value = true
        updateState()
    }

    fun selectSegment(id: UUID?) {
        selectedSegmentId = id
        updateState()
    }

    fun splitSegmentAt(positionMs: Long) {
        val currentClip = currentClips[selectedClipIndex]
        val segment = currentClip.segments.find { positionMs in it.startMs..it.endMs } ?: return
        
        if (positionMs - segment.startMs < MIN_SEGMENT_DURATION_MS || segment.endMs - positionMs < MIN_SEGMENT_DURATION_MS) {
            viewModelScope.launch { 
                _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_segment_too_small_split))) 
            }
            return
        }

        saveToHistory()
        val newSegments = currentClip.segments.toMutableList()
        val index = newSegments.indexOf(segment)
        newSegments.removeAt(index)
        newSegments.add(index, segment.copy(endMs = positionMs))
        newSegments.add(index + 1, segment.copy(id = UUID.randomUUID(), startMs = positionMs))
        
        val updatedClip = currentClip.copy(segments = newSegments)
        val updatedClips = currentClips.toMutableList()
        updatedClips[selectedClipIndex] = updatedClip
        currentClips = updatedClips
        _isDirty.value = true
        updateState()
    }

    fun markSegmentDiscarded(id: UUID) {
        val currentClip = currentClips.getOrNull(selectedClipIndex) ?: return
        val segment = currentClip.segments.find { it.id == id } ?: return
        
        // Prevent discarding the last KEEP segment
        if (segment.action == SegmentAction.KEEP && 
            currentClip.segments.count { it.action == SegmentAction.KEEP } <= 1) {
            viewModelScope.launch {
                _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_cannot_discard_last)))
            }
            return
        }

        saveToHistory()
        val newAction = if (segment.action == SegmentAction.KEEP) SegmentAction.DISCARD else SegmentAction.KEEP
        val newSegments = currentClip.segments.map { 
            if (it.id == id) it.copy(action = newAction) else it 
        }
        
        val updatedClip = currentClip.copy(segments = newSegments)
        val updatedClips = currentClips.toMutableList()
        updatedClips[selectedClipIndex] = updatedClip
        currentClips = updatedClips
        _isDirty.value = true
        updateState()
    }

    fun updateSegmentBounds(id: UUID, start: Long, end: Long) {
        val currentClip = currentClips[selectedClipIndex]
        val newSegments = currentClip.segments.map { 
            if (it.id == id) it.copy(startMs = start, endMs = end) else it 
        }
        val updatedClip = currentClip.copy(segments = newSegments)
        val updatedClips = currentClips.toMutableList()
        updatedClips[selectedClipIndex] = updatedClip
        currentClips = updatedClips
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
            val ranges = withContext(ioDispatcher) {
                DetectionUtils.findSilence(
                    waveform = waveform,
                    threshold = threshold,
                    minSilenceMs = minSilenceMs,
                    totalDurationMs = clip.durationMs,
                    paddingMs = paddingMs,
                    minSegmentMs = minSegmentMs
                )
            }
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
        
        // Stabilized splitting: Perform a single-pass split of all existing segments
        // against all silence ranges to avoid the "fragmentation" issue.
        val newSegments = mutableListOf<TrimSegment>()
        
        clip.segments.forEach { seg ->
            if (seg.action == SegmentAction.DISCARD) {
                newSegments.add(seg)
                return@forEach
            }
            val segRanges = mutableListOf<LongRange>()
            segRanges.add(seg.startMs..seg.endMs)
            
            // Intersection of this segment with all silence ranges
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
                        // The overlap is the SILENCE part, keep track of it as a DISCARD segment later
                        // For now we just carve it out of the 'curr' range
                        if (overlapEnd < curr.last - MIN_SEGMENT_DURATION_MS) {
                            added.add(overlapEnd..curr.last)
                        }
                    }
                }
                segRanges.addAll(added)
                segRanges.sortBy { it.first }
            }
            
            // Now reconstruct segments: the gaps between our remaining segRanges are the DISCARD portions
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
            try {
                _uiState.value = VideoEditingUiState.Loading()
                
                withContext(ioDispatcher) {
                    if (mergeSegments) {
                        val segments = currentClips.flatMap { clip ->
                            clip.segments.filter { it.action == SegmentAction.KEEP }
                                .map { seg -> clip.copy(segments = listOf(seg)) }
                        }
                        
                        if (segments.isEmpty()) {
                            _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_no_tracks_found)))
                            updateState()
                            return@withContext
                        }

                        _uiState.value = VideoEditingUiState.Loading(progress = 0, message = UiText.StringResource(R.string.export_merging))
                        ensureActive()

                        val firstClip = currentClips[selectedClipIndex]
                        val extension = if (!keepVideo) "m4a" else "mp4"
                        val baseNameOnly = firstClip.fileName.substringBeforeLast(".")
                        val outputUri = repository.createMediaOutputUri("${baseNameOnly}_merged.$extension", !keepVideo)
                        
                        if (outputUri == null) {
                            _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_create_file)))
                            updateState()
                            return@withContext
                        }

                        val result = repository.executeLosslessMerge(
                            outputUri, segments, keepAudio, keepVideo, rotationOverride, selectedTracks
                        )
                        
                        result.fold(
                            onSuccess = { 
                                _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.export_success, 1)))
                                _uiEvents.emit(VideoEditingEvent.ExportComplete(true, 1))
                            },
                            onFailure = { 
                                _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_export_failed, it.message ?: "")))
                                _uiEvents.emit(VideoEditingEvent.ExportComplete(false))
                            }
                        )
                    } else {
                        val selectedClip = currentClips[selectedClipIndex]
                        val segments = selectedClip.segments.filter { it.action == SegmentAction.KEEP }
                        
                        if (segments.isEmpty()) {
                            _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.error_no_tracks_found)))
                            _uiEvents.emit(VideoEditingEvent.ExportComplete(false))
                            updateState()
                            return@withContext
                        }

                        var successCount = 0
                        val errors = mutableListOf<String>()

                        for ((index, segment) in segments.withIndex()) {
                            ensureActive()
                            val progress = ((index.toFloat() / segments.size) * 100).toInt()
                            _uiState.value = VideoEditingUiState.Loading(
                                progress = progress,
                                message = UiText.StringResource(R.string.export_saving_segment, index + 1, segments.size)
                            )

                            val extension = if (!keepVideo) "m4a" else "mp4"
                            val timeSuffix = "_${TimeUtils.formatFilenameDuration(segment.startMs)}-${TimeUtils.formatFilenameDuration(segment.endMs)}"
                            val baseNameOnly = selectedClip.fileName.substringBeforeLast(".")
                            val outputUri = repository.createMediaOutputUri("$baseNameOnly$timeSuffix.$extension", !keepVideo)

                            if (outputUri == null) {
                                errors.add("Failed to create output file")
                                continue
                            }

                            val result = repository.executeLosslessCut(selectedClip.uri, outputUri, segment.startMs, segment.endMs, keepAudio, keepVideo, rotationOverride, selectedTracks)
                            result.fold(
                                onSuccess = { successCount++ },
                                onFailure = { errors.add("Segment $index failed: ${it.message}") }
                            )
                        }

                        if (errors.isEmpty() && successCount > 0) {
                            _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.StringResource(R.string.export_success, successCount)))
                            _uiEvents.emit(VideoEditingEvent.ExportComplete(true, successCount))
                            _isDirty.value = false
                        } else if (errors.isNotEmpty()) {
                            _uiEvents.emit(VideoEditingEvent.ShowToast(UiText.DynamicString(errors.joinToString())))
                            _uiEvents.emit(VideoEditingEvent.ExportComplete(successCount > 0, successCount))
                        }
                    }
                }
                updateState()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = VideoEditingUiState.Error(UiText.DynamicString(e.message ?: "Unknown error"))
                _uiEvents.emit(VideoEditingEvent.ExportComplete(false))
            }
        }
    }

    fun extractSnapshot(positionMs: Long) {
        viewModelScope.launch {
            if (!isSnapshotInProgress.compareAndSet(false, true)) return@launch
            updateState()
            
            try {
                val clip = currentClips[selectedClipIndex]
                val bitmap: Bitmap? = repository.getFrameAt(clip.uri, positionMs)

                if (bitmap != null) {
                    val stableBitmap: Bitmap = bitmap
                    
                    val format = preferences.snapshotFormatFlow.first()
                    val quality = preferences.jpgQualityFlow.first()
                    
                    val ext = if (format == "PNG") "png" else "jpg"
                    val compressFormat = if (format == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    
                    val fileName = "snapshot_${System.currentTimeMillis()}.$ext"
                    val outputUri = repository.createImageOutputUri(fileName)
                    if (outputUri != null) {
                        val success = repository.writeSnapshot(stableBitmap, outputUri, format, quality)
                        if (success) {
                            repository.finalizeImage(outputUri)
                            _uiEvents.emit(VideoEditingEvent.ShowToast(
                                UiText.StringResource(R.string.snapshot_saved, fileName)
                            ))
                        } else {
                            _uiEvents.emit(VideoEditingEvent.ShowToast(
                                UiText.StringResource(R.string.snapshot_failed)
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoEditingViewModel", "Snapshot failed", e)
                _uiEvents.emit(VideoEditingEvent.ShowToast(
                    UiText.StringResource(R.string.snapshot_failed)
                ))
            } finally {
                isSnapshotInProgress.set(false)
                updateState()
            }
        }
    }

    fun saveSession() {
        viewModelScope.launch {
            repository.saveSession(currentClips)
        }
    }

    suspend fun hasSavedSession(uri: Uri): Boolean {
        return repository.hasSavedSession(uri)
    }

    fun restoreSession(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = VideoEditingUiState.Loading()
                val validClips = repository.restoreSession(uri)

                if (validClips.isNullOrEmpty()) {
                    _uiEvents.emit(VideoEditingEvent.ShowToast(
                        UiText.StringResource(R.string.error_restore_failed_files_missing)
                    ))
                    updateState() // Return to success state if possible
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
