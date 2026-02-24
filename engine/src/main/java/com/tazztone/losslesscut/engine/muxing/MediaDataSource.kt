package com.tazztone.losslesscut.engine.muxing

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.IOException

/**
 * Handles setting data sources for MediaExtractor and MediaMetadataRetriever,
 * dealing with Android SAF Uri challenges.
 */
class MediaDataSource(private val context: Context) {

    fun setExtractorSource(extractor: MediaExtractor, uriString: String) {
        val uri = Uri.parse(uriString)
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: IOException) {
            Log.e(TAG, "MediaExtractor failed for $uri, trying FileDescriptor", e)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw IOException("Could not open FileDescriptor for $uri", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid URI for MediaExtractor: $uri", e)
            throw e
        }
    }

    fun setRetrieverSource(retriever: MediaMetadataRetriever, uriString: String) {
        val uri = Uri.parse(uriString)
        try {
            retriever.setDataSource(context, uri)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "MediaMetadataRetriever failed for $uri, trying FileDescriptor", e)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            } ?: throw IOException("Could not open FileDescriptor for $uri", e)
        }
    }

    companion object {
        private const val TAG = "MediaDataSource"
    }
}
