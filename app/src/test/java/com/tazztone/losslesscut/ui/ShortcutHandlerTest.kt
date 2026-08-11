package com.tazztone.losslesscut.ui

import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShortcutHandlerTest {

    private val viewModel: VideoEditingViewModel = mockk(relaxed = true)
    private val playerManager: PlayerManager = mockk(relaxed = true)
    private val onSplit: () -> Unit = mockk(relaxed = true)
    private val onSetIn: () -> Unit = mockk(relaxed = true)
    private val onSetOut: () -> Unit = mockk(relaxed = true)
    private val onRestore: () -> Unit = mockk(relaxed = true)

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createHandler() = ShortcutHandler(
        viewModel = viewModel,
        playerManager = playerManager,
        onSplit = onSplit,
        onSetIn = onSetIn,
        onSetOut = onSetOut,
        onRestore = onRestore
    )

    private fun mockKeyEvent(action: Int, keyCode: Int, isAltPressed: Boolean = false): KeyEvent {
        val event = mockk<KeyEvent>()
        every { event.action } returns action
        every { event.keyCode } returns keyCode
        every { event.isAltPressed } returns isAltPressed
        return event
    }

    @Test
    fun `handleKeyEvent returns false for ACTION_UP`() {
        assertFalse(createHandler().handleKeyEvent(mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)))
    }

    @Test
    fun `handleKeyEvent returns false for unhandled key`() {
        assertFalse(createHandler().handleKeyEvent(mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
    }

    @Test
    fun `handleKeyEvent SPACE toggles playback`() {
        assertTrue(createHandler().handleKeyEvent(mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)))
        verify { playerManager.togglePlayback() }
    }

    @Test
    fun `handleKeyEvent DPAD_LEFT without alt seeks to keyframe`() {
        assertTrue(createHandler().handleKeyEvent(mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
        verify { playerManager.seekToKeyframe(-1) }
    }

    @Test
    fun `handleKeyEvent DPAD_LEFT with alt performs nudge`() {
        assertTrue(
            createHandler().handleKeyEvent(
                mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, isAltPressed = true)
            )
        )
        verify { playerManager.performNudge(-1) }
    }

    @Test
    fun `handleKeyEvent DPAD_RIGHT without alt seeks to keyframe`() {
        assertTrue(createHandler().handleKeyEvent(mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify { playerManager.seekToKeyframe(1) }
    }

    @Test
    fun `handleKeyEvent DPAD_RIGHT with alt performs nudge`() {
        assertTrue(
            createHandler().handleKeyEvent(
                mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, isAltPressed = true)
            )
        )
        verify { playerManager.performNudge(1) }
    }

    @Test
    fun `handleKeyEvent S calls onSplit`() {
        assertTrue(createHandler().handleKeyEvent(mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S)))
        verify { onSplit() }
    }

    @Test
    fun `handleKeyEvent I calls onSetIn`() {
        assertTrue(createHandler().handleKeyEvent(mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_I)))
        verify { onSetIn() }
    }

    @Test
    fun `handleKeyEvent O calls onSetOut`() {
        assertTrue(createHandler().handleKeyEvent(mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_O)))
        verify { onSetOut() }
    }

    @Test
    fun `handleKeyEvent R calls onRestore`() {
        assertTrue(createHandler().handleKeyEvent(mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R)))
        verify { onRestore() }
    }
}
