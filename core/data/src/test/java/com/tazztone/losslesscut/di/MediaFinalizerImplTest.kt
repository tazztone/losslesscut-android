package com.tazztone.losslesscut.di

import android.net.Uri
import com.tazztone.losslesscut.utils.StorageUtils
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaFinalizerImplTest {

    private lateinit var storageUtils: StorageUtils
    private lateinit var mediaFinalizer: MediaFinalizerImpl

    @Before
    fun setup() {
        storageUtils = mockk(relaxed = true)
        mediaFinalizer = MediaFinalizerImpl(storageUtils)
    }

    @Test
    fun `finalizeVideo delegates to storageUtils with parsed URI`() {
        val uriString = "content://media/external/video/media/1"
        mediaFinalizer.finalizeVideo(uriString)

        verify { storageUtils.finalizeVideo(Uri.parse(uriString)) }
    }

    @Test
    fun `finalizeAudio delegates to storageUtils with parsed URI`() {
        val uriString = "content://media/external/audio/media/2"
        mediaFinalizer.finalizeAudio(uriString)

        verify { storageUtils.finalizeAudio(Uri.parse(uriString)) }
    }
}
