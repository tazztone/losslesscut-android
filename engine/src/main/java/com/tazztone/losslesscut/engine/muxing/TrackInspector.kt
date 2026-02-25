package com.tazztone.losslesscut.engine.muxing

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import javax.inject.Inject

data class SelectedTrackPlan(
    val trackMap: Map<Int, Int>, // Extractor index -> Muxer index
    val isVideoTrackMap: Map<Int, Boolean>,
    val bufferSize: Int,
    val durationUs: Long,
    val hasVideoTrack: Boolean
)

/**
 * Isolates the logic for selecting tracks and calculating the required buffer size.
 */
class TrackInspector @Inject constructor() {

    fun inspect(
        extractor: MediaExtractor,
        muxerWriter: MuxerWriter,
        keepAudio: Boolean,
        keepVideo: Boolean,
        selectedTracks: List<Int>?
    ): SelectedTrackPlan {
        var durationUs = -1L
        val trackMap = mutableMapOf<Int, Int>()
        val isVideoTrackMap = mutableMapOf<Int, Boolean>()
        var bufferSize = -1

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            
            if (isTrackSelected(i, mime, keepAudio, keepVideo, selectedTracks)) {
                val isVideo = mime.startsWith("video/")
                if (isVideo) {
                    durationUs = getDuration(format)
                    isVideoTrackMap[i] = true
                } else {
                    isVideoTrackMap[i] = false
                }
                
                trackMap[i] = muxerWriter.addTrack(cleanFormat(format, isVideo))
                bufferSize = maxOf(bufferSize, getBufferSize(format))
            }
        }

        return SelectedTrackPlan(
            trackMap = trackMap,
            isVideoTrackMap = isVideoTrackMap,
            bufferSize = if (bufferSize < 0) DEFAULT_BUFFER_SIZE else bufferSize,
            durationUs = durationUs,
            hasVideoTrack = isVideoTrackMap.values.any { it }
        )
    }

    private fun isTrackSelected(
        index: Int,
        mime: String,
        keepAudio: Boolean,
        keepVideo: Boolean,
        selectedTracks: List<Int>?
    ): Boolean {
        if (selectedTracks != null) return selectedTracks.contains(index)
        val isVideo = mime.startsWith("video/")
        val isAudio = mime.startsWith("audio/")
        return (isVideo && keepVideo) || (isAudio && keepAudio)
    }

    private fun getDuration(format: MediaFormat): Long {
        return if (format.containsKey(MediaFormat.KEY_DURATION)) {
            try {
                format.getLong(MediaFormat.KEY_DURATION)
            } catch (e: ClassCastException) {
                Log.w(TAG, "Could not get duration from video track format", e)
                -1L
            }
        } else -1L
    }

    private fun getBufferSize(format: MediaFormat): Int {
        return if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else -1
    }

    /**
     * Strips non-essential keys from the MediaFormat that can cause MediaMuxer to reject the track.
     */
    private fun cleanFormat(original: MediaFormat, isVideo: Boolean): MediaFormat {
        val clean = if (isVideo) {
            MediaFormat.createVideoFormat(
                original.getString(MediaFormat.KEY_MIME)!!,
                original.getInteger(MediaFormat.KEY_WIDTH),
                original.getInteger(MediaFormat.KEY_HEIGHT)
            )
        } else {
            MediaFormat.createAudioFormat(
                original.getString(MediaFormat.KEY_MIME)!!,
                original.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                original.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            )
        }

        // Essential common keys
        copyKey(original, clean, MediaFormat.KEY_DURATION, KeyType.LONG)
        copyKey(original, clean, MediaFormat.KEY_BIT_RATE, KeyType.INT)
        copyKey(original, clean, MediaFormat.KEY_LANGUAGE, KeyType.STRING)

        if (isVideo) {
            copyKey(original, clean, MediaFormat.KEY_FRAME_RATE, KeyType.INT)
            copyKey(original, clean, MediaFormat.KEY_I_FRAME_INTERVAL, KeyType.INT)
            copyKey(original, clean, "rotation-degrees", KeyType.INT)
        }

        // Codec-specific data (CSD) is critical for lossless reproduction
        for (i in 0..MAX_CSD_INDEX) {
            val csdKey = "csd-$i"
            if (original.containsKey(csdKey)) {
                clean.setByteBuffer(csdKey, original.getByteBuffer(csdKey))
            }
        }

        return clean
    }

    private enum class KeyType { INT, LONG, STRING }

    private fun copyKey(from: MediaFormat, to: MediaFormat, key: String, type: KeyType) {
        if (!from.containsKey(key)) return
        try {
            when (type) {
                KeyType.INT -> to.setInteger(key, from.getInteger(key))
                KeyType.LONG -> to.setLong(key, from.getLong(key))
                KeyType.STRING -> to.setString(key, from.getString(key))
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to copy key $key due to type mismatch", e)
        } catch (e: ClassCastException) {
            Log.w(TAG, "Failed to copy key $key due to type mismatch", e)
        }
    }

    companion object {
        private const val TAG = "TrackInspector"
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB
        private const val MAX_CSD_INDEX = 10
    }
}
