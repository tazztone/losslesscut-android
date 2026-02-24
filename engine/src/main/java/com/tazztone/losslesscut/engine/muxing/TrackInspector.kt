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
                
                trackMap[i] = muxerWriter.addTrack(format)
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

    companion object {
        private const val TAG = "TrackInspector"
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB
    }
}
