package com.tazztone.losslesscut.ui

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    fun `togglePlayback when player is null should do nothing`() {
        val playerManager = PlayerManager(context, playerView, viewModel)
        // No player injected
        playerManager.togglePlayback()
        // No crash means success
    }

    @Test
    fun `togglePlayback when state is ENDED should seek to 0 and play`() {
        val playerManager = PlayerManager(context, playerView, viewModel)
        val mockPlayer = mockk<ExoPlayer>(relaxed = true)

        PlayerManager::class.java.getDeclaredField("player").apply {
            isAccessible = true
            set(playerManager, mockPlayer)
        }

        every { mockPlayer.playbackState } returns Player.STATE_ENDED

        playerManager.togglePlayback()

        verify { mockPlayer.seekTo(0) }
        verify { mockPlayer.play() }
    }

    @Test
    fun `togglePlayback when state is not ENDED and isPlaying is true should pause`() {
        val playerManager = PlayerManager(context, playerView, viewModel)
        val mockPlayer = mockk<ExoPlayer>(relaxed = true)

        PlayerManager::class.java.getDeclaredField("player").apply {
            isAccessible = true
            set(playerManager, mockPlayer)
        }

        every { mockPlayer.playbackState } returns Player.STATE_READY
        every { mockPlayer.isPlaying } returns true

        playerManager.togglePlayback()

        verify { mockPlayer.pause() }
        verify(exactly = 0) { mockPlayer.play() }
    }

    @Test
    fun `togglePlayback when state is not ENDED and isPlaying is false should play`() {
        val playerManager = PlayerManager(context, playerView, viewModel)
        val mockPlayer = mockk<ExoPlayer>(relaxed = true)

        PlayerManager::class.java.getDeclaredField("player").apply {
            isAccessible = true
            set(playerManager, mockPlayer)
        }

        every { mockPlayer.playbackState } returns Player.STATE_READY
        every { mockPlayer.isPlaying } returns false

        playerManager.togglePlayback()

        verify { mockPlayer.play() }
        verify(exactly = 0) { mockPlayer.pause() }
    }
}
