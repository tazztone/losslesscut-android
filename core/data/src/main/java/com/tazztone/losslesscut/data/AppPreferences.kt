package com.tazztone.losslesscut.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import com.tazztone.losslesscut.domain.cache.IAnalysisCache

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lossless_cut_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val analysisCache: IAnalysisCache? = null
) {

    private object PreferencesKeys {
        val UNDO_LIMIT = intPreferencesKey("undo_limit")
        val SNAPSHOT_FORMAT = stringPreferencesKey("snapshot_format")
        val JPG_QUALITY = intPreferencesKey("jpg_quality")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val CUSTOM_OUTPUT_URI = stringPreferencesKey("custom_output_uri")
        val AUTO_EXTRACT_WAVEFORMS = booleanPreferencesKey("auto_extract_waveforms")
        val DEFAULT_VISUAL_FRAME_STEP = intPreferencesKey("default_visual_frame_step")
        val CACHE_CAPACITY_MB = intPreferencesKey("cache_capacity_mb")
        val CACHE_RETENTION_DAYS = intPreferencesKey("cache_retention_days")
        val LANGUAGE = stringPreferencesKey("language")
    }

    private val sharedPrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    fun getAccentColorSync(): String {
        return sharedPrefs.getString("accent_color", "cyan") ?: "cyan"
    }

    val accentColorFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] ?: "cyan"
        }

    val undoLimitFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.UNDO_LIMIT] ?: 30
        }

    val snapshotFormatFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.SNAPSHOT_FORMAT] ?: "JPEG"
        }

    val jpgQualityFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.JPG_QUALITY] ?: 95
        }

    val customOutputUriFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.CUSTOM_OUTPUT_URI]
        }

    val autoExtractWaveformsFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_EXTRACT_WAVEFORMS] ?: true
        }

    val defaultVisualFrameStepFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_VISUAL_FRAME_STEP] ?: DEFAULT_FRAME_STEP
        }

    suspend fun setUndoLimit(limit: Int) {
        require(limit in 1..100) { "Undo limit must be between 1 and 100" }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNDO_LIMIT] = limit
        }
    }

    suspend fun setSnapshotFormat(format: String) {
        require(format in setOf("PNG", "JPEG")) { "Snapshot format must be PNG or JPEG" }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SNAPSHOT_FORMAT] = format
        }
    }

    suspend fun setJpgQuality(quality: Int) {
        require(quality in 1..100) { "JPG quality must be between 1 and 100" }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.JPG_QUALITY] = quality
        }
    }

    suspend fun setAccentColor(colorName: String) {
        sharedPrefs.edit().putString("accent_color", colorName).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = colorName
        }
    }

    suspend fun setCustomOutputUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PreferencesKeys.CUSTOM_OUTPUT_URI)
            } else {
                preferences[PreferencesKeys.CUSTOM_OUTPUT_URI] = uri
            }
        }
    }

    suspend fun setAutoExtractWaveforms(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_EXTRACT_WAVEFORMS] = enabled
        }
    }

    suspend fun setDefaultVisualFrameStep(frameStep: Int) {
        require(frameStep in MIN_FRAME_STEP..MAX_FRAME_STEP) {
            "Frame step must be between $MIN_FRAME_STEP and $MAX_FRAME_STEP"
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_VISUAL_FRAME_STEP] = frameStep
        }
    }

    val cacheCapacityMBFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.CACHE_CAPACITY_MB] ?: DEFAULT_CACHE_CAPACITY_MB
        }

    val cacheRetentionDaysFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.CACHE_RETENTION_DAYS] ?: DEFAULT_CACHE_RETENTION_DAYS
        }

    val languageFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.LANGUAGE] ?: "system"
        }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language
        }
    }

    suspend fun setCacheCapacityMB(capacityMB: Int) {
        require(capacityMB in MIN_CACHE_CAPACITY_MB..MAX_CACHE_CAPACITY_MB) {
            "Cache capacity must be between $MIN_CACHE_CAPACITY_MB and $MAX_CACHE_CAPACITY_MB MiB"
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CACHE_CAPACITY_MB] = capacityMB
        }
        analysisCache?.updateCachePolicy(capacityMB * BYTES_PER_MIB, cacheRetentionDaysFlow.first())
    }

    suspend fun setCacheRetentionDays(days: Int) {
        require(days in MIN_CACHE_RETENTION_DAYS..MAX_CACHE_RETENTION_DAYS) {
            "Cache retention must be between $MIN_CACHE_RETENTION_DAYS and $MAX_CACHE_RETENTION_DAYS days"
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CACHE_RETENTION_DAYS] = days
        }
        analysisCache?.updateCachePolicy(cacheCapacityMBFlow.first() * BYTES_PER_MIB, days)
    }

    suspend fun applyAnalysisCachePolicy() {
        analysisCache?.updateCachePolicy(
            cacheCapacityMBFlow.first() * BYTES_PER_MIB,
            cacheRetentionDaysFlow.first()
        )
    }

    companion object {
        private const val BYTES_PER_MIB = 1024L * 1024L
        const val DEFAULT_FRAME_STEP = 5
        const val MIN_FRAME_STEP = 1
        const val MAX_FRAME_STEP = 30
        const val DEFAULT_CACHE_CAPACITY_MB = 250
        const val MIN_CACHE_CAPACITY_MB = 50
        const val MAX_CACHE_CAPACITY_MB = 1000
        const val DEFAULT_CACHE_RETENTION_DAYS = 30
        const val MIN_CACHE_RETENTION_DAYS = 1
        const val MAX_CACHE_RETENTION_DAYS = 90
    }
}
