package com.tazztone.losslesscut.ui.editor

import android.app.Dialog
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.MediaTrack
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.viewmodel.ExportSettings
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExportOptionsDialogPresenterTest {

    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.AppTheme
    )

    @Test
    fun `combined output is the default and maps to merge`() {
        val settings = show(state()).export()

        assertTrue(settings.mergeSegments)
        assertEquals(listOf(1, 2), settings.selectedTracks)
        assertEquals(null, settings.rotationOverride)
    }

    @Test
    fun `separate output maps to individual segment export`() {
        val dialog = show(state())
        dialog.findViewById<View>(R.id.separateOutputCard).performClick()

        val settings = dialog.export()

        assertFalse(settings.mergeSegments)
    }

    @Test
    fun `rotation selection maps to the selected override`() {
        val dialog = show(state(), initialRotation = 90)
        assertEquals(2, dialog.findViewById<Spinner>(R.id.rotationSpinner).selectedItemPosition)

        val settings = dialog.export()

        assertEquals(90, settings.rotationOverride)
    }

    @Test
    fun `rotation options map to every supported override`() {
        listOf(1 to 0, 2 to 90, 3 to 180, 4 to 270).forEach { (position, expected) ->
            val dialog = show(state())
            dialog.findViewById<Spinner>(R.id.rotationSpinner).setSelection(position)

            assertEquals(expected, dialog.export().rotationOverride)
        }
    }

    @Test
    fun `selected tracks are forwarded and update keep flags`() {
        val dialog = show(state())
        trackCheckbox(dialog, 2).performClick()

        val settings = dialog.export()

        assertEquals(listOf(1), settings.selectedTracks)
        assertTrue(settings.keepVideo)
        assertFalse(settings.keepAudio)
    }

    @Test
    fun `export is disabled when every available track is unchecked`() {
        val dialog = show(state())
        trackCheckbox(dialog, 1).performClick()
        trackCheckbox(dialog, 2).performClick()

        assertFalse(dialog.findViewById<MaterialButton>(R.id.confirmExport).isEnabled)
    }

    @Test
    fun `audio-only state hides rotation controls`() {
        val dialog = show(state(audioOnly = true, tracks = listOf(audioTrack())))

        assertEquals(View.GONE, dialog.findViewById<View>(R.id.rotationSection).visibility)
        assertNotNull(dialog.findViewById<View>(R.id.combinedOutputCard))
    }

    private fun show(
        state: VideoEditingUiState.Success,
        initialRotation: Int = 0
    ): Dialog {
        ExportOptionsDialogPresenter(
            context = context,
            layoutInflater = LayoutInflater.from(context),
            onExport = { capturedSettings = it }
        ).show(state, initialRotation)
        return ShadowDialog.getLatestDialog()
    }

    private var capturedSettings: ExportSettings? = null

    private fun Dialog.export(): ExportSettings {
        findViewById<MaterialButton>(R.id.confirmExport).performClick()
        return requireNotNull(capturedSettings)
    }

    private fun trackCheckbox(dialog: Dialog, trackId: Int): MaterialCheckBox {
        return dialog.findViewById<View>(R.id.tracksContainer).findViewWithTag(trackId)
    }

    private fun state(
        audioOnly: Boolean = false,
        tracks: List<MediaTrack> = listOf(videoTrack(), audioTrack())
    ): VideoEditingUiState.Success {
        val segments = listOf(
            TrimSegment(startMs = 0, endMs = 1_000, action = SegmentAction.KEEP),
            TrimSegment(startMs = 2_000, endMs = 3_000, action = SegmentAction.KEEP)
        )
        val clip = MediaClip(
            id = UUID.randomUUID(),
            uri = "content://mock/video.mp4",
            fileName = "video.mp4",
            durationMs = 3_000,
            width = if (audioOnly) 0 else 1_920,
            height = if (audioOnly) 0 else 1_080,
            videoMime = if (audioOnly) null else "video/avc",
            audioMime = "audio/mp4a-latm",
            sampleRate = 44_100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = audioOnly,
            segments = segments,
            availableTracks = tracks
        )
        return VideoEditingUiState.Success(
            clips = listOf(clip),
            keyframes = emptyList(),
            segments = segments,
            isAudioOnly = audioOnly,
            hasAudioTrack = true,
            availableTracks = tracks
        )
    }

    private fun videoTrack() = MediaTrack(1, "video/avc", isVideo = true, isAudio = false)

    private fun audioTrack() = MediaTrack(2, "audio/mp4a-latm", isVideo = false, isAudio = true, language = "English")
}
