package com.tazztone.losslesscut.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.di.IoDispatcher
import com.tazztone.losslesscut.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.engine.LosslessEngineInterface
import com.tazztone.losslesscut.utils.StorageUtils
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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

enum class SegmentAction { KEEP, DISCARD }

data class TrimSegment(
    val id: UUID = UUID.randomUUID(),
    val startMs: Long,
    val endMs: Long,
    val action: SegmentAction = SegmentAction.KEEP
)

data class MediaClip(
    val id: UUID = UUID.randomUUID(),
    val uri: Uri,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoMime: String?,
    val audioMime: String?,
    val sampleRate: Int,
    val channelCount: Int,
    val fps: Float,
    val rotation: Int,
    val isAudioOnly: Boolean,
    val segments: List<TrimSegment> = listOf(TrimSegment(startMs = 0, endMs = durationMs))
)

sealed class VideoEditingUiState {
    object Initial : VideoEditingUiState()
    object Loading : VideoEditingUiState()
    data class Success(
        val clips: List<MediaClip>,
        val selectedClipIndex: Int = 0,
        val keyframes: List<Long>,
        val segments: List<TrimSegment>,
        val selectedSegmentId: UUID? = null,
        val canUndo: Boolean = false,
        val videoFps: Float = 30f,
        val isAudioOnly: Boolean = false,
        val hasAudioTrack: Boolean = true,
        val isSnapshotInProgress: Boolean = false
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

    private val _waveformData = MutableStateFlow<FloatArray?>(null)
    val waveformData: StateFlow<FloatArray?> = _waveformData.asStateFlow()

    private var currentClips = listOf<MediaClip>()
    private var selectedClipIndex = 0
    private var currentKeyframes: List<Long> = emptyList()
    private var history = mutableListOf<List<MediaClip>>()
    private var selectedSegmentId: UUID? = null
    
    private val keyframeCache = mutableMapOf<Uri, List<Long>>()
    private var isSnapshotInProgress = false
    
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

    fun initialize(uris: List<Uri>) {
        if (currentClips.isNotEmpty()) return
        if (uris.isEmpty()) {
            _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_invalid_video_uri_msg))
            return
        }
        
        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading
            try {
                val clips = mutableListOf<MediaClip>()
                var baseMetadata: StorageUtils.DetailedMetadata? = null

                for (uri in uris) {
                    val metadata = storageUtils.getDetailedMetadata(uri)
                    
                    if (baseMetadata == null) {
                        baseMetadata = metadata
                    } else {
                        val result = validateCompatibility(baseMetadata, metadata)
                        if (result is CompatibilityResult.Incompatible) {
                             _uiEvents.emit(context.getString(R.string.error_incompatible_format, metadata.fileName) + " (${result.reason})")
                             continue
                        }
                    }

                    clips.add(MediaClip(
                        uri = uri,
                        fileName = metadata.fileName,
                        durationMs = metadata.durationMs,
                        width = metadata.width,
                        height = metadata.height,
                        videoMime = metadata.videoMime,
                        audioMime = metadata.audioMime,
                        sampleRate = metadata.sampleRate,
                        channelCount = metadata.channelCount,
                        fps = metadata.fps,
                        rotation = metadata.rotation,
                        isAudioOnly = metadata.isAudioOnly
                    ))
                }

                if (clips.isEmpty()) {
                    _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_no_valid_clips))
                    return@launch
                }

                currentClips = clips
                selectedClipIndex = 0
                history.clear()
                history.add(currentClips.map { it.copy() })
                _isDirty.value = false

                loadClipData(selectedClipIndex)
            } catch (e: Exception) {
                val msg = context.getString(R.string.error_load_video, e.message)
                _uiState.value = VideoEditingUiState.Error(msg)
            }
        }
    }

    fun addClips(uris: List<Uri>) {
        if (uris.isEmpty() || currentClips.isEmpty()) return
        
        viewModelScope.launch {
            try {
                val newClips = mutableListOf<MediaClip>()
                val baseClip = currentClips[0]
                val baseMetadata = StorageUtils.DetailedMetadata(
                    fileName = baseClip.fileName,
                    durationMs = baseClip.durationMs,
                    width = baseClip.width,
                    height = baseClip.height,
                    videoMime = baseClip.videoMime,
                    audioMime = baseClip.audioMime,
                    sampleRate = baseClip.sampleRate,
                    channelCount = baseClip.channelCount,
                    fps = baseClip.fps,
                    rotation = baseClip.rotation,
                    isAudioOnly = baseClip.isAudioOnly
                )

                for (uri in uris) {
                    val metadata = storageUtils.getDetailedMetadata(uri)
                    val result = validateCompatibility(baseMetadata, metadata)
                    
                    if (result is CompatibilityResult.Incompatible) {
                        _uiEvents.emit(context.getString(R.string.error_incompatible_format, metadata.fileName) + " (${result.reason})")
                        continue
                    }

                    newClips.add(MediaClip(
                        uri = uri,
                        fileName = metadata.fileName,
                        durationMs = metadata.durationMs,
                        width = metadata.width,
                        height = metadata.height,
                        videoMime = metadata.videoMime,
                        audioMime = metadata.audioMime,
                        sampleRate = metadata.sampleRate,
                        channelCount = metadata.channelCount,
                        fps = metadata.fps,
                        rotation = metadata.rotation,
                        isAudioOnly = metadata.isAudioOnly
                    ))
                }

                if (newClips.isNotEmpty()) {
                    currentClips = currentClips + newClips
                    pushToHistory()
                    updateSuccessState()
                    _uiEvents.emit(context.getString(R.string.clips_added, newClips.size))
                }
            } catch (e: Exception) {
                _uiEvents.emit(context.getString(R.string.error_load_video, e.message))
            }
        }
    }

    sealed class CompatibilityResult {
        object Compatible : CompatibilityResult()
        data class Incompatible(val reason: String) : CompatibilityResult()
    }

    private fun validateCompatibility(base: StorageUtils.DetailedMetadata, target: StorageUtils.DetailedMetadata): CompatibilityResult {
        if (base.isAudioOnly != target.isAudioOnly) return CompatibilityResult.Incompatible("Audio-only mismatch")
        if (base.videoMime != target.videoMime) return CompatibilityResult.Incompatible("Video codec mismatch: ${base.videoMime} vs ${target.videoMime}")
        if (base.audioMime != target.audioMime) return CompatibilityResult.Incompatible("Audio codec mismatch: ${base.audioMime} vs ${target.audioMime}")
        if (base.width != target.width || base.height != target.height) return CompatibilityResult.Incompatible("Resolution mismatch: ${base.width}x${base.height} vs ${target.width}x${target.height}")
        if (base.sampleRate != target.sampleRate || base.channelCount != target.channelCount) return CompatibilityResult.Incompatible("Audio format mismatch (Sample rate/Channels)")
        if (base.rotation != target.rotation) return CompatibilityResult.Incompatible("Rotation mismatch")
        return CompatibilityResult.Compatible
    }

    private suspend fun loadClipData(index: Int) {
        val clip = currentClips[index]
        
        val keyframes = keyframeCache.getOrPut(clip.uri) {
            engine.probeKeyframes(context, clip.uri)
        }
        currentKeyframes = keyframes
        
        updateSuccessState()

        viewModelScope.launch(ioDispatcher) {
            val cacheKey = waveformCacheKey(clip.uri, clip.durationMs)
            val cachedWaveform = loadWaveformFromCache(cacheKey)
            
            if (cachedWaveform != null) {
                _waveformData.value = cachedWaveform
            } else {
                val finalWaveform = AudioWaveformExtractor.extract(context, clip.uri, bucketCount = 1000) { progressiveWaveform ->
                    _waveformData.value = progressiveWaveform
                }
                if (finalWaveform != null) {
                    saveWaveformToCache(cacheKey, finalWaveform)
                    _waveformData.value = finalWaveform
                }
            }
        }
    }

    private fun updateSuccessState() {
        if (currentClips.isEmpty()) return
        val selectedClip = currentClips[selectedClipIndex]
        _uiState.value = VideoEditingUiState.Success(
            clips = currentClips.map { it.copy() },
            selectedClipIndex = selectedClipIndex,
            keyframes = currentKeyframes,
            segments = selectedClip.segments.map { it.copy() },
            selectedSegmentId = selectedSegmentId,
            canUndo = history.size > 1,
            videoFps = selectedClip.fps,
            isAudioOnly = selectedClip.isAudioOnly,
            hasAudioTrack = selectedClip.audioMime != null,
            isSnapshotInProgress = isSnapshotInProgress
        )
    }

    private fun pushToHistory() {
        val newState = currentClips.map { it.copy() }
        if (history.isNotEmpty() && history.last() == newState) return
        history.add(newState)
        while (history.size > undoLimit) { history.removeAt(0) }
        _isDirty.value = true
    }

    fun undo() {
        if (history.size > 1) {
            history.removeAt(history.size - 1)
            currentClips = history.last().map { it.copy() }
            selectedSegmentId = null
            viewModelScope.launch { loadClipData(selectedClipIndex) }
        }
    }

    fun selectClip(index: Int) {
        if (index < 0 || index >= currentClips.size || index == selectedClipIndex) return
        selectedClipIndex = index
        selectedSegmentId = null
        viewModelScope.launch { loadClipData(selectedClipIndex) }
    }

    fun removeClip(index: Int) {
        if (currentClips.size <= 1 || index < 0 || index >= currentClips.size) return
        
        val newClips = currentClips.toMutableList()
        newClips.removeAt(index)
        
        if (selectedClipIndex >= newClips.size) {
            selectedClipIndex = newClips.size - 1
        } else if (selectedClipIndex == index) {
            // If we removed the currently selected clip, stay at the same index 
            // (which is now the next clip) unless we were at the end.
            selectedClipIndex = selectedClipIndex.coerceAtMost(newClips.size - 1)
        } else if (selectedClipIndex > index) {
            selectedClipIndex--
        }

        currentClips = newClips
        pushToHistory()
        selectedSegmentId = null
        viewModelScope.launch { loadClipData(selectedClipIndex) }
    }

    fun reorderClips(fromIndex: Int, toIndex: Int) {
        val newClips = currentClips.toMutableList()
        val clip = newClips.removeAt(fromIndex)
        newClips.add(toIndex, clip)
        
        if (selectedClipIndex == fromIndex) {
            selectedClipIndex = toIndex
        } else if (fromIndex < selectedClipIndex && toIndex >= selectedClipIndex) {
            selectedClipIndex--
        } else if (fromIndex > selectedClipIndex && toIndex <= selectedClipIndex) {
            selectedClipIndex++
        }

        currentClips = newClips
        pushToHistory()
        updateSuccessState()
    }

    fun selectSegment(id: UUID?) {
        selectedSegmentId = id
        updateSuccessState()
    }

    fun splitSegmentAt(timeMs: Long) {
        val clip = currentClips[selectedClipIndex]
        val segments = clip.segments
        val segmentToSplit = segments.find { timeMs >= it.startMs && timeMs <= it.endMs } ?: return
        
        if (timeMs - segmentToSplit.startMs < MIN_SEGMENT_DURATION_MS || 
            segmentToSplit.endMs - timeMs < MIN_SEGMENT_DURATION_MS) {
            viewModelScope.launch { 
                _uiEvents.emit(context.getString(R.string.error_segment_too_small_split)) 
            }
            return
        }

        val index = segments.indexOf(segmentToSplit)
        val newSegments = segments.toMutableList()
        
        val left = segmentToSplit.copy(id = UUID.randomUUID(), endMs = timeMs)
        val right = segmentToSplit.copy(id = UUID.randomUUID(), startMs = timeMs)
        
        newSegments.removeAt(index)
        newSegments.add(index, left)
        newSegments.add(index + 1, right)
        
        currentClips = currentClips.mapIndexed { i, c ->
            if (i == selectedClipIndex) c.copy(segments = newSegments) else c
        }
        selectedSegmentId = right.id
        pushToHistory()
        updateSuccessState()
    }

    fun toggleSegmentAction(id: UUID) {
        val clip = currentClips[selectedClipIndex]
        val segment = clip.segments.find { it.id == id } ?: return
        
        if (segment.action == SegmentAction.KEEP) {
            val keepCount = clip.segments.count { it.action == SegmentAction.KEEP }
            if (keepCount <= 1) {
                viewModelScope.launch { _uiEvents.emit(context.getString(R.string.error_cannot_delete_last)) }
                return
            }
        }
        
        val newSegments = clip.segments.map {
            if (it.id == id) {
                it.copy(action = if (it.action == SegmentAction.KEEP) SegmentAction.DISCARD else SegmentAction.KEEP)
            } else {
                it
            }
        }
        
        currentClips = currentClips.mapIndexed { i, c ->
            if (i == selectedClipIndex) c.copy(segments = newSegments) else c
        }
        pushToHistory()
        updateSuccessState()
    }

    fun deleteSegment(id: UUID) {
        toggleSegmentAction(id)
    }

    fun updateSegmentBounds(id: UUID, startMs: Long, endMs: Long) {
        val clip = currentClips[selectedClipIndex]
        val newSegments = clip.segments.map {
            if (it.id == id) {
                if (endMs - startMs < MIN_SEGMENT_DURATION_MS) {
                    if (startMs != it.startMs) {
                        it.copy(startMs = (endMs - MIN_SEGMENT_DURATION_MS).coerceAtLeast(0))
                    } else {
                        it.copy(endMs = startMs + MIN_SEGMENT_DURATION_MS)
                    }
                } else {
                    it.copy(startMs = startMs, endMs = endMs)
                }
            } else {
                it
            }
        }
        currentClips = currentClips.mapIndexed { i, c ->
            if (i == selectedClipIndex) c.copy(segments = newSegments) else c
        }
        updateSuccessState()
    }

    fun commitSegmentBounds() {
        pushToHistory()
        updateSuccessState()
    }

    fun exportSegments(isLossless: Boolean, keepAudio: Boolean = true, keepVideo: Boolean = true, rotationOverride: Int? = null, mergeSegments: Boolean = false) {
        if (currentClips.isEmpty()) return
        
        val clipsToExport = if (mergeSegments) currentClips else listOf(currentClips[selectedClipIndex])
        val allSegmentsToExport = clipsToExport.flatMap { it.segments.filter { s -> s.action == SegmentAction.KEEP } }
        
        if (allSegmentsToExport.isEmpty()) {
            _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_no_segments_export))
            return
        }

        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading
            if (isLossless) {
                val exportIsAudioOnly = !keepVideo
                if (mergeSegments) {
                    val extension = if (exportIsAudioOnly) "m4a" else "mp4"
                    val baseName = currentClips[0].fileName.substringBeforeLast(".")
                    val fileName = "${baseName}_merged_${System.currentTimeMillis()}.$extension"
                    val outputUri = storageUtils.createMediaOutputUri(fileName, exportIsAudioOnly)

                    if (outputUri == null) {
                        _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_create_file))
                        return@launch
                    }

                    val result = engine.executeLosslessMerge(context, outputUri, clipsToExport, keepAudio, keepVideo, rotationOverride)
                    result.fold(
                        onSuccess = {
                            val msg = if (exportIsAudioOnly) context.getString(R.string.export_success_audio, 1) 
                                     else context.getString(R.string.export_success, 1)
                            _uiEvents.emit(msg)
                            _isDirty.value = false
                            updateSuccessState()
                        },
                        onFailure = {
                            _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_export_failed, it.message))
                        }
                    )
                } else {
                    val selectedClip = currentClips[selectedClipIndex]
                    val segments = selectedClip.segments.filter { it.action == SegmentAction.KEEP }
                    var successCount = 0
                    val errors = mutableListOf<String>()

                    for ((index, segment) in segments.withIndex()) {
                        val extension = if (exportIsAudioOnly) "m4a" else "mp4"
                        val fileName = "clip_${System.currentTimeMillis()}_$index.$extension"
                        val outputUri = storageUtils.createMediaOutputUri(fileName, exportIsAudioOnly)

                        if (outputUri == null) {
                            errors.add(context.getString(R.string.error_create_file))
                            continue
                        }

                        val result = engine.executeLosslessCut(context, selectedClip.uri, outputUri, segment.startMs, segment.endMs, keepAudio, keepVideo, rotationOverride)
                        result.fold(
                            onSuccess = { successCount++ },
                            onFailure = { errors.add(context.getString(R.string.error_segment_failed, index, it.message)) }
                        )
                    }

                    if (errors.isEmpty() && successCount > 0) {
                        val msg = if (exportIsAudioOnly) context.getString(R.string.export_success_audio, successCount) 
                                 else context.getString(R.string.export_success, successCount)
                        _uiEvents.emit(msg)
                        _isDirty.value = false
                        updateSuccessState()
                    } else if (successCount > 0) {
                        _uiEvents.emit(context.getString(R.string.export_partial_success, successCount, errors.joinToString()))
                        updateSuccessState()
                    } else {
                        _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_export_failed, errors.joinToString()))
                    }
                }
            } else {
                _uiEvents.emit(context.getString(R.string.precise_mode_coming_soon))
                updateSuccessState()
            }
        }
    }

    fun extractSnapshot(currentPositionMs: Long) {
        if (currentClips.isEmpty()) return
        val clip = currentClips[selectedClipIndex]
        viewModelScope.launch(ioDispatcher) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                isSnapshotInProgress = true
                updateSuccessState()
                
                retriever.setDataSource(context, clip.uri)
                val bitmap = retriever.getFrameAtTime(currentPositionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bitmap != null) {
                    val formatSetting = preferences.snapshotFormatFlow.first()
                    val isJpeg = formatSetting == "JPEG"
                    val ext = if (isJpeg) "jpeg" else "png"
                    val compressFormat = if (isJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    val quality = if (isJpeg) preferences.jpgQualityFlow.first() else 100

                    val fileName = "snapshot_${System.currentTimeMillis()}.$ext"
                    val outputUri = storageUtils.createImageOutputUri(fileName)
                    if (outputUri != null) {
                        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                            bitmap.compress(compressFormat, quality, outputStream)
                            storageUtils.finalizeImage(outputUri)
                            _uiEvents.emit(context.getString(R.string.snapshot_saved, fileName))
                        } ?: _uiEvents.emit(context.getString(R.string.snapshot_failed))
                    } else {
                         _uiEvents.emit(context.getString(R.string.snapshot_failed))
                    }
                    bitmap.recycle()
                } else {
                    _uiEvents.emit(context.getString(R.string.snapshot_failed))
                }
            } catch (e: Exception) {
                Log.e("VideoEditingViewModel", "Snapshot error", e)
                _uiEvents.emit(context.getString(R.string.error_snapshot_failed_generic))
            } finally {
                retriever.release()
                isSnapshotInProgress = false
                updateSuccessState()
            }
        }
    }

    private fun waveformCacheKey(uri: Uri, durationMs: Long): String {
        return "waveform_${uri.toString().hashCode()}_${durationMs}.bin"
    }

    private fun saveWaveformToCache(cacheKey: String, waveform: FloatArray) {
        try {
            val cacheFile = File(context.cacheDir, cacheKey)
            DataOutputStream(FileOutputStream(cacheFile)).use { out ->
                out.writeInt(waveform.size)
                waveform.forEach { out.writeFloat(it) }
            }
            cleanupOldWaveforms()
        } catch (e: Exception) {
            Log.e("VideoEditingViewModel", "Failed to save waveform to cache", e)
        }
    }

    private fun cleanupOldWaveforms() {
        try {
            val files = context.cacheDir.listFiles { _, name -> name.startsWith("waveform_") && name.endsWith(".bin") }
            if (files != null && files.size > 10) {
                files.sortByDescending { it.lastModified() }
                files.drop(10).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e("VideoEditingViewModel", "Failed to cleanup waveforms", e)
        }
    }

    private fun loadWaveformFromCache(cacheKey: String): FloatArray? {
        val cacheFile = File(context.cacheDir, cacheKey)
        if (!cacheFile.exists()) return null
        return try {
            DataInputStream(FileInputStream(cacheFile)).use { input ->
                val size = input.readInt()
                FloatArray(size) { input.readFloat() }
            }
        } catch (e: Exception) {
            Log.e("VideoEditingViewModel", "Failed to load waveform from cache", e)
            null
        }
    }
}
