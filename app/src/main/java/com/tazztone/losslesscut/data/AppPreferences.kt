package com.tazztone.losslesscut.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lossless_cut_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object PreferencesKeys {
        val UNDO_LIMIT = intPreferencesKey("undo_limit")
        val SNAPSHOT_FORMAT = stringPreferencesKey("snapshot_format")
        val JPG_QUALITY = intPreferencesKey("jpg_quality")
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
            preferences[PreferencesKeys.SNAPSHOT_FORMAT] ?: "PNG"
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

    suspend fun setUndoLimit(limit: Int) {
        require(limit in 1..100) { "Undo limit must be between 1 and 100" }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNDO_LIMIT] = limit
        }
    }

    suspend fun setSnapshotFormat(format: String) {
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
}
