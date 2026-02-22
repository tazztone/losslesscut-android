package com.tazztone.losslesscut.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.data.MediaClip
import com.tazztone.losslesscut.data.SegmentAction
import com.tazztone.losslesscut.data.TrimSegment
import com.tazztone.losslesscut.di.IoDispatcher
import com.tazztone.losslesscut.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.engine.LosslessEngineInterface
import com.tazztone.losslesscut.utils.StorageUtils
import com.tazztone.losslesscut.utils.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.util.UUID
import javax.inject.Inject

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
    
    private val keyframeCache = object : LinkedHashMap<String, List<Long>>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Long>>?): Boolean {
            return size > 20
        }
    }
    @Volatile private var isSnapshotInProgress = false
    private var waveformJob: Job? = null
    
    private var undoLimit = 30
    private val gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriAdapter())
        .registerTypeAdapter(UUID::class.java, UuidAdapter())
        .create()
    
    private class UriAdapter : JsonSerializer<Uri>, JsonDeserializer<Uri> {
        override fun serialize(src: Uri, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.toString())
        }
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Uri {
            return Uri.parse(json.asString)
        }
    }

    private class UuidAdapter : JsonSerializer<UUID>, JsonDeserializer<UUID> {
        override fun serialize(src: UUID, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.toString())
        }
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UUID {
            return UUID.fromString(json.asString)
        }
    }

    companion object {
        const val MIN_SEGMENT_DURATION_MS = 100L
    }

    fun initialize(uris: List<Uri>) {
        if (_uiState.value !is VideoEditingUiState.Initial) return
        
        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading
            try {
                // Initialize with multiple clips
                val clips = uris.map { uri ->
                    engine.getMediaMetadata(context, uri).fold(
                        onSuccess = { meta ->
                            MediaClip(
                                uri = uri,
                                fileName = storageUtils.getFileName(uri),
                                durationMs = meta.durationMs,
                                width = meta.width,
                                height = meta.height,
                                videoMime = meta.videoMime,
                                audioMime = meta.audioMime,
                                sampleRate = meta.sampleRate,
                                channelCount = meta.channelCount,
                                fps = meta.fps,
                                rotation = meta.rotation,
                                isAudioOnly = meta.videoMime == null,
                                segments = listOf(TrimSegment(startMs = 0, endMs = meta.durationMs))
                            )
                        },
                        onFailure = { throw it }
                    )
                }
                
                currentClips = clips
                selectedClipIndex = 0
                loadClipData(selectedClipIndex)
            } catch (e: Exception) {
                _uiState.value = VideoEditingUiState.Error(context.getString(R.string.error_load_video, e.message))
            }
        }
    }

    private fun loadClipData(index: Int) {
        val clip = currentClips[index]
        viewModelScope.launch {
            // Get keyframes
            val cacheKey = clip.uri.toString()
            val kfs = keyframeCache[cacheKey] ?: withContext(ioDispatcher) {
                engine.getKeyframes(context, clip.uri).getOrElse { emptyList() }
            }
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
            val cached = withContext(ioDispatcher) { loadWaveformFromCache(cacheKey) }
            if (cached != null) {
                _waveformData.value = cached
                return@launch
            }

            AudioWaveformExtractor.extract(context, clip.uri)
                ?.let { waveform ->
                    _waveformData.value = waveform
                    saveWaveformToCache(cacheKey, waveform)
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
                    engine.getMediaMetadata(context, uri).fold(
                        onSuccess = { meta ->
                            MediaClip(
                                uri = uri,
                                fileName = storageUtils.getFileName(uri),
                                durationMs = meta.durationMs,
                                width = meta.width,
                                height = meta.height,
                                videoMime = meta.videoMime,
                                audioMime = meta.audioMime,
                                sampleRate = meta.sampleRate,
                                channelCount = meta.channelCount,
                                fps = meta.fps,
                                rotation = meta.rotation,
                                isAudioOnly = meta.videoMime == null,
                                segments = listOf(TrimSegment(startMs = 0, endMs = meta.durationMs))
                            )
                        },
                        onFailure = { throw it }
                    )
                }
                saveToHistory()
                currentClips = currentClips + newClips
                updateState()
            } catch (e: Exception) {
                _uiEvents.emit(context.getString(R.string.error_load_video, e.message))
            }
        }
    }

    fun removeClip(index: Int) {
        if (currentClips.size <= 1) {
            viewModelScope.launch { _uiEvents.emit(context.getString(R.string.error_cannot_delete_last)) }
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
        saveToHistory()
        val newList = currentClips.toMutableList()
        val item = newList.removeAt(from)
        newList.add(to, item)
        currentClips = newList
        if (selectedClipIndex == from) selectedClipIndex = to
        else if (selectedClipIndex in (minOf(from, to)..maxOf(from, to))) {
            if (from < to) selectedClipIndex-- else selectedClipIndex++
        }
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
            viewModelScope.launch { _uiEvents.emit(context.getString(R.string.error_segment_too_small_split)) }
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
        val currentClip = currentClips[selectedClipIndex]
        val segment = currentClip.segments.find { it.id == id } ?: return
        
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
            currentClips = history.removeAt(history.size - 1)
            updateState()
            loadClipData(selectedClipIndex)
        }
    }

    private fun saveToHistory() {
        history.add(currentClips.map { it.copy(segments = it.segments.toList()) })
        if (history.size > undoLimit) history.removeAt(0)
    }

    private fun updateState() {
        val clip = currentClips[selectedClipIndex]
        _uiState.value = VideoEditingUiState.Success(
            clips = currentClips,
            selectedClipIndex = selectedClipIndex,
            keyframes = currentKeyframes,
            segments = clip.segments,
            selectedSegmentId = selectedSegmentId,
            canUndo = history.isNotEmpty(),
            videoFps = clip.fps,
            isAudioOnly = clip.isAudioOnly,
            hasAudioTrack = clip.audioMime != null,
            isSnapshotInProgress = isSnapshotInProgress
        )
    }

    fun exportSegments(isLossless: Boolean, keepAudio: Boolean, keepVideo: Boolean, rotationOverride: Int?, mergeSegments: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = VideoEditingUiState.Loading
                
                if (mergeSegments) {
                    // Placeholder for merge logic
                    _uiEvents.emit("Merge not implemented yet")
                } else {
                    val selectedClip = currentClips[selectedClipIndex]
                    val segments = selectedClip.segments.filter { it.action == SegmentAction.KEEP }
                    var successCount = 0
                    val errors = mutableListOf<String>()

                    for ((index, segment) in segments.withIndex()) {
                        val extension = if (!keepVideo) "m4a" else "mp4"
                        val timeSuffix = "_${TimeUtils.formatFilenameDuration(segment.startMs)}-${TimeUtils.formatFilenameDuration(segment.endMs)}"
                        val outputUri = storageUtils.createMediaOutputUri(selectedClip.fileName, !keepVideo, timeSuffix)

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
                        _uiEvents.emit(context.getString(R.string.export_success, successCount))
                        _isDirty.value = false
                    } else if (errors.isNotEmpty()) {
                        _uiEvents.emit(context.getString(R.string.export_partial_success, successCount, errors.joinToString()))
                    }
                }
                updateState()
            } catch (e: Exception) {
                _uiState.value = VideoEditingUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun extractSnapshot(positionMs: Long) {
        viewModelScope.launch {
            if (isSnapshotInProgress) return@launch
            isSnapshotInProgress = true
            updateState()
            
            try {
                val clip = currentClips[selectedClipIndex]
                val bitmap: Bitmap? = withContext(ioDispatcher) {
                    engine.getFrameAt(context, clip.uri, positionMs)
                }

                if (bitmap != null) {
                    val stableBitmap: Bitmap = bitmap
                    val fileName = "snapshot_${System.currentTimeMillis()}.jpg"
                    val outputUri = storageUtils.createImageOutputUri(fileName)
                    if (outputUri != null) {
                        try {
                            withContext(ioDispatcher) {
                                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                                    stableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("VideoEditingViewModel", "Failed to write snapshot", e)
                        }
                        _uiEvents.emit(context.getString(R.string.snapshot_saved, fileName))
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoEditingViewModel", "Snapshot failed", e)
                _uiEvents.emit(context.getString(R.string.snapshot_failed))
            } finally {
                isSnapshotInProgress = false
                updateState()
            }
        }
    }

    private fun saveWaveformToCache(cacheKey: String, waveform: FloatArray) {
        try {
            val cacheFile = File(context.cacheDir, cacheKey)
            DataOutputStream(FileOutputStream(cacheFile)).use { out ->
                out.writeInt(waveform.size)
                waveform.forEach { out.writeFloat(it) }
            }
        } catch (e: Exception) {
            Log.e("VideoEditingViewModel", "Failed to save waveform to cache", e)
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

    fun saveSession() {
        if (currentClips.isEmpty()) return
        
        viewModelScope.launch(ioDispatcher) {
            try {
                val sessionId = currentClips.first().uri.toString().hashCode().toString()
                val sessionFile = File(context.cacheDir, "session_$sessionId.json")
                
                val json = gson.toJson(currentClips)
                sessionFile.writeText(json)
                Log.d("VideoEditingViewModel", "Session saved for $sessionId")
            } catch (e: Exception) {
                Log.e("VideoEditingViewModel", "Failed to save session", e)
            }
        }
    }

    suspend fun hasSavedSession(uri: Uri): Boolean {
        val sessionId = uri.toString().hashCode().toString()
        val sessionFile = File(context.cacheDir, "session_$sessionId.json")
        return sessionFile.exists()
    }

    fun restoreSession(uri: Uri) {
        viewModelScope.launch {
            try {
                val sessionId = uri.toString().hashCode().toString()
                val sessionFile = File(context.cacheDir, "session_$sessionId.json")
                if (!sessionFile.exists()) return@launch

                val json = withContext(ioDispatcher) { sessionFile.readText() }
                val type = object : TypeToken<List<MediaClip>>() {}.type
                val restoredClips: List<MediaClip> = gson.fromJson(json, type)
                
                currentClips = restoredClips
                selectedClipIndex = 0
                _isDirty.value = true
                loadClipData(selectedClipIndex)
                _uiEvents.emit(context.getString(R.string.session_restored))
            } catch (e: Exception) {
                Log.e("VideoEditingViewModel", "Failed to restore session", e)
            }
        }
    }
}
