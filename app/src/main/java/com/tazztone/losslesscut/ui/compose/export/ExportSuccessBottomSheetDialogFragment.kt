package com.tazztone.losslesscut.ui.compose.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.TimeUtils
import com.tazztone.losslesscut.ui.compose.theme.LosslessCutTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ExportSuccessBottomSheetDialogFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_OUTPUT_URIS = "arg_output_uris"
        private const val ARG_IS_AUDIO_ONLY = "arg_is_audio_only"

        fun newInstance(
            outputUris: List<String>,
            isAudioOnly: Boolean
        ): ExportSuccessBottomSheetDialogFragment {
            return ExportSuccessBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_OUTPUT_URIS, ArrayList(outputUris))
                    putBoolean(ARG_IS_AUDIO_ONLY, isAudioOnly)
                }
            }
        }
    }

    private var itemsState by mutableStateOf<List<ExportedMediaItem>>(emptyList())
    private var isAudioOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriStrings = arguments?.getStringArrayList(ARG_OUTPUT_URIS).orEmpty()
        isAudioOnly = arguments?.getBoolean(ARG_IS_AUDIO_ONLY, false) ?: false

        // Initialize with basic items immediately
        itemsState = uriStrings.map { uriString ->
            val uri = Uri.parse(uriString)
            ExportedMediaItem(
                uri = uri,
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "export"
            )
        }

        // Load rich metadata and thumbnails in background
        loadMetadata(uriStrings)
    }

    private fun loadMetadata(uriStrings: List<String>) {
        viewLifecycleOwnerLiveData.observe(this) { owner ->
            if (owner == null) return@observe
            lifecycleScope.launch {
                val resolvedItems = withContext(Dispatchers.IO) {
                    uriStrings.map { uriStr ->
                        val uri = Uri.parse(uriStr)
                        resolveMediaItemDetails(requireContext(), uri, isAudioOnly)
                    }
                }
                itemsState = resolvedItems
            }
        }
    }

    private fun resolveMediaItemDetails(
        context: Context,
        uri: Uri,
        audioOnly: Boolean
    ): ExportedMediaItem {
        val (displayName, fileSize) = queryDisplayNameAndSize(context, uri)
        val durationMs = queryDurationMs(context, uri)
        val thumbnail = if (!audioOnly) loadThumbnailBitmap(context, uri) else null

        val formattedDuration = durationMs?.let { TimeUtils.formatDuration(it) }
        val formattedSize = fileSize?.let { Formatter.formatFileSize(context, it) }

        return ExportedMediaItem(
            uri = uri,
            fileName = displayName,
            formattedSize = formattedSize,
            formattedDuration = formattedDuration,
            thumbnailBitmap = thumbnail
        )
    }

    private fun queryDisplayNameAndSize(context: Context, uri: Uri): Pair<String, Long?> {
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "export"
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = try {
            context.contentResolver.query(uri, projection, null, null, null)
        } catch (_: Exception) {
            null
        } ?: return fallbackName to null

        return cursor.use { c ->
            if (!c.moveToFirst()) return@use fallbackName to null
            val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val name = if (nameIndex != -1) c.getString(nameIndex) ?: fallbackName else fallbackName
            val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
            val size = if (sizeIndex != -1 && !c.isNull(sizeIndex)) c.getLong(sizeIndex) else null
            name to size
        }
    }

    private fun queryDurationMs(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    private fun loadThumbnailBitmap(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
            } catch (_: Exception) {
                null
            }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            LosslessCutTheme {
                ExportSuccessScreen(
                    items = itemsState,
                    isAudioOnly = isAudioOnly,
                    onShareSingle = { uri -> shareSingleMedia(uri) },
                    onShareAll = { shareAllMedia() },
                    onOpenMedia = { uri -> openMedia(uri) },
                    onDoneHome = {
                        dismiss()
                        activity?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onContinueEditing = {
                        dismiss()
                    },
                    onScrollChanged = { scrollValue ->
                        val bottomSheet = (dialog as? BottomSheetDialog)
                            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                        if (bottomSheet != null) {
                            BottomSheetBehavior.from(bottomSheet).isDraggable = scrollValue == 0
                        }
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        BottomSheetBehavior.from(bottomSheet).apply {
            isFitToContents = true
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun shareSingleMedia(uri: Uri) {
        val mimeType = if (isAudioOnly) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, getString(R.string.share_media_chooser))
        startActivity(chooser)
    }

    private fun shareAllMedia() {
        val uris = itemsState.map { it.uri }
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            shareSingleMedia(uris.first())
            return
        }

        val mimeType = if (isAudioOnly) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, getString(R.string.share_media_chooser))
        startActivity(chooser)
    }

    private fun openMedia(uri: Uri) {
        val mimeType = if (isAudioOnly) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_open_file, Toast.LENGTH_SHORT).show()
        }
    }
}
