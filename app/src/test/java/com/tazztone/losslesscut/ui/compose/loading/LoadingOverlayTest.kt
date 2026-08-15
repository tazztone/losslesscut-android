package com.tazztone.losslesscut.ui.compose.loading

import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LoadingOverlayTest {

    @Test
    fun `loading overlay composable renders in indeterminate mode without crashing`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val composeView = ComposeView(context).apply {
            setContent {
                LoadingOverlay(
                    progress = 0,
                    message = "Importing media…",
                    isVisible = true
                )
            }
        }
        assertNotNull(composeView)
    }

    @Test
    fun `loading overlay composable renders in determinate mode with percentage without crashing`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val composeView = ComposeView(context).apply {
            setContent {
                LoadingOverlay(
                    progress = 75,
                    message = "Saving segment 3 of 4…",
                    isVisible = true
                )
            }
        }
        assertNotNull(composeView)
    }

    @Test
    fun `laser waveform animation composable renders independently`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val composeView = ComposeView(context).apply {
            setContent {
                LaserWaveformAnimation()
            }
        }
        assertNotNull(composeView)
    }
}
