package com.tazztone.losslesscut.ui.editor

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddClipsDelegateTest {

    private lateinit var context: Context
    private val viewModel: VideoEditingViewModel = mockk(relaxed = true)
    private val uiStateFlow = MutableStateFlow<VideoEditingUiState>(VideoEditingUiState.Initial)

    private lateinit var delegate: AddClipsDelegate

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
        context = activity
        every { viewModel.uiState } returns uiStateFlow
        delegate = AddClipsDelegate(context, viewModel)
    }

    @Test
    fun `onClipsReceived does nothing when uris are empty`() {
        delegate.onClipsReceived(emptyList())
        verify(exactly = 0) { viewModel.addClips(any()) }
    }

    @Test
    fun `onClipsReceived adds clips directly when not in Success state`() {
        uiStateFlow.value = VideoEditingUiState.Initial

        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns "content://media/1"
        delegate.onClipsReceived(listOf(uri))

        verify(exactly = 1) { viewModel.addClips(listOf(uri)) }
    }

    @Test
    fun `onClipsReceived adds clips directly when no duplicates in Success state`() {
        val existingClip = mockk<MediaClip>(relaxed = true)
        every { existingClip.id } returns UUID.randomUUID()
        every { existingClip.uri } returns "content://media/1"

        uiStateFlow.value = VideoEditingUiState.Success(
            clips = listOf(existingClip),
            keyframes = emptyList(),
            segments = emptyList()
        )

        val newUri = mockk<Uri>(relaxed = true)
        every { newUri.toString() } returns "content://media/2"
        delegate.onClipsReceived(listOf(newUri))

        verify(exactly = 1) { viewModel.addClips(listOf(newUri)) }
    }

    @Test
    fun `onClipsReceived shows dialog when duplicate exists and import anyway adds all`() {
        val existingUriString = "content://media/1"
        val existingClip = mockk<MediaClip>(relaxed = true)
        every { existingClip.id } returns UUID.randomUUID()
        every { existingClip.uri } returns existingUriString

        uiStateFlow.value = VideoEditingUiState.Success(
            clips = listOf(existingClip),
            keyframes = emptyList(),
            segments = emptyList()
        )

        val duplicateUri = mockk<Uri>(relaxed = true)
        every { duplicateUri.toString() } returns existingUriString

        val newUri = mockk<Uri>(relaxed = true)
        every { newUri.toString() } returns "content://media/2"

        val urisToAdd = listOf(duplicateUri, newUri)

        delegate.onClipsReceived(urisToAdd)

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog?
        assertNotNull("Dialog should be shown", dialog)

        val positiveButton = dialog!!.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(positiveButton)

        // Click "Import Anyway"
        positiveButton.performClick()
        ShadowLooper.idleMainLooper()

        verify(exactly = 1) { viewModel.addClips(urisToAdd) }
    }

    @Test
    fun `onClipsReceived shows dialog when duplicate exists and skip adds non-duplicates`() {
        val existingUriString = "content://media/1"
        val existingClip = mockk<MediaClip>(relaxed = true)
        every { existingClip.id } returns UUID.randomUUID()
        every { existingClip.uri } returns existingUriString

        uiStateFlow.value = VideoEditingUiState.Success(
            clips = listOf(existingClip),
            keyframes = emptyList(),
            segments = emptyList()
        )

        val duplicateUri = mockk<Uri>(relaxed = true)
        every { duplicateUri.toString() } returns existingUriString

        val newUri = mockk<Uri>(relaxed = true)
        every { newUri.toString() } returns "content://media/2"

        val urisToAdd = listOf(duplicateUri, newUri)

        delegate.onClipsReceived(urisToAdd)

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog?
        assertNotNull("Dialog should be shown", dialog)

        // Click "Skip"
        dialog!!.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        ShadowLooper.idleMainLooper()

        verify(exactly = 1) { viewModel.addClips(listOf(newUri)) }
    }

    @Test
    fun `onClipsReceived shows dialog when duplicate exists and cancel adds nothing`() {
        val existingUriString = "content://media/1"
        val existingClip = mockk<MediaClip>(relaxed = true)
        every { existingClip.id } returns UUID.randomUUID()
        every { existingClip.uri } returns existingUriString

        uiStateFlow.value = VideoEditingUiState.Success(
            clips = listOf(existingClip),
            keyframes = emptyList(),
            segments = emptyList()
        )

        val duplicateUri = mockk<Uri>(relaxed = true)
        every { duplicateUri.toString() } returns existingUriString

        val newUri = mockk<Uri>(relaxed = true)
        every { newUri.toString() } returns "content://media/2"

        val urisToAdd = listOf(duplicateUri, newUri)

        delegate.onClipsReceived(urisToAdd)

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog?
        assertNotNull("Dialog should be shown", dialog)

        // Click "Cancel"
        dialog!!.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        ShadowLooper.idleMainLooper()

        verify(exactly = 0) { viewModel.addClips(any()) }
    }

    @Test
    fun `onClipsReceived uses single file message when adding one duplicate file`() {
        val existingUriString = "content://media/1"
        val existingClip = mockk<MediaClip>(relaxed = true)
        every { existingClip.id } returns UUID.randomUUID()
        every { existingClip.uri } returns existingUriString

        uiStateFlow.value = VideoEditingUiState.Success(
            clips = listOf(existingClip),
            keyframes = emptyList(),
            segments = emptyList()
        )

        val duplicateUri = mockk<Uri>(relaxed = true)
        every { duplicateUri.toString() } returns existingUriString

        delegate.onClipsReceived(listOf(duplicateUri))

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog?
        assertNotNull("Dialog should be shown", dialog)
    }

    @Test
    fun `onClipsReceived uses single file message when adding multiple files that are all duplicates`() {
        val existingUriString1 = "content://media/1"
        val existingUriString2 = "content://media/2"

        val existingClip1 = mockk<MediaClip>(relaxed = true)
        every { existingClip1.id } returns UUID.randomUUID()
        every { existingClip1.uri } returns existingUriString1

        val existingClip2 = mockk<MediaClip>(relaxed = true)
        every { existingClip2.id } returns UUID.randomUUID()
        every { existingClip2.uri } returns existingUriString2

        uiStateFlow.value = VideoEditingUiState.Success(
            clips = listOf(existingClip1, existingClip2),
            keyframes = emptyList(),
            segments = emptyList()
        )

        val duplicateUri1 = mockk<Uri>(relaxed = true)
        every { duplicateUri1.toString() } returns existingUriString1
        val duplicateUri2 = mockk<Uri>(relaxed = true)
        every { duplicateUri2.toString() } returns existingUriString2

        delegate.onClipsReceived(listOf(duplicateUri1, duplicateUri2))

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog?
        assertNotNull("Dialog should be shown", dialog)
    }
}
