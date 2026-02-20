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

    fun createImageOutputUri(context: Context, fileName: String): Uri? {
        val resolver = context.contentResolver
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val mimeType = if (fileName.endsWith(".jpeg", ignoreCase = true) || fileName.endsWith(".jpg", ignoreCase = true)) "image/jpeg" else "image/png"

        val newImageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LosslessCut")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        return resolver.insert(imageCollection, newImageDetails)
    }

    fun finalizeImage(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val updatedDetails = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, updatedDetails, null, null)
        }
    }

    data class VideoMetadata(val fileName: String, val durationMs: Long)
    
    fun getVideoMetadata(context: Context, videoUri: Uri): VideoMetadata {
        var fileName = "video.mp4"
        context.contentResolver.query(videoUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(0) ?: "video.mp4"
                }
            }

        var durationMs = 0L
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLong() ?: 0
        } catch (e: Exception) {
            android.util.Log.w("StorageUtils", "Failed to extract metadata: ${e.message}")
        } finally {
            retriever.release()
        }
        return VideoMetadata(fileName, durationMs)
    }
}
