package com.tazztone.losslesscut.engine.muxing

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileDescriptor
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaDataSourceTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var dataSource: MediaDataSource

    @Before
    fun setup() {
        context = mockk()
        contentResolver = mockk()
        every { context.contentResolver } returns contentResolver
        dataSource = MediaDataSource(context)
    }

    @Test
    fun `setExtractorSource uses context by default`() {
        val extractor = mockk<MediaExtractor>(relaxed = true)
        val uriString = "content://test"
        val uri = Uri.parse(uriString)

        dataSource.setExtractorSource(extractor, uriString)

        verify { extractor.setDataSource(context, uri, null) }
    }

    @Test
    fun `setExtractorSource falls back to FileDescriptor on IOException`() {
        val extractor = mockk<MediaExtractor>(relaxed = true)
        val uriString = "content://test"
        val uri = Uri.parse(uriString)
        val pfd = mockk<ParcelFileDescriptor>()
        val fd = mockk<FileDescriptor>()

        every { extractor.setDataSource(context, uri, null) } throws IOException("Failed")
        every { contentResolver.openFileDescriptor(uri, "r") } returns pfd
        every { pfd.fileDescriptor } returns fd
        every { pfd.close() } returns Unit

        dataSource.setExtractorSource(extractor, uriString)

        verify { extractor.setDataSource(fd) }
        verify { pfd.close() }
    }

    @Test
    fun `setRetrieverSource uses context by default`() {
        val retriever = mockk<MediaMetadataRetriever>(relaxed = true)
        val uriString = "content://test"
        val uri = Uri.parse(uriString)

        dataSource.setRetrieverSource(retriever, uriString)

        verify { retriever.setDataSource(context, uri) }
    }

    @Test
    fun `setRetrieverSource falls back to FileDescriptor on IllegalArgumentException`() {
        val retriever = mockk<MediaMetadataRetriever>(relaxed = true)
        val uriString = "content://test"
        val uri = Uri.parse(uriString)
        val pfd = mockk<ParcelFileDescriptor>()
        val fd = mockk<FileDescriptor>()

        every { retriever.setDataSource(context, uri) } throws IllegalArgumentException("Failed")
        every { contentResolver.openFileDescriptor(uri, "r") } returns pfd
        every { pfd.fileDescriptor } returns fd
        every { pfd.close() } returns Unit

        dataSource.setRetrieverSource(retriever, uriString)

        verify { retriever.setDataSource(fd) }
    }
}
