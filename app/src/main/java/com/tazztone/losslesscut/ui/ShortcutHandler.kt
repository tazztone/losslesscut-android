package com.tazztone.losslesscut.ui

import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel

@UnstableApi
class ShortcutHandler(
    private val viewModel: VideoEditingViewModel,
    private val playerManager: PlayerManager,
    private val launchMode: String,
    private val onSplit: () -> Unit,
    private val onSetIn: () -> Unit,
    private val onSetOut: () -> Unit,
    private val onRestore: () -> Unit
) {
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_SPACE -> {
                    playerManager.togglePlayback()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.isAltPressed) {
                        playerManager.performNudge(-1)
                    } else {
                        playerManager.seekToKeyframe(-1)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.isAltPressed) {
                        playerManager.performNudge(1)
                    } else {
                        playerManager.seekToKeyframe(1)
                    }
                    return true
                }
                KeyEvent.KEYCODE_S -> {
                    if (launchMode == VideoEditingActivity.MODE_CUT) onSplit()
                    return true
                }
                KeyEvent.KEYCODE_I -> {
                    if (launchMode == VideoEditingActivity.MODE_CUT) onSetIn()
                    return true
                }
                KeyEvent.KEYCODE_O -> {
                    if (launchMode == VideoEditingActivity.MODE_CUT) onSetOut()
                    return true
                }
                KeyEvent.KEYCODE_R -> {
                    if (launchMode == VideoEditingActivity.MODE_CUT) onRestore()
                    return true
                }
            }
        }
        return false
    }
}
