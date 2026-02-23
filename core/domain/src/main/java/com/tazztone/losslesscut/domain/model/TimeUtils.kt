package com.tazztone.losslesscut.domain.model


import java.util.Locale

object TimeUtils {
    /**
     * Formats milliseconds into a string: "HH:mm:ss.mmm" or "mm:ss.mmm".
     */
    fun formatDuration(milliseconds: Long): String {
        val secondsTotal = milliseconds / 1000
        val millis = milliseconds % 1000
        val hours = secondsTotal / 3600
        val minutes = (secondsTotal % 3600) / 60
        val seconds = secondsTotal % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, millis)
        }
    }

    /**
     * Formats milliseconds into a filename-friendly string: "00h00m00s000ms"
     * Only includes non-zero larger units.
     */
    fun formatFilenameDuration(milliseconds: Long): String {
        val secondsTotal = milliseconds / 1000
        val ms = milliseconds % 1000
        val hrs = secondsTotal / 3600
        val mins = (secondsTotal % 3600) / 60
        val secs = secondsTotal % 60

        return buildString {
            if (hrs > 0) append(String.format(Locale.getDefault(), "%02dh", hrs))
            if (mins > 0 || hrs > 0) append(String.format(Locale.getDefault(), "%02dm", mins))
            append(String.format(Locale.getDefault(), "%02ds", secs))
            if (ms > 0) append(String.format(Locale.getDefault(), "%03dms", ms))
        }
    }
}
