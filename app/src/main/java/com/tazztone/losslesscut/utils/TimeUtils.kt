package com.tazztone.losslesscut.utils
import com.tazztone.losslesscut.di.*
import com.tazztone.losslesscut.customviews.*
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.*
import com.tazztone.losslesscut.viewmodel.*
import com.tazztone.losslesscut.engine.*
import com.tazztone.losslesscut.data.*
import com.tazztone.losslesscut.utils.*

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
}
