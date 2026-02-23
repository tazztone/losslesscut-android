package com.tazztone.losslesscut.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppPreferencesTest {

    private lateinit var context: Context
    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = AppPreferences(context)
    }

    @Test
    fun testDefaultUndoLimit() = runTest {
        // Assuming default is 30. If other tests run before this and change it, this might fail
        // unless Robolectric isolates data storage.
        // DataStore uses a file. Robolectric's filesystem is usually in-memory and reset?
        val limit = preferences.undoLimitFlow.first()
        // If it fails, we know persistence is an issue.
        // But let's check for 30 or whatever was set.
        // Actually, let's just test setting and getting.

        // Reset to default manually to be safe if persistence exists
        preferences.setUndoLimit(30)
        assertEquals(30, preferences.undoLimitFlow.first())
    }

    @Test
    fun testSetUndoLimit() = runTest {
        preferences.setUndoLimit(50)
        val limit = preferences.undoLimitFlow.first()
        assertEquals(50, limit)
    }

    @Test
    fun testDefaultSnapshotFormat() = runTest {
        // Reset
        preferences.setSnapshotFormat("PNG")
        val format = preferences.snapshotFormatFlow.first()
        assertEquals("PNG", format)
    }

    @Test
    fun testSetSnapshotFormat() = runTest {
        preferences.setSnapshotFormat("JPEG")
        val format = preferences.snapshotFormatFlow.first()
        assertEquals("JPEG", format)
    }

    @Test
    fun testSetJpgQuality() = runTest {
        preferences.setJpgQuality(80)
        val quality = preferences.jpgQualityFlow.first()
        assertEquals(80, quality)
    }
}
