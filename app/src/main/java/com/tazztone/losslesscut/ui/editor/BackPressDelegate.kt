package com.tazztone.losslesscut.ui.editor

import android.content.Context
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles the back-press confirmation logic when there are unsaved changes.
 */
class BackPressDelegate(
    private val context: Context,
    private val isDirty: StateFlow<Boolean>,
    private val onConfirmExit: () -> Unit
) {
    fun handleBackPress(): Boolean {
        if (isDirty.value) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.exit_confirm_title)
                .setMessage(R.string.exit_confirm_message)
                .setPositiveButton(R.string.exit_confirm_discard) { _, _ ->
                    onConfirmExit()
                }
                .setNegativeButton(R.string.exit_confirm_keep_editing, null)
                .show()
            return true // handled
        }
        return false // not handled, let standard back press proceed
    }
}
