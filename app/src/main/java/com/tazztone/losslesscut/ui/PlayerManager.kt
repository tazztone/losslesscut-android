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
    private val binding: ActivityVideoEditingBinding,
    private val viewModel: VideoEditingViewModel,
    private val listener: Player.Listener
) {
    var player: ExoPlayer? = null
        private set

    fun initialize() {
        player = ExoPlayer.Builder(context).build().apply {
            binding.playerView.player = this
            addListener(listener)
        }
    }

    fun release() {
        player?.apply {
            removeListener(listener)
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

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun seekTo(index: Int, positionMs: Long) {
        player?.seekTo(index, positionMs)
    }

    fun updatePlaybackSpeed(speed: Float, isPitchCorrectionEnabled: Boolean) {
        val params = androidx.media3.common.PlaybackParameters(speed, if (isPitchCorrectionEnabled) 1.0f else speed)
        player?.playbackParameters = params
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
