package com.tazztone.losslesscut.ui.editor

import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.ui.PlayerManager
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import java.util.UUID

/**
 * Binds customVideoSeeker callbacks and provides a higher-level API for the fragment.
 */
@UnstableApi
class TimelineSeekerDelegate(
    private val seeker: CustomVideoSeeker,
    private val viewModel: VideoEditingViewModel,
    private val playerManager: PlayerManager,
    private val onSeek: (Long) -> Unit,
    private val onDraggingChanged: (Boolean) -> Unit
) {
    fun setup() {
        seeker.onSeekStart = { onDraggingChanged(true) }
        seeker.onSeekEnd = { onDraggingChanged(false) }
        seeker.onSeekListener = { pos ->
            playerManager.seekTo(pos)
            onSeek(pos)
        }
        seeker.onSegmentSelected = { id -> viewModel.selectSegment(id) }
        seeker.onSegmentBoundsChanged = { id, start, end, seekPos ->
            onDraggingChanged(true)
            viewModel.updateSegmentBounds(id, start, end)
            playerManager.seekTo(seekPos)
            onSeek(seekPos)
        }
        seeker.onSegmentBoundsDragEnd = {
            onDraggingChanged(false)
            viewModel.commitSegmentBounds()
        }
    }

    fun setVideoDuration(durationMs: Long) {
        seeker.setVideoDuration(durationMs)
    }

    fun resetView() {
        seeker.resetView()
    }
}
