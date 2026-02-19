package com.tazztone.losslesscut

import java.util.Locale

object TimeUtils {
    /**
     * Formats milliseconds into a string: "HH:mm:ss" or "mm:ss".
     */
    fun formatDuration(milliseconds: Long): String {
        val secondsTotal = milliseconds / 1000
        val hours = secondsTotal / 3600
        val minutes = (secondsTotal % 3600) / 60
        val seconds = secondsTotal % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
