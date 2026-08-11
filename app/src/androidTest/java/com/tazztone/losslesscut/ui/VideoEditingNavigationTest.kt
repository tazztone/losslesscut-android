package com.tazztone.losslesscut.ui

import android.content.Intent
import android.net.Uri
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tazztone.losslesscut.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoEditingNavigationTest {

    @Test
    fun unifiedModeSelectsEditorDestination() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, VideoEditingActivity::class.java).apply {
            putParcelableArrayListExtra(
                VideoEditingActivity.EXTRA_VIDEO_URIS,
                arrayListOf(Uri.parse("content://test.invalid/video.mp4"))
            )
        }

        ActivityScenario.launch<VideoEditingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val navHost = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                assertEquals(R.id.editorFragment, navHost.navController.currentDestination?.id)
            }
        }
    }
}
