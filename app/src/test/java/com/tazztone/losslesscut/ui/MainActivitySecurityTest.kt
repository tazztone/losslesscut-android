package com.tazztone.losslesscut.ui

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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
    fun testIsValidUri_InternalContentScheme_ReturnsFalse() {
        val packageName = activity.packageName
        val uri1 = Uri.parse("content://$packageName/private/file")
        assertFalse(invokeIsValidUri(uri1))

        val uri2 = Uri.parse("content://$packageName.provider/shared/file")
        assertFalse(invokeIsValidUri(uri2))
    }

    @Test
    fun testIsValidUri_FileScheme_Validation() {
        // Safe external file should be true
        val uri = Uri.parse("file:///storage/emulated/0/Download/video.mp4")
        assertTrue(invokeIsValidUri(uri))

        // Path traversal should be false
        val uri2 = Uri.parse("file:///storage/emulated/0/../../../../etc/passwd")
        assertFalse(invokeIsValidUri(uri2))

        // Private app data should be false
        val dataDir = activity.applicationInfo.dataDir
        val uri3 = Uri.parse("file://$dataDir/shared_prefs/prefs.xml")
        assertFalse(invokeIsValidUri(uri3))
    }

    @Test
    fun testIsValidUri_InvalidSchemeOrNull_ReturnsFalse() {
        assertFalse(invokeIsValidUri(null))
        assertFalse(invokeIsValidUri(Uri.parse("http://example.com/video.mp4")))
        assertFalse(invokeIsValidUri(Uri.parse("https://example.com/video.mp4")))
    }

    private fun invokeIsValidUri(uri: Uri?): Boolean {
        val method = MainActivity::class.java.getDeclaredMethod("isValidUri", Uri::class.java)
        method.isAccessible = true
        return method.invoke(activity, uri) as Boolean
    }
}
