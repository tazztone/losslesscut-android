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
            copyKey(original, clean, "color-standard", KeyType.INT)
            copyKey(original, clean, "color-transfer", KeyType.INT)
            copyKey(original, clean, "color-range", KeyType.INT)
            copyKey(original, clean, "profile", KeyType.INT)
            copyKey(original, clean, "level", KeyType.INT)
            copyKey(original, clean, "hdr-static-info", KeyType.BYTE_BUFFER)
            copyKey(original, clean, "hdr10-plus-info", KeyType.BYTE_BUFFER)
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

    private enum class KeyType { INT, LONG, STRING, BYTE_BUFFER }

    private fun copyKey(from: MediaFormat, to: MediaFormat, key: String, type: KeyType) {
        if (!from.containsKey(key)) return
        when (type) {
            KeyType.INT -> copyIntKey(from, to, key)
            KeyType.LONG -> copyLongKey(from, to, key)
            KeyType.STRING -> copyStringKey(from, to, key)
            KeyType.BYTE_BUFFER -> copyByteBufferKey(from, to, key)
        }
    }

    private fun copyIntKey(from: MediaFormat, to: MediaFormat, key: String) {
        try {
            val value = from.getInteger(key)
            if (value != null) {
                to.setInteger(key, value)
            }
        } catch (_: ClassCastException) {
            if (key == MediaFormat.KEY_FRAME_RATE) {
                try {
                    val floatValue = from.getFloat(key)
                    to.setFloat(key, floatValue)
                } catch (_: ClassCastException) {
                    Log.w(TAG, "Failed to copy frame rate as float")
                } catch (_: NullPointerException) {
                    Log.w(TAG, "Failed to copy frame rate as float")
                }
            }
        } catch (_: NullPointerException) {
            Log.w(TAG, "Null value for integer key $key")
        }
    }

    private fun copyLongKey(from: MediaFormat, to: MediaFormat, key: String) {
        try {
            val value = from.getLong(key)
            to.setLong(key, value)
        } catch (_: ClassCastException) {
            Log.w(TAG, "Type mismatch for long key $key")
        } catch (_: NullPointerException) {
            Log.w(TAG, "Null value for long key $key")
        }
    }

    private fun copyStringKey(from: MediaFormat, to: MediaFormat, key: String) {
        try {
            val value = from.getString(key)
            if (value != null) {
                to.setString(key, value)
            }
        } catch (_: ClassCastException) {
            Log.w(TAG, "Type mismatch for string key $key")
        } catch (_: NullPointerException) {
            Log.w(TAG, "Null value for string key $key")
        }
    }

    private fun copyByteBufferKey(from: MediaFormat, to: MediaFormat, key: String) {
        try {
            val value = from.getByteBuffer(key)
            if (value != null) {
                to.setByteBuffer(key, value)
            }
        } catch (_: ClassCastException) {
            Log.w(TAG, "Type mismatch for bytebuffer key $key")
        } catch (_: NullPointerException) {
            Log.w(TAG, "Null value for bytebuffer key $key")
        }
    }

    companion object {
        private const val TAG = "TrackInspector"
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB
        private const val MAX_CSD_INDEX = 10
    }
}
