package com.tazztone.losslesscut.ui.editor

import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.databinding.EditorTimelineBinding
import com.tazztone.losslesscut.ui.PlayerManager
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import java.util.UUID

/**
 * Binds customVideoSeeker callbacks and provides a higher-level API for the fragment.
 */
@UnstableApi
class TimelineSeekerDelegate(
    private val binding: EditorTimelineBinding,
    private val viewModel: VideoEditingViewModel,
    private val playerManager: PlayerManager,
    private val onSeek: (Long) -> Unit,
    private val onDraggingChanged: (Boolean) -> Unit
) {
    fun setup() {
        binding.customVideoSeeker.onSeekStart = {
            onDraggingChanged(true)
        }
        binding.customVideoSeeker.onSeekEnd = {
            onDraggingChanged(false)
        }
        binding.customVideoSeeker.onSeekListener = { pos ->
            playerManager.seekTo(pos)
            onSeek(pos)
        }
        binding.customVideoSeeker.onSegmentSelected = { id -> viewModel.selectSegment(id) }
        binding.customVideoSeeker.onSegmentBoundsChanged = { id, start, end, seekPos ->
            onDraggingChanged(true)
            viewModel.updateSegmentBounds(id, start, end)
            playerManager.seekTo(seekPos)
            onSeek(seekPos)
        }
        binding.customVideoSeeker.onSegmentBoundsDragEnd = {
            onDraggingChanged(false)
            viewModel.commitSegmentBounds()
        }
    }

    fun setVideoDuration(durationMs: Long) {
        binding.customVideoSeeker.setVideoDuration(durationMs)
    }

    fun resetView() {
        binding.customVideoSeeker.resetView()
    }
}
