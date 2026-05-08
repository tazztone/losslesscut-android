package com.tazztone.losslesscut.ui

import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.widget.TooltipCompat
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaClipAdapterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
    }

    @Test
    fun testAddViewHolderBinding() {
        var addClicked = false
        val adapter = MediaClipAdapter(
            onClipSelected = {},
            onClipsReordered = { _, _ -> },
            onClipLongPressed = {},
            onStartDrag = {},
            onAddClicked = { addClicked = true }
        )
        adapter.submitList(emptyList())

        val parent = FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 1) // VIEW_TYPE_ADD = 1
        adapter.onBindViewHolder(viewHolder, 0)

        // Cannot easily read TooltipCompat text directly without reflection or shadow in older UI toolkits,
        // but we can verify the click listener which was set during bind()
        viewHolder.itemView.performClick()
        assertTrue("onAddClicked should be called", addClicked)
    }

    @Test
    fun testGetItemCount() {
        val adapter = MediaClipAdapter(
            onClipSelected = {},
            onClipsReordered = { _, _ -> },
            onClipLongPressed = {},
            onStartDrag = {},
            onAddClicked = {}
        )

        // Submit an empty list, count should be 1 (just the add button)
        adapter.submitList(emptyList())
        assertEquals(1, adapter.itemCount)

        // Submit a list with 1 item, count should be 2 (item + add button)
        val clip = MediaClip(
            id = UUID.randomUUID(),
            uri = "content://dummy",
            fileName = "dummy.mp4",
            durationMs = 1000L,
            width = 1920,
            height = 1080,
            videoMime = "video/avc",
            audioMime = "audio/mp4a-latm",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = false
        )
        adapter.submitList(listOf(clip))
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun testGetItemViewType() {
        val adapter = MediaClipAdapter(
            onClipSelected = {},
            onClipsReordered = { _, _ -> },
            onClipLongPressed = {},
            onStartDrag = {},
            onAddClicked = {}
        )

        // With empty list, position 0 should be VIEW_TYPE_ADD (1)
        adapter.submitList(emptyList())
        assertEquals(1, adapter.getItemViewType(0))

        // With 1 item, position 0 should be VIEW_TYPE_CLIP (0), position 1 should be VIEW_TYPE_ADD (1)
        val clip = MediaClip(
            id = UUID.randomUUID(),
            uri = "content://dummy",
            fileName = "dummy.mp4",
            durationMs = 1000L,
            width = 1920,
            height = 1080,
            videoMime = "video/avc",
            audioMime = "audio/mp4a-latm",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = false
        )
        adapter.submitList(listOf(clip))
        assertEquals(0, adapter.getItemViewType(0))
        assertEquals(1, adapter.getItemViewType(1))
    }
}
