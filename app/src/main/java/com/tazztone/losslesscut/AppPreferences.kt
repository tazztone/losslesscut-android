package com.tazztone.losslesscut

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFS_NAME = "lossless_cut_prefs"
    private const val KEY_UNDO_LIMIT = "undo_limit"
    private const val KEY_SNAPSHOT_FORMAT = "snapshot_format"
    private const val KEY_JPG_QUALITY = "jpg_quality"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getUndoLimit(context: Context): Int {
        return getPrefs(context).getInt(KEY_UNDO_LIMIT, 30) // Default 30
    }

    fun setUndoLimit(context: Context, limit: Int) {
        getPrefs(context).edit().putInt(KEY_UNDO_LIMIT, limit).apply()
    }

    fun getSnapshotFormat(context: Context): String {
        return getPrefs(context).getString(KEY_SNAPSHOT_FORMAT, "PNG") ?: "PNG"
    }

    fun setSnapshotFormat(context: Context, format: String) {
        getPrefs(context).edit().putString(KEY_SNAPSHOT_FORMAT, format).apply()
    }

    fun getJpgQuality(context: Context): Int {
        return getPrefs(context).getInt(KEY_JPG_QUALITY, 95) // Default 95
    }

    fun setJpgQuality(context: Context, quality: Int) {
        getPrefs(context).edit().putInt(KEY_JPG_QUALITY, quality).apply()
    }
}
