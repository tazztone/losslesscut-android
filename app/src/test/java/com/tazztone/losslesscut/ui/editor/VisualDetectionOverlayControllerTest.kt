package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.domain.model.VisualStrategy
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VisualDetectionOverlayControllerTest {

    private lateinit var context: Context
    private lateinit var root: View
    private lateinit var viewModel: VideoEditingViewModel
    private lateinit var seeker: CustomVideoSeeker
    private val testScope = TestScope()

    private lateinit var uiStateFlow: MutableStateFlow<VideoEditingUiState>
    private lateinit var visualDetectionProgressFlow: MutableStateFlow<Pair<Int, Int>?>

    private lateinit var controller: VisualDetectionOverlayController
    private var dismissCalled = false

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.AppTheme)

        root = LayoutInflater.from(context).inflate(R.layout.dialog_visual_detection, null, false)

        viewModel = mockk(relaxed = true)
        seeker = mockk(relaxed = true)

        uiStateFlow = MutableStateFlow(VideoEditingUiState.Initial)
        visualDetectionProgressFlow = MutableStateFlow(null)

        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.visualDetectionProgress } returns visualDetectionProgressFlow

        dismissCalled = false
        val onDismiss: () -> Unit = { dismissCalled = true }

        controller = VisualDetectionOverlayController(
            context = context,
            scope = testScope,
            viewModel = viewModel,
            seeker = seeker,
            root = root,
            onDismiss = onDismiss
        )
    }

    @Test
    fun `activate_initializesState`() {
        controller.activate()

        val tvDetectedStatus = root.findViewById<TextView>(R.id.tvDetectedStatus)
        assertEquals(context.getString(R.string.no_silence_detected), tvDetectedStatus.text.toString())
    }

    @Test
    fun `deactivate_cancelsOperations`() {
        controller.activate()
        controller.deactivate()

        verify { viewModel.cancelVisualDetection() }
        verify { seeker.visualStrategy = null }
    }

    @Test
    fun `btnSceneChange_click_updatesStrategy`() {
        controller.activate()
        val btnSceneChange = root.findViewById<View>(R.id.btnSceneChange)

        val btnBlackFrames = root.findViewById<View>(R.id.btnBlackFrames)
        btnBlackFrames.performClick()

        btnSceneChange.performClick()

        verify { seeker.visualStrategy = VisualStrategy.SCENE_CHANGE }

        val tvSensitivityLabel = root.findViewById<TextView>(R.id.tvSensitivityLabel)
        assertEquals(context.getString(R.string.sensitivity), tvSensitivityLabel.text.toString())
    }

    @Test
    fun `btnBlackFrames_click_updatesStrategy`() {
        controller.activate()
        val btnBlackFrames = root.findViewById<View>(R.id.btnBlackFrames)

        btnBlackFrames.performClick()

        verify { seeker.visualStrategy = VisualStrategy.BLACK_FRAMES }

        val tvSensitivityLabel = root.findViewById<TextView>(R.id.tvSensitivityLabel)
        assertEquals(context.getString(R.string.luma_threshold), tvSensitivityLabel.text.toString())
    }

    @Test
    fun `btnFreezeFrame_click_updatesStrategy`() {
        controller.activate()
        val btnFreezeFrame = root.findViewById<View>(R.id.btnFreezeFrame)

        btnFreezeFrame.performClick()

        verify { seeker.visualStrategy = VisualStrategy.FREEZE_FRAME }

        val tvSensitivityLabel = root.findViewById<TextView>(R.id.tvSensitivityLabel)
        assertEquals(context.getString(R.string.diff_threshold), tvSensitivityLabel.text.toString())
    }

    @Test
    fun `btnBlurQuality_click_updatesStrategy`() {
        controller.activate()
        val btnBlurQuality = root.findViewById<View>(R.id.btnBlurQuality)

        btnBlurQuality.performClick()

        verify { seeker.visualStrategy = VisualStrategy.BLUR_QUALITY }

        val tvSensitivityLabel = root.findViewById<TextView>(R.id.tvSensitivityLabel)
        assertEquals(context.getString(R.string.blur_threshold), tvSensitivityLabel.text.toString())
    }

    @Test
    fun `btnDetectAction_startsDetection_whenNotAnalyzing`() {
        controller.activate()
        val btnDetectAction = root.findViewById<MaterialButton>(R.id.btnDetectAction)

        visualDetectionProgressFlow.value = null
        btnDetectAction.performClick()

        verify { viewModel.previewVisualSegments(any()) }
    }

    @Test
    fun `btnDetectAction_cancelsDetection_whenAnalyzing`() {
        controller.activate()
        val btnDetectAction = root.findViewById<MaterialButton>(R.id.btnDetectAction)

        visualDetectionProgressFlow.value = Pair(10, 100)
        btnDetectAction.performClick()

        verify { viewModel.cancelVisualDetection() }
    }

    @Test
    fun `btnCancelVisual_callsOnDismiss`() {
        controller.activate()
        val btnCancelVisual = root.findViewById<MaterialButton>(R.id.btnCancelVisual)

        btnCancelVisual.performClick()

        assertTrue(dismissCalled)
    }

    @Test
    fun `btnApplyVisual_callsViewModelAndOnDismiss`() {
        controller.activate()
        val btnApplyVisual = root.findViewById<MaterialButton>(R.id.btnApplyVisual)

        btnApplyVisual.performClick()

        verify { viewModel.applyDetection(any()) }
        assertTrue(dismissCalled)
    }

    @Test
    fun `observeState_updatesDetectedStatus_whenSuccess`() {
        controller.activate()
        val tvDetectedStatus = root.findViewById<TextView>(R.id.tvDetectedStatus)
        val btnApplyVisual = root.findViewById<MaterialButton>(R.id.btnApplyVisual)

        val ranges = listOf(0L..1000L, 2000L..3000L) // 2 ranges, total 2000ms
        uiStateFlow.value = VideoEditingUiState.Success(
            clips = emptyList(),
            keyframes = emptyList(),
            segments = emptyList(),
            detectionPreviewRanges = ranges
        )

        testScope.testScheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        assertTrue(btnApplyVisual.isEnabled)
        assertTrue(tvDetectedStatus.text.contains("2")) // ranges count
        assertTrue(tvDetectedStatus.text.contains("00:02.000")) // duration
    }

    @Test
    fun `observeState_updatesDetectedStatus_whenSuccessEmpty`() {
        controller.activate()
        val tvDetectedStatus = root.findViewById<TextView>(R.id.tvDetectedStatus)
        val btnApplyVisual = root.findViewById<MaterialButton>(R.id.btnApplyVisual)

        uiStateFlow.value = VideoEditingUiState.Success(
            clips = emptyList(),
            keyframes = emptyList(),
            segments = emptyList(),
            detectionPreviewRanges = emptyList()
        )

        testScope.testScheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        assertFalse(btnApplyVisual.isEnabled)
        assertEquals(context.getString(R.string.no_silence_detected), tvDetectedStatus.text.toString())
    }

    @Test
    fun `observeProgress_updatesProgressViews_whenAnalyzing`() {
        controller.activate()
        val layoutProgress = root.findViewById<View>(R.id.layoutProgress)
        val progressIndicator = root.findViewById<LinearProgressIndicator>(R.id.progressIndicator)
        val tvProgressText = root.findViewById<TextView>(R.id.tvProgressText)
        val btnDetectAction = root.findViewById<MaterialButton>(R.id.btnDetectAction)

        visualDetectionProgressFlow.value = Pair(50, 100)

        testScope.testScheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        assertEquals(View.VISIBLE, layoutProgress.visibility)
        assertEquals(context.getString(R.string.cancel), btnDetectAction.text.toString())
        assertFalse(progressIndicator.isIndeterminate)
        assertEquals(50, progressIndicator.progress)
        assertEquals(context.getString(R.string.analyzing_progress, 50, 100), tvProgressText.text.toString())
    }

    @Test
    fun `observeProgress_hidesProgressViews_whenNotAnalyzing`() {
        controller.activate()
        val layoutProgress = root.findViewById<View>(R.id.layoutProgress)
        val btnDetectAction = root.findViewById<MaterialButton>(R.id.btnDetectAction)

        visualDetectionProgressFlow.value = null
        every { viewModel.hasCachedAnalysis() } returns false

        testScope.testScheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        assertEquals(View.GONE, layoutProgress.visibility)
        assertEquals(context.getString(R.string.detect), btnDetectAction.text.toString())
        assertTrue(btnDetectAction.isEnabled)
    }
}
