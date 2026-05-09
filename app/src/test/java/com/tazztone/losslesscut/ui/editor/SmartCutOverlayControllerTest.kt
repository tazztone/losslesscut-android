package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.databinding.FragmentEditorBinding
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmartCutOverlayControllerTest {

    private lateinit var context: Context
    private lateinit var binding: FragmentEditorBinding
    private lateinit var viewModel: VideoEditingViewModel
    private val testScope = TestScope(UnconfinedTestDispatcher())

    private val uiStateFlow = MutableStateFlow<VideoEditingUiState>(
        VideoEditingUiState.Success(
            clips = emptyList(),
            keyframes = emptyList(),
            segments = emptyList(),
            isAudioOnly = false
        )
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.tazztone.losslesscut.R.style.AppTheme)

        binding = FragmentEditorBinding.inflate(LayoutInflater.from(context))

        // We have to mock the dialog_smart_cut inclusions as the real layout might need them
        // But FragmentEditorBinding will have smartCutOverlay included if it exists in layout.
        // Actually fragment_editor.xml has smartCutOverlay layout manually or via include.

        viewModel = mockk(relaxed = true)
        every { viewModel.uiState } returns uiStateFlow
    }

    @Test
    fun `show in portrait hides controls and sets overlay visible`() {
        context.resources.configuration.orientation = Configuration.ORIENTATION_PORTRAIT

        val controller = SmartCutOverlayController(context, testScope, binding, viewModel)

        controller.show()

        assertEquals(View.VISIBLE, binding.smartCutOverlay?.root?.visibility)
        assertEquals(View.GONE, binding.toolbar?.visibility)
        assertEquals(View.GONE, binding.editingControls.root.visibility)
        assertEquals(View.GONE, binding.playlistArea.root.visibility)
        assertEquals(View.GONE, binding.playerSection.btnPlayPause.visibility)

        assertFalse(binding.seekerContainer.customVideoSeeker.segmentsVisible)
        assertTrue(binding.seekerContainer.customVideoSeeker.playheadVisible)
        assertTrue(controller.isVisible())
    }

    @Test
    fun `show in landscape keeps controls visible but sets overlay visible`() {
        context.resources.configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

        val controller = SmartCutOverlayController(context, testScope, binding, viewModel)

        // ensure default is visible
        binding.toolbar?.visibility = View.VISIBLE
        binding.editingControls.root.visibility = View.VISIBLE

        controller.show()

        assertEquals(View.VISIBLE, binding.smartCutOverlay?.root?.visibility)
        assertEquals(View.VISIBLE, binding.toolbar?.visibility)
        assertEquals(View.VISIBLE, binding.editingControls.root.visibility)
        assertEquals(View.GONE, binding.playlistArea.root.visibility)
        assertEquals(View.GONE, binding.playerSection.btnPlayPause.visibility)
    }

    @Test
    fun `hide restores visibility and clears detections`() {
        context.resources.configuration.orientation = Configuration.ORIENTATION_PORTRAIT
        val controller = SmartCutOverlayController(context, testScope, binding, viewModel)

        controller.show()
        controller.hide()

        assertEquals(View.GONE, binding.smartCutOverlay?.root?.visibility)
        assertEquals(View.VISIBLE, binding.toolbar?.visibility)
        assertEquals(View.VISIBLE, binding.editingControls.root.visibility)
        assertEquals(View.VISIBLE, binding.playerSection.btnPlayPause.visibility)

        assertTrue(binding.seekerContainer.customVideoSeeker.segmentsVisible)
        assertTrue(binding.seekerContainer.customVideoSeeker.playheadVisible)

        verify { viewModel.clearSilencePreview() }
        verify { viewModel.cancelVisualDetection() }
        assertFalse(controller.isVisible())
    }

    @Test
    fun `setupTabs disables visual tab for audio only`() {
        uiStateFlow.value = VideoEditingUiState.Success(
            clips = emptyList(),
            keyframes = emptyList(),
            segments = emptyList(),
            isAudioOnly = true
        )

        val controller = SmartCutOverlayController(context, testScope, binding, viewModel)
        controller.show() // This calls setupTabs

        val tabLayout = binding.smartCutOverlay?.root?.findViewById<com.google.android.material.tabs.TabLayout>(com.tazztone.losslesscut.R.id.tabLayout)
        val visualTab = tabLayout?.getTabAt(1)?.view

        assertFalse(visualTab?.isEnabled ?: true)
        assertEquals(0.5f, visualTab?.alpha ?: 1f, 0.01f)
    }
}
