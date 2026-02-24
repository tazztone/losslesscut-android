package com.tazztone.losslesscut.engine.muxing

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer

/**
 * Wraps MediaMuxer lifecycle and centralizes the "stop only if started" pattern.
 */
class MuxerWriter(private val muxer: MediaMuxer) {

    private var isStarted = false

    fun addTrack(format: MediaFormat): Int {
        check(!isStarted) { "Cannot add track after muxer started" }
        return muxer.addTrack(format)
    }

    fun setOrientationHint(degrees: Int) {
        check(!isStarted) { "Cannot set orientation after muxer started" }
        muxer.setOrientationHint(degrees)
    }

    fun start() {
        if (!isStarted) {
            muxer.start()
            isStarted = true
        }
    }

    fun writeSampleData(trackIndex: Int, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        check(isStarted) { "Muxer must be started before writing sample data" }
        muxer.writeSampleData(trackIndex, byteBuffer, bufferInfo)
    }

    fun stopAndRelease() {
        try {
            if (isStarted) {
                muxer.stop()
                isStarted = false
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Muxer stop failed, likely already stopped or released", e)
        } finally {
            try {
                muxer.release()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Muxer release failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "MuxerWriter"
    }
}
