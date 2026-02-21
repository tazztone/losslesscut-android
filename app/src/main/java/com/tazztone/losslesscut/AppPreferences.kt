package com.tazztone.losslesscut

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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lossless_cut_prefs")

class AppPreferences(private val context: Context) {

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
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.JPG_QUALITY] = quality
        }
    }
}
