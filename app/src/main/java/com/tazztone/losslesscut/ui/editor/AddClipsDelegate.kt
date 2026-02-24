package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.net.Uri
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Handles the logic for adding clips, including duplicate detection.
 */
class AddClipsDelegate(
    private val context: Context,
    private val viewModel: VideoEditingViewModel
) {
    fun onClipsReceived(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val currentState = viewModel.uiState.value
        if (currentState is VideoEditingUiState.Success) {
            val existingUris = currentState.clips.map { it.uri }.toSet()
            val duplicates = uris.filter { it.toString() in existingUris }
            val nonDuplicates = uris.filter { it.toString() !in existingUris }

            if (duplicates.isEmpty()) {
                viewModel.addClips(uris)
            } else {
                val message = if (uris.size == 1) {
                    context.getString(R.string.duplicate_files_msg)
                } else if (nonDuplicates.isEmpty()) {
                    context.getString(R.string.duplicate_files_msg)
                } else {
                    context.getString(R.string.duplicate_files_confirm_msg, duplicates.size)
                }

                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.add_video)
                    .setMessage(message)
                    .setPositiveButton(R.string.import_anyway) { _, _ ->
                        viewModel.addClips(uris)
                    }
                    .setNegativeButton(R.string.skip) { _, _ ->
                        if (nonDuplicates.isNotEmpty()) {
                            viewModel.addClips(nonDuplicates)
                        }
                    }
                    .setNeutralButton(android.R.string.cancel, null)
                    .show()
            }
        } else {
            viewModel.addClips(uris)
        }
    }
}
