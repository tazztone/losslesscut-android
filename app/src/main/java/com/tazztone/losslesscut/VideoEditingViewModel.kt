package com.tazztone.losslesscut

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class VideoEditingUiState {
    object Initial : VideoEditingUiState()
    object Loading : VideoEditingUiState()
    data class Success(
        val videoUri: Uri,
        val videoFileName: String,
        val keyframes: List<Float>
    ) : VideoEditingUiState()
    data class Error(val message: String) : VideoEditingUiState()
}

class VideoEditingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<VideoEditingUiState>(VideoEditingUiState.Initial)
    val uiState: StateFlow<VideoEditingUiState> = _uiState.asStateFlow()

    private var currentVideoUri: Uri? = null

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
                } catch (e: Exception) {
                    android.util.Log.w("ViewModel", "Failed to extract metadata: ${e.message}")
                } finally {
                    retriever.release()
                }

                _uiState.value = VideoEditingUiState.Success(
                    videoUri = videoUri,
                    videoFileName = fileName,
                    keyframes = keyframes.map { it.toFloat() }
                )
            } catch (e: Exception) {
                _uiState.value = VideoEditingUiState.Error("Failed to load video: ${e.message}")
            }
        }
    }

    fun trimVideo(context: Context, startMs: Long, endMs: Long, isLossless: Boolean) {
        val uri = currentVideoUri ?: return
        
        viewModelScope.launch {
            _uiState.value = VideoEditingUiState.Loading
            
            if (isLossless) {
                val outputUri = StorageUtils.createVideoOutputUri(context, "trimmed_${System.currentTimeMillis()}.mp4")
                if (outputUri == null) {
                    _uiState.value = VideoEditingUiState.Error("Failed to create output file")
                    return@launch
                }

                val result = LosslessEngine.executeLosslessCut(context, uri, outputUri, startMs, endMs)
                
                result.fold(
                    onSuccess = { newUri ->
                        _uiState.value = VideoEditingUiState.Success(
                            videoUri = newUri,
                            videoFileName = "trimmed_video.mp4",
                            keyframes = emptyList() // Needs re-probe if we want to continue editing
                        )
                        currentVideoUri = newUri
                    },
                    onFailure = { error ->
                        _uiState.value = VideoEditingUiState.Error("Trim failed: ${error.message}")
                    }
                )
            } else {
                _uiState.value = VideoEditingUiState.Error("Precise mode coming in v2.0")
            }
        }
    }
}
