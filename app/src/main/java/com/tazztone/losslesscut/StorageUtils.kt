package com.tazztone.losslesscut

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object StorageUtils {

    fun createVideoOutputUri(context: Context, fileName: String): Uri? {
        val resolver = context.contentResolver
        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val newVideoDetails = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LosslessCut")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        return resolver.insert(videoCollection, newVideoDetails)
    }

    fun finalizeVideo(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val updatedVideoDetails = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            resolver.update(uri, updatedVideoDetails, null, null)
        }
    }

    fun getVideoMetadata(context: Context, videoUri: Uri): Pair<String, Long> {
        var fileName = "video.mp4"
        var durationMs = 0L
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            fileName = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: videoUri.lastPathSegment ?: "video.mp4"
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLong() ?: 0
        } catch (t: Throwable) {
            android.util.Log.w("StorageUtils", "Failed to extract metadata: ${t.message}")
        } finally {
            retriever.release()
        }
        return Pair(fileName, durationMs)
    }
}
