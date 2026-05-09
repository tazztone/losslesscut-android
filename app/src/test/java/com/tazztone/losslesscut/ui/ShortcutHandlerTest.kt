package com.tazztone.losslesscut.ui

import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import io.mockk.*
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

    private fun createHandler(launchMode: String): ShortcutHandler {
        return ShortcutHandler(
            viewModel = viewModel,
            playerManager = playerManager,
            launchMode = launchMode,
            onSplit = onSplit,
            onSetIn = onSetIn,
            onSetOut = onSetOut,
            onRestore = onRestore
        )
    }

    private fun mockKeyEvent(action: Int, keyCode: Int, isAltPressed: Boolean = false): KeyEvent {
        val event = mockk<KeyEvent>()
        every { event.action } returns action
        every { event.keyCode } returns keyCode
        every { event.isAltPressed } returns isAltPressed
        return event
    }

    @Test
    fun `handleKeyEvent returns false for ACTION_UP`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)
        assertFalse(handler.handleKeyEvent(event))
    }

    @Test
    fun `handleKeyEvent returns false for unhandled key`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        assertFalse(handler.handleKeyEvent(event))
    }

    @Test
    fun `handleKeyEvent SPACE toggles playback`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
        assertTrue(handler.handleKeyEvent(event))
        verify { playerManager.togglePlayback() }
    }

    @Test
    fun `handleKeyEvent DPAD_LEFT without alt seeks to keyframe`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, isAltPressed = false)
        assertTrue(handler.handleKeyEvent(event))
        verify { playerManager.seekToKeyframe(-1) }
    }

    @Test
    fun `handleKeyEvent DPAD_LEFT with alt performs nudge`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, isAltPressed = true)
        assertTrue(handler.handleKeyEvent(event))
        verify { playerManager.performNudge(-1) }
    }

    @Test
    fun `handleKeyEvent DPAD_RIGHT without alt seeks to keyframe`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, isAltPressed = false)
        assertTrue(handler.handleKeyEvent(event))
        verify { playerManager.seekToKeyframe(1) }
    }

    @Test
    fun `handleKeyEvent DPAD_RIGHT with alt performs nudge`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, isAltPressed = true)
        assertTrue(handler.handleKeyEvent(event))
        verify { playerManager.performNudge(1) }
    }

    @Test
    fun `handleKeyEvent S calls onSplit in MODE_CUT`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S)
        assertTrue(handler.handleKeyEvent(event))
        verify { onSplit() }
    }

    @Test
    fun `handleKeyEvent S does not call onSplit in MODE_REMUX`() {
        val handler = createHandler(VideoEditingActivity.MODE_REMUX)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S)
        assertTrue(handler.handleKeyEvent(event))
        verify(exactly = 0) { onSplit() }
    }

    @Test
    fun `handleKeyEvent I calls onSetIn in MODE_CUT`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_I)
        assertTrue(handler.handleKeyEvent(event))
        verify { onSetIn() }
    }

    @Test
    fun `handleKeyEvent I does not call onSetIn in MODE_REMUX`() {
        val handler = createHandler(VideoEditingActivity.MODE_REMUX)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_I)
        assertTrue(handler.handleKeyEvent(event))
        verify(exactly = 0) { onSetIn() }
    }

    @Test
    fun `handleKeyEvent O calls onSetOut in MODE_CUT`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_O)
        assertTrue(handler.handleKeyEvent(event))
        verify { onSetOut() }
    }

    @Test
    fun `handleKeyEvent O does not call onSetOut in MODE_REMUX`() {
        val handler = createHandler(VideoEditingActivity.MODE_REMUX)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_O)
        assertTrue(handler.handleKeyEvent(event))
        verify(exactly = 0) { onSetOut() }
    }

    @Test
    fun `handleKeyEvent R calls onRestore in MODE_CUT`() {
        val handler = createHandler(VideoEditingActivity.MODE_CUT)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R)
        assertTrue(handler.handleKeyEvent(event))
        verify { onRestore() }
    }

    @Test
    fun `handleKeyEvent R does not call onRestore in MODE_REMUX`() {
        val handler = createHandler(VideoEditingActivity.MODE_REMUX)
        val event = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R)
        assertTrue(handler.handleKeyEvent(event))
        verify(exactly = 0) { onRestore() }
    }
}
