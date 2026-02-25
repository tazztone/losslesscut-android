package com.tazztone.losslesscut.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel

@UnstableApi
abstract class BaseEditingFragment(@LayoutRes layoutId: Int) : Fragment(layoutId) {
    protected val viewModel: VideoEditingViewModel by activityViewModels()
    protected lateinit var playerManager: PlayerManager
    
    // Abstract because each fragment has its own binding type
    abstract fun getPlayerView(): androidx.media3.ui.PlayerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playerManager.release()
    }

    @Suppress("ThrowsCount", "UseRequire")
    protected fun showErrorDialog(message: String) {
        val ctx = context ?: return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(com.tazztone.losslesscut.R.string.error_incompatible_format_title)
            .setMessage(message)
            .setPositiveButton(com.tazztone.losslesscut.R.string.ok) { _, _ ->
                activity?.finish()
            }
            .setCancelable(false)
            .show()
    }
}
