package com.tazztone.losslesscut.ui

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import io.mockk.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val playerView = mockk<PlayerView>(relaxed = true)
    private val viewModel = mockk<VideoEditingViewModel>(relaxed = true)

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onIsPlayingChanged should not cause recursion`() {
        var callbackCalled = false
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel,
            onIsPlayingChanged = {
                callbackCalled = true
            }
        )

        // Access the private listener via reflection or by just triggering it if it was public
        // Since it's private, we can test the behavior by triggering the listener through mockk if possible,
        // but here we just want to ensure that the logic in PlayerManager doesn't have the infinite loop.
        
        // Let's use reflection to get the listener and call it
        val playerListenerField = PlayerManager::class.java.getDeclaredField("playerListener")
        playerListenerField.isAccessible = true
        val listener = playerListenerField.get(playerManager) as Player.Listener

        listener.onIsPlayingChanged(true)

        assert(callbackCalled) { "Callback should have been called" }
    }

    @Test
    fun `onStateChanged should trigger callback`() {
        var stateReceived = -1
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel,
            onStateChanged = { state ->
                stateReceived = state
            }
        )

        val playerListenerField = PlayerManager::class.java.getDeclaredField("playerListener")
        playerListenerField.isAccessible = true
        val listener = playerListenerField.get(playerManager) as Player.Listener

        listener.onPlaybackStateChanged(Player.STATE_READY)

        assert(stateReceived == Player.STATE_READY)
    }

    @Test
    fun `togglePlayback when player is null should not crash`() {
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel
        )
        // No crash means success
        playerManager.togglePlayback()
    }

    @Test
    fun `togglePlayback when state is ended should seek to 0 and play`() {
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel
        )
        val mockPlayer = mockk<androidx.media3.exoplayer.ExoPlayer>(relaxed = true)
        every { mockPlayer.playbackState } returns Player.STATE_ENDED

        val playerField = PlayerManager::class.java.getDeclaredField("player")
        playerField.isAccessible = true
        playerField.set(playerManager, mockPlayer)

        playerManager.togglePlayback()

        verify { mockPlayer.seekTo(0L) }
        verify { mockPlayer.play() }
    }

    @Test
    fun `togglePlayback when isPlaying is true should pause`() {
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel
        )
        val mockPlayer = mockk<androidx.media3.exoplayer.ExoPlayer>(relaxed = true)
        every { mockPlayer.playbackState } returns Player.STATE_READY
        every { mockPlayer.isPlaying } returns true

        val playerField = PlayerManager::class.java.getDeclaredField("player")
        playerField.isAccessible = true
        playerField.set(playerManager, mockPlayer)

        playerManager.togglePlayback()

        verify { mockPlayer.pause() }
        verify(exactly = 0) { mockPlayer.play() }
    }

    @Test
    fun `togglePlayback when isPlaying is false should play`() {
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel
        )
        val mockPlayer = mockk<androidx.media3.exoplayer.ExoPlayer>(relaxed = true)
        every { mockPlayer.playbackState } returns Player.STATE_READY
        every { mockPlayer.isPlaying } returns false

        val playerField = PlayerManager::class.java.getDeclaredField("player")
        playerField.isAccessible = true
        playerField.set(playerManager, mockPlayer)

        playerManager.togglePlayback()

        verify { mockPlayer.play() }
        verify(exactly = 0) { mockPlayer.pause() }
    }

    @Test
    fun `updatePlaybackSpeed should update properties, player parameters, and invoke callback`() {
        var callbackSpeed = -1f
        var callbackPitchCorrection = false
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel,
            onPlaybackParametersChanged = { speed, pitchCorrection ->
                callbackSpeed = speed
                callbackPitchCorrection = pitchCorrection
            }
        )

        val mockPlayer = mockk<androidx.media3.exoplayer.ExoPlayer>(relaxed = true)
        PlayerManager::class.java.getDeclaredField("player").apply {
            isAccessible = true
            set(playerManager, mockPlayer)
        }

        playerManager.updatePlaybackSpeed(2.0f, true)

        assert(playerManager.currentPlaybackSpeed == 2.0f) { "Speed should be updated" }
        assert(playerManager.isPitchCorrectionEnabled) { "Pitch correction should be enabled" }
        assert(callbackSpeed == 2.0f) { "Callback speed should be 2.0" }
        assert(callbackPitchCorrection) { "Callback pitch correction should be true" }

        val paramsSlot = slot<androidx.media3.common.PlaybackParameters>()
        verify { mockPlayer.playbackParameters = capture(paramsSlot) }
        assert(paramsSlot.captured.speed == 2.0f) { "Player playback parameters speed should be 2.0" }
        assert(paramsSlot.captured.pitch == 1.0f) { "Player playback parameters pitch should be 1.0 when pitch correction is true" }
    }

    @Test
    fun `updatePlaybackSpeed without pitch correction should set pitch equal to speed`() {
        var callbackSpeed = -1f
        var callbackPitchCorrection = true
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel,
            onPlaybackParametersChanged = { speed, pitchCorrection ->
                callbackSpeed = speed
                callbackPitchCorrection = pitchCorrection
            }
        )

        val mockPlayer = mockk<androidx.media3.exoplayer.ExoPlayer>(relaxed = true)
        PlayerManager::class.java.getDeclaredField("player").apply {
            isAccessible = true
            set(playerManager, mockPlayer)
        }

        playerManager.updatePlaybackSpeed(1.5f, false)

        assert(playerManager.currentPlaybackSpeed == 1.5f) { "Speed should be updated" }
        assert(!playerManager.isPitchCorrectionEnabled) { "Pitch correction should be disabled" }
        assert(callbackSpeed == 1.5f) { "Callback speed should be 1.5" }
        assert(!callbackPitchCorrection) { "Callback pitch correction should be false" }

        val paramsSlot = slot<androidx.media3.common.PlaybackParameters>()
        verify { mockPlayer.playbackParameters = capture(paramsSlot) }
        assert(paramsSlot.captured.speed == 1.5f) { "Player playback parameters speed should be 1.5" }
        assert(paramsSlot.captured.pitch == 1.5f) { "Player playback parameters pitch should be 1.5 when pitch correction is false" }
    }

    @Test
    fun `seekTo with positionMs delegates to player`() {
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel
        )
        val exoPlayer = mockk<ExoPlayer>(relaxed = true)
        val playerField = PlayerManager::class.java.getDeclaredField("player")
        playerField.isAccessible = true
        playerField.set(playerManager, exoPlayer)

        playerManager.seekTo(1500L)
        verify { exoPlayer.seekTo(1500L) }
    }

    @Test
    fun `seekTo with index and positionMs delegates to player`() {
        val playerManager = PlayerManager(
            context = context,
            playerView = playerView,
            viewModel = viewModel
        )
        val exoPlayer = mockk<ExoPlayer>(relaxed = true)
        val playerField = PlayerManager::class.java.getDeclaredField("player")
        playerField.isAccessible = true
        playerField.set(playerManager, exoPlayer)

        playerManager.seekTo(1, 2500L)
        verify { exoPlayer.seekTo(1, 2500L) }
    }
}
