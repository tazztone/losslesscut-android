package com.tazztone.losslesscut.ui.editor

import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.databinding.EditorTimelineBinding
import com.tazztone.losslesscut.ui.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the progress ticker job that updates the seeker UI during playback.
 */
@UnstableApi
class PlaybackProgressTicker(
    private val scope: CoroutineScope,
    private val binding: EditorTimelineBinding,
    private val playerManager: PlayerManager,
    private val onUpdate: (Long, Long) -> Unit
) {
    private var updateJob: Job? = null
    var isDraggingTimeline = false

    companion object {
        private const val UPDATE_DELAY_MS = 16L
    }

    fun start() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                if (playerManager.isPlaying && !isDraggingTimeline) {
                    val pos = playerManager.currentPosition
                    val duration = playerManager.duration
                    binding.customVideoSeeker.setSeekPosition(pos)
                    onUpdate(pos, duration)
                }
                delay(UPDATE_DELAY_MS)
            }
        }
    }

    fun stop() {
        updateJob?.cancel()
        val pos = playerManager.currentPosition
        val duration = playerManager.duration
        binding.customVideoSeeker.setSeekPosition(pos)
        onUpdate(pos, duration)
    }
}
