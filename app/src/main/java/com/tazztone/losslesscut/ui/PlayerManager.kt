package com.tazztone.losslesscut.ui

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.ActivityVideoEditingBinding
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel

@UnstableApi
class PlayerManager(
    private val context: Context,
    private val playerView: androidx.media3.ui.PlayerView,
    private val viewModel: VideoEditingViewModel,
    private val onStateChanged: (Int) -> Unit = {},
    private val onMediaTransition: (Int) -> Unit = {},
    private val onIsPlayingChanged: (Boolean) -> Unit = {},
    private val onSpeedChanged: (Float) -> Unit = {}
) {
    var player: ExoPlayer? = null
        private set

    val playbackSpeeds = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 4.0f)
    var currentPlaybackSpeed = 1.0f
        private set

    var isPitchCorrectionEnabled = true

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = currentMediaItemIndex
            viewModel.selectClip(index)
            onMediaTransition(index)
        }

        override fun onPlaybackStateChanged(state: Int) {
            onStateChanged(state)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onIsPlayingChanged(isPlaying)
        }
    }

    fun initialize() {
        player = ExoPlayer.Builder(context).build().apply {
            playerView.player = this
            addListener(playerListener)
        }
    }

    fun release() {
        player?.apply {
            removeListener(playerListener)
            release()
        }
        player = null
    }

    fun setMediaItems(uris: List<Uri>, initialIndex: Int = 0, initialPosition: Long = 0, playWhenReady: Boolean = false) {
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
                it.seekTo(0)
                it.play()
            } else {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
    }

    fun performNudge(direction: Int) {
        player?.let {
            val delta = 100L * direction // 100ms nudge
            it.seekTo(it.currentPosition + delta)
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun seekTo(index: Int, positionMs: Long) {
        player?.seekTo(index, positionMs)
    }

    fun updatePlaybackSpeed(speed: Float, isPitchCorrectionEnabled: Boolean) {
        this.currentPlaybackSpeed = speed
        this.isPitchCorrectionEnabled = isPitchCorrectionEnabled
        val params = androidx.media3.common.PlaybackParameters(speed, if (isPitchCorrectionEnabled) 1.0f else speed)
        player?.playbackParameters = params
        onSpeedChanged(speed)
    }

    fun cyclePlaybackSpeed() {
        val nextIdx = (playbackSpeeds.indexOf(currentPlaybackSpeed) + 1) % playbackSpeeds.size
        updatePlaybackSpeed(playbackSpeeds[nextIdx], isPitchCorrectionEnabled)
    }

    fun setSeekParameters(params: androidx.media3.exoplayer.SeekParameters) {
        player?.setSeekParameters(params)
    }

    fun moveMediaItem(from: Int, to: Int) {
        player?.moveMediaItem(from, to)
    }

    fun removeMediaItem(index: Int) {
        player?.removeMediaItem(index)
    }

    fun seekToPrevious() {
        player?.seekToPrevious()
    }

    fun seekToNext() {
        player?.seekToNext()
    }

    fun pause() {
        player?.pause()
    }

    fun play() {
        player?.play()
    }

    val isPlaying: Boolean get() = player?.isPlaying ?: false
    val duration: Long get() = player?.duration ?: 0L
    val currentPosition: Long get() = player?.currentPosition ?: 0L
    val currentMediaItemIndex: Int get() = player?.currentMediaItemIndex ?: 0
}
