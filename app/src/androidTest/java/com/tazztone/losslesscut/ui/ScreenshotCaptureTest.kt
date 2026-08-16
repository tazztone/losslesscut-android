package com.tazztone.losslesscut.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.media3.common.Player
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScreenshotCaptureTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testFileName = "SpatCut_20260113230858.mp4"
    private val goldenDurationMs = 18450L

    @org.junit.Before
    fun setUp() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP")
        uiAutomation.executeShellCommand("wm dismiss-keyguard")
    }

    @Test
    fun captureDashboard() {
        val intent = Intent(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                runBlocking {
                    val clip = MediaClip(
                        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        uri = "content://${context.packageName}.fileprovider/cache/$testFileName",
                        fileName = testFileName,
                        durationMs = goldenDurationMs,
                        width = 1920,
                        height = 1080,
                        videoMime = "video/avc",
                        audioMime = "audio/mp4a-latm",
                        sampleRate = 48000,
                        channelCount = 2,
                        fps = 30.0f,
                        rotation = 0,
                        isAudioOnly = false,
                        segments = listOf(
                            TrimSegment(
                                id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                                startMs = 2000L,
                                endMs = 6500L,
                                action = SegmentAction.KEEP
                            ),
                            TrimSegment(
                                id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                                startMs = 9500L,
                                endMs = 15500L,
                                action = SegmentAction.KEEP
                            )
                        )
                    )
                    activity.sessionUseCase.saveSession(listOf(clip))
                }
            }

            scenario.recreate()
            composeTestRule.waitForIdle()
            Thread.sleep(500) // Settle animations and layout

            saveScreenshot("screenshot_dashboard.png")
        }
    }

    @Test
    fun captureSettings() {
        val intent = Intent(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            composeTestRule.waitForIdle()

            // Open settings bottom sheet via Compose button
            composeTestRule.onNodeWithContentDescription("Settings").performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(600) // Settle bottom sheet slide-in animation

            saveScreenshot("screenshot_settings.png")
        }
    }

    @Test
    fun captureLandscapeEditor() {
        val fileUri = prepareTestVideoUri()
        val intent = Intent(context, VideoEditingActivity::class.java).apply {
            putParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, arrayListOf(fileUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Media", fileUri)
        }

        ActivityScenario.launch<VideoEditingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            waitForEditorReady(scenario)

            scenario.onActivity { activity ->
                val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val editorFragment = navHost?.childFragmentManager?.fragments?.firstOrNull { it is EditorFragment } as? EditorFragment
                val viewModel = activity.findViewById<View>(android.R.id.content).let {
                    androidx.lifecycle.ViewModelProvider(activity)[VideoEditingViewModel::class.java]
                }

                viewModel.onUserInteraction()
                viewModel.splitSegmentAt(7000L)
                Thread.sleep(200)
                val segments = (viewModel.uiState.value as? VideoEditingUiState.Success)?.segments.orEmpty()
                if (segments.size >= 2) {
                    viewModel.updateSegmentBounds(segments[0].id, 2000L, 6500L)
                    viewModel.updateSegmentBounds(segments[1].id, 9500L, 15500L)
                    viewModel.commitSegmentBounds()
                }
                editorFragment?.getPlayerView()?.player?.seekTo(4500L)
            }

            Thread.sleep(800) // Settle video frame render, gap layout, and timeline markers
            saveScreenshot("screenshot_landscape.png")
        }
    }

    @Test
    fun capturePortraitEditor() {
        val fileUri = prepareTestVideoUri()
        val intent = Intent(context, VideoEditingActivity::class.java).apply {
            putParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, arrayListOf(fileUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Media", fileUri)
        }

        ActivityScenario.launch<VideoEditingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            waitForEditorReady(scenario)

            scenario.onActivity { activity ->
                val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val editorFragment = navHost?.childFragmentManager?.fragments?.firstOrNull { it is EditorFragment } as? EditorFragment
                val viewModel = activity.findViewById<View>(android.R.id.content).let {
                    androidx.lifecycle.ViewModelProvider(activity)[VideoEditingViewModel::class.java]
                }

                viewModel.onUserInteraction()
                viewModel.splitSegmentAt(7000L)
                Thread.sleep(200)
                val segments = (viewModel.uiState.value as? VideoEditingUiState.Success)?.segments.orEmpty()
                if (segments.size >= 2) {
                    viewModel.updateSegmentBounds(segments[0].id, 2000L, 6500L)
                    viewModel.updateSegmentBounds(segments[1].id, 9500L, 15500L)
                    viewModel.commitSegmentBounds()
                }
                editorFragment?.getPlayerView()?.player?.seekTo(4500L)
            }

            Thread.sleep(800) // Settle video frame render, gap layout, and timeline markers
            saveScreenshot("screenshot_portrait.png")
        }
    }

    @Test
    fun captureSmartCutSilence() {
        val fileUri = prepareTestVideoUri()
        val intent = Intent(context, VideoEditingActivity::class.java).apply {
            putParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, arrayListOf(fileUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Media", fileUri)
        }

        ActivityScenario.launch<VideoEditingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            waitForEditorReady(scenario)

            scenario.onActivity { activity ->
                val viewModel = activity.findViewById<View>(android.R.id.content).let {
                    androidx.lifecycle.ViewModelProvider(activity)[VideoEditingViewModel::class.java]
                }
                viewModel.onUserInteraction()
                viewModel.splitSegmentAt(7000L)
                Thread.sleep(200)
                val segments = (viewModel.uiState.value as? VideoEditingUiState.Success)?.segments.orEmpty()
                if (segments.size >= 2) {
                    viewModel.updateSegmentBounds(segments[0].id, 2000L, 6500L)
                    viewModel.updateSegmentBounds(segments[1].id, 9500L, 15500L)
                    viewModel.commitSegmentBounds()
                }

                val btnSmartCut = activity.findViewById<View>(R.id.btnSmartCut)
                btnSmartCut?.performClick()
            }

            Thread.sleep(600) // Settle Smart Cut overlay opening
            saveScreenshot("screenshot_smart_cut_silence.png")
        }
    }

    @Test
    fun captureSmartCutVisual() {
        val fileUri = prepareTestVideoUri()
        val intent = Intent(context, VideoEditingActivity::class.java).apply {
            putParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, arrayListOf(fileUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Media", fileUri)
        }

        ActivityScenario.launch<VideoEditingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            waitForEditorReady(scenario)

            scenario.onActivity { activity ->
                val viewModel = activity.findViewById<View>(android.R.id.content).let {
                    androidx.lifecycle.ViewModelProvider(activity)[VideoEditingViewModel::class.java]
                }
                viewModel.onUserInteraction()
                viewModel.splitSegmentAt(7000L)
                Thread.sleep(200)
                val segments = (viewModel.uiState.value as? VideoEditingUiState.Success)?.segments.orEmpty()
                if (segments.size >= 2) {
                    viewModel.updateSegmentBounds(segments[0].id, 2000L, 6500L)
                    viewModel.updateSegmentBounds(segments[1].id, 9500L, 15500L)
                    viewModel.commitSegmentBounds()
                }

                val btnSmartCut = activity.findViewById<View>(R.id.btnSmartCut)
                btnSmartCut?.performClick()

                val tabLayout = activity.findViewById<TabLayout>(R.id.tabLayout)
                tabLayout?.getTabAt(1)?.select()
            }

            Thread.sleep(600) // Settle Visual tab switch
            saveScreenshot("screenshot_smart_cut_visual.png")
        }
    }

    private fun prepareTestVideoUri(): Uri {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val cacheFile = File(cacheDir, testFileName)
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val pfd = uiAutomation.executeShellCommand("cp /data/local/tmp/$testFileName ${cacheFile.absolutePath}")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
                val buf = ByteArray(8192)
                while (stream.read(buf) != -1) {}
            }
            Thread.sleep(1000)
        }

        assertTrue(
            "Test video must exist in cacheDir: ${cacheFile.absolutePath} (size: ${cacheFile.length()})",
            cacheFile.exists() && cacheFile.length() > 0L
        )

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
    }

    private fun waitForEditorReady(scenario: ActivityScenario<VideoEditingActivity>, timeoutMs: Long = 15000L) {
        val startTime = System.currentTimeMillis()
        var isReady = false
        var lastDiagnostic = ""

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            scenario.onActivity { activity ->
                val viewModel = androidx.lifecycle.ViewModelProvider(activity)[VideoEditingViewModel::class.java]
                val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val editorFragment = navHost?.childFragmentManager?.fragments?.firstOrNull { it is EditorFragment } as? EditorFragment
                val player = editorFragment?.getPlayerView()?.player

                val uiState = viewModel.uiState.value
                val hasWaveform = viewModel.waveformData.value != null
                val playerReady = player != null && player.playbackState == Player.STATE_READY

                lastDiagnostic = "UiState=${uiState::class.simpleName}, Waveform=${if (hasWaveform) "Ready" else "Null"}, PlayerState=${player?.playbackState ?: "NoPlayer"}"

                if (uiState is VideoEditingUiState.Success && hasWaveform && playerReady) {
                    isReady = true
                }
            }

            if (isReady) return
            Thread.sleep(100)
        }

        assertTrue("Timed out waiting for Editor readiness. Last state: $lastDiagnostic", isReady)
    }

    private fun saveScreenshot(fileName: String) {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val cmd = "screencap -p /sdcard/Download/losslesscut_screenshots/$fileName"
        val pfd = uiAutomation.executeShellCommand(cmd)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
            val buf = ByteArray(8192)
            while (stream.read(buf) != -1) {}
        }
    }
}
