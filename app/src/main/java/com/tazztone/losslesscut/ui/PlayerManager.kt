package com.tazztone.losslesscut.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.tazztone.losslesscut.viewmodel.*

@OptIn(UnstableApi::class)
class PlayerManager(
    private val context: Context,
    private val playerView: androidx.media3.ui.PlayerView,
    private val viewModel: VideoEditingViewModel,
    onStateChanged: (Int) -> Unit = {},
    onMediaTransition: (Int) -> Unit = {},
    onIsPlayingChanged: (Boolean) -> Unit = {},
    onPlaybackParametersChanged: (Float, Boolean) -> Unit = { _, _ -> }
) {
    private val onStateChangedCallback = onStateChanged
    private val onMediaTransitionCallback = onMediaTransition
    private val onIsPlayingChangedCallback = onIsPlayingChanged
    private val onPlaybackParametersChangedCallback = onPlaybackParametersChanged
    private var onFrameStepRequestedCallback: (Long) -> Unit = {}

    // ponytail: input-rate limiting is the initial ceiling; add decoder-aware
    // coalescing only if device profiling proves it necessary.
    private var pendingFrameIndex: Long? = null

    var player: ExoPlayer? = null
        private set

    val playbackSpeeds = listOf(0.25f, 0.5f, 1.0f, 2.0f, 4.0f)
    var currentPlaybackSpeed = 1.0f
        private set

    var isPitchCorrectionEnabled = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            pendingFrameIndex = null
            val index = currentMediaItemIndex
            viewModel.selectClip(index)
            onMediaTransitionCallback(index)
        }

        override fun onPlaybackStateChanged(state: Int) {
            onStateChangedCallback(state)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) pendingFrameIndex = null
            onIsPlayingChangedCallback(isPlaying)
        }
    }

    fun initialize() {
        player = ExoPlayer.Builder(context).build().apply {
            playerView.player = this
            addListener(playerListener)
        }
    }

    fun setOnFrameStepRequested(callback: (Long) -> Unit) {
        onFrameStepRequestedCallback = callback
    }

    fun release() {
        pendingFrameIndex = null
        player?.apply {
            removeListener(playerListener)
            release()
        }
        player = null
    }

    fun seekToKeyframe(direction: Int) {
        pendingFrameIndex = null
        val currentPos = currentPosition
        val state = viewModel.uiState.value as? VideoEditingUiState.Success
        val keyframes = state?.keyframes ?: emptyList()

        val target = if (direction > 0) {
            keyframes.firstOrNull { it > currentPos + 10 }
        } else {
            keyframes.lastOrNull { it < currentPos - 10 }
        }

        if (target != null) {
            seekTo(target)
        }
    }

    fun seekToFrame(direction: Int): Boolean {
        val player = player ?: return false
        val state = viewModel.uiState.value as? VideoEditingUiState.Success
        if (state?.isAudioOnly == true) return false

        val fps = (state?.videoFps ?: 30f).takeIf { it > 0f } ?: 30f
        val currentPos = player.currentPosition
        val currentFrameIndex = pendingFrameIndex
            ?: Math.round(currentPos * fps / 1000.0)
        val targetFrameIndex = (currentFrameIndex + direction).coerceAtLeast(0)
        val unboundedTargetMs = Math.round(targetFrameIndex * 1000.0 / fps)
        val targetMs = if (player.duration > 0L) {
            unboundedTargetMs.coerceAtMost(player.duration)
        } else {
            unboundedTargetMs
        }
        val effectiveFrameIndex = Math.round(targetMs * fps / 1000.0)
        val currentLogicalPosition = pendingFrameIndex?.let {
            Math.round(it * 1000.0 / fps)
        } ?: currentPos

        if (targetMs == currentLogicalPosition) return false

        pendingFrameIndex = effectiveFrameIndex
        if (player.isPlaying) player.pause()
        onFrameStepRequestedCallback(targetMs)
        player.seekTo(targetMs)
        return true
    }

    fun selectAudioTrack(trackIndex: Int) {
        val player = player ?: return
        val (trackGroup, subIndex) = findAudioTrackTarget(player, trackIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(
                    trackGroup,
                    listOf(subIndex)
                )
            )
            .build()
    }

    private fun findAudioTrackTarget(
        player: Player,
        targetOrdinal: Int
    ): Pair<androidx.media3.common.TrackGroup, Int>? {
        var ordinal = 0
        for (group in player.currentTracks.groups) {
            if (group.type != androidx.media3.common.C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                if (ordinal++ == targetOrdinal) {
                    return group.mediaTrackGroup to i
                }
            }
        }
        return null
    }

    fun setMediaItems(uris: List<Uri>, initialIndex: Int = 0, initialPosition: Long = 0, playWhenReady: Boolean = false) {
        pendingFrameIndex = null
        val mediaItems = uris.map { MediaItem.fromUri(it) }
        player?.apply {
            setMediaItems(mediaItems)
            prepare()
            this.playWhenReady = playWhenReady
            seekTo(initialIndex, initialPosition)
        }
    }

    fun togglePlayback() {
        player?.let {
            if (it.playbackState == Player.STATE_ENDED) {
                pendingFrameIndex = null
                it.seekTo(0)
                it.play()
            } else {
                if (it.isPlaying) {
                    it.pause()
                } else {
                    pendingFrameIndex = null
                    it.play()
                }
            }
        }
    }

    fun performNudge(direction: Int) {
        pendingFrameIndex = null
        player?.let {
            val delta = 100L * direction // 100ms nudge
            it.seekTo(it.currentPosition + delta)
        }
    }

    fun seekTo(positionMs: Long) {
        pendingFrameIndex = null
        player?.seekTo(positionMs)
    }

    fun seekTo(index: Int, positionMs: Long) {
        pendingFrameIndex = null
        player?.seekTo(index, positionMs)
    }

    fun updatePlaybackSpeed(speed: Float, isPitchCorrectionEnabled: Boolean) {
        this.currentPlaybackSpeed = speed
        this.isPitchCorrectionEnabled = isPitchCorrectionEnabled
        val params = androidx.media3.common.PlaybackParameters(speed, if (isPitchCorrectionEnabled) 1.0f else speed)
        player?.playbackParameters = params
        onPlaybackParametersChangedCallback(speed, isPitchCorrectionEnabled)
    }

    fun cyclePlaybackSpeed() {
        val nextIdx = (playbackSpeeds.indexOf(currentPlaybackSpeed) + 1) % playbackSpeeds.size
        updatePlaybackSpeed(playbackSpeeds[nextIdx], isPitchCorrectionEnabled)
    }

    fun togglePitchCorrection(): Boolean {
        isPitchCorrectionEnabled = !isPitchCorrectionEnabled
        updatePlaybackSpeed(currentPlaybackSpeed, isPitchCorrectionEnabled)
        return isPitchCorrectionEnabled
    }

    fun setSeekParameters(params: androidx.media3.exoplayer.SeekParameters) {
        player?.setSeekParameters(params)
    }

    fun moveMediaItem(from: Int, to: Int) {
        pendingFrameIndex = null
        player?.moveMediaItem(from, to)
    }

    fun removeMediaItem(index: Int) {
        pendingFrameIndex = null
        player?.removeMediaItem(index)
    }

    fun seekToPrevious() {
        pendingFrameIndex = null
        player?.seekToPrevious()
    }

    fun seekToNext() {
        pendingFrameIndex = null
        player?.seekToNext()
    }

    fun pause() {
        player?.pause()
    }

    fun play() {
        pendingFrameIndex = null
        player?.play()
    }

    val isPlaying: Boolean get() = player?.isPlaying ?: false
    val duration: Long get() = player?.duration ?: 0L
    val currentPosition: Long get() = player?.currentPosition ?: 0L
    val currentMediaItemIndex: Int get() = player?.currentMediaItemIndex ?: 0
}
