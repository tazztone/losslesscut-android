package com.tazztone.losslesscut.engine
import com.tazztone.losslesscut.di.*
import com.tazztone.losslesscut.customviews.*
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.*
import com.tazztone.losslesscut.viewmodel.*
import com.tazztone.losslesscut.engine.*
import com.tazztone.losslesscut.data.*
import com.tazztone.losslesscut.utils.*

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LosslessEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storageUtils = StorageUtils(context)
    private val engine = LosslessEngineImpl(storageUtils, kotlinx.coroutines.Dispatchers.IO)

    @Test
    fun probeKeyframes_withInvalidUri_returnsEmptyList() = runBlocking {
        val invalidUri = Uri.parse("content://invalid/video.mp4")
        val keyframes = engine.probeKeyframes(context, invalidUri)
        assertTrue("Keyframes should be empty for invalid URI", keyframes.isEmpty())
    }

    @Test
    fun executeLosslessCut_withInvalidUri_returnsFailure() = runBlocking {
        val invalidUri = Uri.parse("content://invalid/video.mp4")
        val outputFile = File(context.cacheDir, "output.mp4")
        val outputUri = Uri.fromFile(outputFile)
        val result = engine.executeLosslessCut(context, invalidUri, outputUri, 0, 1000)
        assertTrue("Lossless cut should fail for invalid URI", result.isFailure)
    }
}
