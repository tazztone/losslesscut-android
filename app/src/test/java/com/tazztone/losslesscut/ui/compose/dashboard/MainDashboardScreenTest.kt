package com.tazztone.losslesscut.ui.compose.dashboard

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.domain.model.SessionSummary
import com.tazztone.losslesscut.ui.compose.theme.LosslessCutTheme
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainDashboardScreenTest {

    @Test
    fun `dashboard composable renders with empty session list without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val composeView = ComposeView(context).apply {
            setContent {
                LosslessCutTheme(accentColorName = "cyan") {
                    MainDashboardScreen(
                        recentSessions = emptyList(),
                        snackbarHostState = SnackbarHostState(),
                        onLoadMedia = {},
                        onOpenSettings = {},
                        onOpenAbout = {},
                        onResumeSession = {},
                        onRemoveSession = {}
                    )
                }
            }
        }
        assertNotNull(composeView)
    }

    @Test
    fun `dashboard composable renders with active sessions without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummySessions = listOf(
            SessionSummary(
                uri = "content://media/external/video/media/101",
                fileName = "vacation_clip_2026.mp4",
                updatedAtEpochMs = System.currentTimeMillis() - 3600000L,
                clipCount = 3
            ),
            SessionSummary(
                uri = "content://media/external/video/media/102",
                fileName = "screencast_tutorial.mp4",
                updatedAtEpochMs = System.currentTimeMillis() - 86400000L,
                clipCount = 1
            )
        )

        val composeView = ComposeView(context).apply {
            setContent {
                LosslessCutTheme(accentColorName = "purple") {
                    MainDashboardScreen(
                        recentSessions = dummySessions,
                        snackbarHostState = SnackbarHostState(),
                        onLoadMedia = {},
                        onOpenSettings = {},
                        onOpenAbout = {},
                        onResumeSession = {},
                        onRemoveSession = {}
                    )
                }
            }
        }
        assertNotNull(composeView)
    }

    @Test
    fun `theme composable applies all supported accent colors without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accents = listOf("cyan", "purple", "green", "yellow", "red", "orange", "unknown")

        for (accent in accents) {
            val composeView = ComposeView(context).apply {
                setContent {
                    LosslessCutTheme(accentColorName = accent) {
                        MainDashboardScreen(
                            recentSessions = emptyList(),
                            snackbarHostState = SnackbarHostState(),
                            onLoadMedia = {},
                            onOpenSettings = {},
                            onOpenAbout = {},
                            onResumeSession = {},
                            onRemoveSession = {}
                        )
                    }
                }
            }
            assertNotNull(composeView)
        }
    }
}
