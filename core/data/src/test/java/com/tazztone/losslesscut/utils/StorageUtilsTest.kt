package com.tazztone.losslesscut.utils

import com.tazztone.losslesscut.data.AppPreferences
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.net.Uri
import android.content.Context
import android.content.ContentResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
        storageUtils = StorageUtils(context, preferences, testDispatcher)
    }

    @Test
    fun testGetFileName_returnsCorrectName() = runTest {
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
    fun testGetFileName_returnsDefaultIfCursorEmpty() = runTest {
        val uri = Uri.parse("content://invalid/uri")

        // Mock query returning null
        every {
            contentResolver.query(eq(uri), any(), any(), any(), any())
        } returns null

        val name = storageUtils.getFileName(uri)
        assertEquals("video.mp4", name)
    }

    @Test
    fun testDeleteOriginalMedia_emptyList_returnsSuccess() = runTest {
        val result = storageUtils.deleteOriginalMedia(emptyList())
        assertEquals(MediaDeletionResult.Success(emptyList()), result)
    }

    @Test
    @Config(sdk = [28])
    fun testDeleteOriginalMedia_api28_callsResolverDelete() = runTest {
        val uri = Uri.parse("content://media/external/video/media/100")
        every { contentResolver.delete(uri, null, null) } returns 1

        val result = storageUtils.deleteOriginalMedia(listOf(uri))
        assertEquals(MediaDeletionResult.Success(listOf(uri)), result)
    }

    @Test
    fun testDeleteOriginalMedia_mediaStoreUriApi30_handlesRobolectricStub() = runTest {
        val uri = Uri.parse("content://media/external/video/media/100")
        val result = storageUtils.deleteOriginalMedia(listOf(uri))
        // Robolectric stubs MediaStore.createTrashRequest by throwing UnsupportedOperationException
        assert(result is MediaDeletionResult.Failed)
    }

    @Test
    fun testDeleteOriginalMedia_documentUri_deletesDocument() = runTest {
        val uri = Uri.parse("content://com.android.providers.downloads.documents/document/1")
        mockkStatic(android.provider.DocumentsContract::class) {
            every { android.provider.DocumentsContract.isDocumentUri(context, uri) } returns true
            every { android.provider.DocumentsContract.deleteDocument(contentResolver, uri) } returns true

            val result = storageUtils.deleteOriginalMedia(listOf(uri))
            assertEquals(MediaDeletionResult.Success(listOf(uri)), result)
        }
    }

    @Test
    fun testDeleteOriginalMedia_partialDocumentFailure_reportsConfirmedUris() = runTest {
        val firstUri = Uri.parse("content://com.android.providers.downloads.documents/document/1")
        val secondUri = Uri.parse("content://com.android.providers.downloads.documents/document/2")
        mockkStatic(android.provider.DocumentsContract::class) {
            every { android.provider.DocumentsContract.isDocumentUri(context, firstUri) } returns true
            every { android.provider.DocumentsContract.isDocumentUri(context, secondUri) } returns true
            every { android.provider.DocumentsContract.deleteDocument(contentResolver, firstUri) } returns true
            every { android.provider.DocumentsContract.deleteDocument(contentResolver, secondUri) } returns false

            val result = storageUtils.deleteOriginalMedia(listOf(firstUri, secondUri))

            assertTrue(result is MediaDeletionResult.Failed)
            result as MediaDeletionResult.Failed
            assertEquals(listOf(firstUri), result.removedUris)
            assertEquals(listOf(secondUri), result.remainingUris)
        }
    }

    @Test
    fun testDeleteOriginalMedia_mixedUris_doesNotDeleteDocumentsBeforeMediaStorePrompt() = runTest {
        val documentUri = Uri.parse("content://com.android.providers.downloads.documents/document/1")
        val mediaStoreUri = Uri.parse("content://media/external/video/media/100")
        mockkStatic(android.provider.DocumentsContract::class) {
            every { android.provider.DocumentsContract.isDocumentUri(context, documentUri) } returns true
            every { android.provider.DocumentsContract.isDocumentUri(context, mediaStoreUri) } returns false

            val result = storageUtils.deleteOriginalMedia(listOf(documentUri, mediaStoreUri))

            assertTrue(result is MediaDeletionResult.Failed)
            verify(exactly = 0) {
                android.provider.DocumentsContract.deleteDocument(contentResolver, documentUri)
            }
        }
    }
}

