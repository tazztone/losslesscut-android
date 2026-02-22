package com.tazztone.losslesscut.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.media.MediaFormat
import androidx.documentfile.provider.DocumentFile
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.data.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences
) {

    fun getFileName(uri: Uri): String {
        var name = "video.mp4"
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: "video.mp4"
                }
            }
        return name
    }


    suspend fun createMediaOutputUri(fileName: String, isAudioOnly: Boolean, suffix: String? = null): Uri? {
        val customUriString = preferences.customOutputUriFlow.first()
        if (customUriString != null) {
            val customUri = Uri.parse(customUriString)
            val parentDoc = DocumentFile.fromTreeUri(context, customUri)
            if (parentDoc != null && parentDoc.exists()) {
                val baseName = fileName.substringBeforeLast(".")
                val extension = fileName.substringAfterLast(".", "mp4")
                val finalFileName = if (suffix != null) "${baseName}${suffix}.${extension}" else fileName
                val mimeType = if (isAudioOnly) "audio/mp4" else "video/mp4"
                
                val newFile = parentDoc.createFile(mimeType, finalFileName)
                return newFile?.uri
            }
        }

        val resolver = context.contentResolver
        
        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "mp4")
        val finalFileName = if (suffix != null) "${baseName}${suffix}.${extension}" else fileName

        val mimeType = if (isAudioOnly) "audio/mp4" else "video/mp4"
        val collection = if (isAudioOnly) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        val relativePath = if (isAudioOnly) {
            Environment.DIRECTORY_MUSIC + "/LosslessCut"
        } else {
            Environment.DIRECTORY_MOVIES + "/LosslessCut"
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        return resolver.insert(collection, contentValues)
    }

    fun finalizeVideo(uri: Uri) {
        finalizeMedia(uri)
    }

    fun finalizeAudio(uri: Uri) {
        finalizeMedia(uri)
    }

    private fun finalizeMedia(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val updatedDetails = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, updatedDetails, null, null)
        }
    }

    suspend fun createImageOutputUri(fileName: String): Uri? {
        val customUriString = preferences.customOutputUriFlow.first()
        if (customUriString != null) {
            val customUri = Uri.parse(customUriString)
            val parentDoc = DocumentFile.fromTreeUri(context, customUri)
            if (parentDoc != null && parentDoc.exists()) {
                val mimeType = if (fileName.endsWith(".jpeg", ignoreCase = true) || fileName.endsWith(".jpg", ignoreCase = true)) "image/jpeg" else "image/png"
                val newFile = parentDoc.createFile(mimeType, fileName)
                return newFile?.uri
            }
        }

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

    fun finalizeImage(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val updatedDetails = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, updatedDetails, null, null)
        }
    }

    
    data class DetailedMetadata(
        val fileName: String,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val videoMime: String?,
        val audioMime: String?,
        val sampleRate: Int,
        val channelCount: Int,
        val fps: Float,
        val rotation: Int,
        val isAudioOnly: Boolean
    )

    fun getDetailedMetadata(uri: Uri): DetailedMetadata {
        var fileName = "video.mp4"
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(0) ?: "video.mp4"
                }
            }

        var durationMs = 0L
        var width = 0
        var height = 0
        var videoMime: String? = null
        var audioMime: String? = null
        var sampleRate = 0
        var channelCount = 0
        var fps = 0f
        var rotation = 0
        var isAudioOnly = true

        val extractor = android.media.MediaExtractor()
        val retriever = android.media.MediaMetadataRetriever()
        
        try {
            extractor.setDataSource(context, uri, null)
            retriever.setDataSource(context, uri)

            durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            val fpsStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            fps = fpsStr?.toFloat() ?: 0f

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                
                if (mime.startsWith("video/")) {
                    isAudioOnly = false
                    videoMime = mime
                    width = format.getInteger(android.media.MediaFormat.KEY_WIDTH)
                    height = format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                    if (format.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)) {
                        fps = format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE).toFloat()
                    }
                } else if (mime.startsWith("audio/")) {
                    audioMime = mime
                    sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    channelCount = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("StorageUtils", "Failed to extract detailed metadata: ${e.message}")
        } finally {
            extractor.release()
            retriever.release()
        }

        return DetailedMetadata(
            fileName, durationMs, width, height, videoMime, audioMime, 
            sampleRate, channelCount, fps, rotation, isAudioOnly
        )
    }

}
