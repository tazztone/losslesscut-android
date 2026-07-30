package com.tazztone.losslesscut.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.cache.IAnalysisCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val undoLimit: Int = 30,
    val snapshotFormat: String = "JPEG",
    val jpgQuality: Int = 95,
    val customOutputUri: String? = null,
    val accentColor: String = "cyan",
    val autoExtractWaveforms: Boolean = true,
    val visualFrameStep: Int = 5,
    val cacheCapacityMB: Int = 250,
    val cacheRetentionDays: Int = 30,
    val cacheUsageBytes: Long = 0L,
    val language: String = "system",
    val isClearingCache: Boolean = false,
    val cacheClearSuccessMessage: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val analysisCache: IAnalysisCache?
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.undoLimitFlow,
                preferences.snapshotFormatFlow,
                preferences.jpgQualityFlow,
                preferences.customOutputUriFlow,
                preferences.accentColorFlow,
                preferences.autoExtractWaveformsFlow,
                preferences.defaultVisualFrameStepFlow,
                preferences.cacheCapacityMBFlow,
                preferences.cacheRetentionDaysFlow,
                preferences.languageFlow
            ) { args ->
                val undoLimit = args[INDEX_UNDO_LIMIT] as Int
                val snapshotFormat = args[INDEX_SNAPSHOT_FORMAT] as String
                val jpgQuality = args[INDEX_JPG_QUALITY] as Int
                val customOutputUri = args[INDEX_CUSTOM_OUTPUT_URI] as String?
                val accentColor = args[INDEX_ACCENT_COLOR] as String
                val autoExtractWaveforms = args[INDEX_AUTO_EXTRACT_WAVEFORMS] as Boolean
                val visualFrameStep = args[INDEX_VISUAL_FRAME_STEP] as Int
                val cacheCapacityMB = args[INDEX_CACHE_CAPACITY_MB] as Int
                val cacheRetentionDays = args[INDEX_CACHE_RETENTION_DAYS] as Int
                val language = args[INDEX_LANGUAGE] as String

                _uiState.value.copy(
                    undoLimit = undoLimit,
                    snapshotFormat = snapshotFormat,
                    jpgQuality = jpgQuality,
                    customOutputUri = customOutputUri,
                    accentColor = accentColor,
                    autoExtractWaveforms = autoExtractWaveforms,
                    visualFrameStep = visualFrameStep,
                    cacheCapacityMB = cacheCapacityMB,
                    cacheRetentionDays = cacheRetentionDays,
                    language = language
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }

        refreshCacheUsage()
    }

    fun refreshCacheUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val usage = analysisCache?.getCacheUsageBytes() ?: 0L
            _uiState.update { it.copy(cacheUsageBytes = usage) }
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            preferences.setLanguage(language)
        }
    }

    fun setUndoLimit(limit: Int) {
        viewModelScope.launch {
            preferences.setUndoLimit(limit)
        }
    }

    fun setSnapshotFormat(isJpeg: Boolean) {
        viewModelScope.launch {
            preferences.setSnapshotFormat(if (isJpeg) "JPEG" else "PNG")
        }
    }

    fun setJpgQuality(quality: Int) {
        viewModelScope.launch {
            preferences.setJpgQuality(quality)
        }
    }

    fun setCustomOutputUri(uri: String?) {
        viewModelScope.launch {
            preferences.setCustomOutputUri(uri)
        }
    }

    fun setAccentColor(color: String) {
        viewModelScope.launch {
            preferences.setAccentColor(color)
        }
    }

    fun setAutoExtractWaveforms(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoExtractWaveforms(enabled)
        }
    }

    fun setVisualFrameStep(step: Int) {
        viewModelScope.launch {
            preferences.setDefaultVisualFrameStep(step)
        }
    }

    fun setCacheCapacityMB(capacityMB: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.setCacheCapacityMB(capacityMB)
            refreshCacheUsage()
        }
    }

    fun setCacheRetentionDays(days: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.setCacheRetentionDays(days)
            refreshCacheUsage()
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isClearingCache = true) }
            analysisCache?.clearCache()
            val usage = analysisCache?.getCacheUsageBytes() ?: 0L
            _uiState.update { 
                it.copy(
                    cacheUsageBytes = usage,
                    isClearingCache = false,
                    cacheClearSuccessMessage = true
                )
            }
        }
    }

    fun clearCacheMessageShown() {
        _uiState.update { it.copy(cacheClearSuccessMessage = false) }
    }

    companion object {
        private const val INDEX_UNDO_LIMIT = 0
        private const val INDEX_SNAPSHOT_FORMAT = 1
        private const val INDEX_JPG_QUALITY = 2
        private const val INDEX_CUSTOM_OUTPUT_URI = 3
        private const val INDEX_ACCENT_COLOR = 4
        private const val INDEX_AUTO_EXTRACT_WAVEFORMS = 5
        private const val INDEX_VISUAL_FRAME_STEP = 6
        private const val INDEX_CACHE_CAPACITY_MB = 7
        private const val INDEX_CACHE_RETENTION_DAYS = 8
        private const val INDEX_LANGUAGE = 9
    }
}
