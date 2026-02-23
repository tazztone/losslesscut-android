package com.tazztone.losslesscut.utils

import com.tazztone.losslesscut.data.AppPreferences
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.net.Uri
import android.content.Context
import android.content.ContentResolver
import org.junit.Assert.assertEquals
import org.junit.Before
import android.database.MatrixCursor
import android.provider.OpenableColumns

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StorageUtilsTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var preferences: AppPreferences
    private lateinit var storageUtils: StorageUtils

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver

        preferences = mockk(relaxed = true)
        every { preferences.customOutputUriFlow } returns flowOf(null)

        storageUtils = StorageUtils(context, preferences)
    }

    @Test
    fun testGetFileName_returnsCorrectName() {
        val uri = Uri.parse("content://media/external/video/media/1")
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
        cursor.addRow(arrayOf("video.mp4"))

        // Mock query
        every {
            contentResolver.query(eq(uri), any(), any(), any(), any())
        } returns cursor

        val name = storageUtils.getFileName(uri)
        assertEquals("video.mp4", name)
    }

    @Test
    fun testGetFileName_returnsDefaultIfCursorEmpty() {
        val uri = Uri.parse("content://invalid/uri")

        // Mock query returning null
        every {
            contentResolver.query(eq(uri), any(), any(), any(), any())
        } returns null

        val name = storageUtils.getFileName(uri)
        assertEquals("video.mp4", name)
    }
}
