package com.tazztone.losslesscut.ui

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

@RunWith(AndroidJUnit4::class)
class MainActivitySecurityTest {

    private lateinit var activity: MainActivity
    private lateinit var activityController: ActivityController<MainActivity>

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(MainActivity::class.java)
        activity = activityController.get()
    }

    @Test
    fun testIsValidUri_ContentScheme_ReturnsTrue() {
        val uri = Uri.parse("content://media/external/video/media/1")
        assertTrue(invokeIsValidUri(uri))
    }

    @Test
    fun testIsValidUri_FileScheme_SafePath_ReturnsTrue() {
        val uri = Uri.parse("file:///storage/emulated/0/Download/video.mp4")
        assertTrue(invokeIsValidUri(uri))
    }

    @Test
    fun testIsValidUri_FileScheme_PathTraversal_ReturnsFalse() {
        val uri = Uri.parse("file:///data/data/com.tazztone.losslesscut/../../../../etc/passwd")
        assertFalse(invokeIsValidUri(uri))

        val uri2 = Uri.parse("file:///storage/emulated/0/Download/../illegal.mp4")
        assertFalse(invokeIsValidUri(uri2))
    }

    @Test
    fun testIsValidUri_InvalidScheme_ReturnsFalse() {
        val uri = Uri.parse("http://example.com/video.mp4")
        assertFalse(invokeIsValidUri(uri))

        val uri2 = Uri.parse("https://example.com/video.mp4")
        assertFalse(invokeIsValidUri(uri2))
    }

    @Test
    fun testIsValidUri_NullUri_ReturnsFalse() {
        assertFalse(invokeIsValidUri(null))
    }

    private fun invokeIsValidUri(uri: Uri?): Boolean {
        val method = MainActivity::class.java.getDeclaredMethod("isValidUri", Uri::class.java)
        method.isAccessible = true
        return method.invoke(activity, uri) as Boolean
    }
}
