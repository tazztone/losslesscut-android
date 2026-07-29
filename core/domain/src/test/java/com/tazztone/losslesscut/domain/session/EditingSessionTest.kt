package com.tazztone.losslesscut.domain.session

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

public class EditingSessionTest {

    private fun createTestClip(
        durationMs: Long = 10000L,
        segments: List<TrimSegment> = listOf(TrimSegment(startMs = 0, endMs = durationMs))
    ): MediaClip {
        return MediaClip(
            id = UUID.randomUUID(),
            uri = "content://media/external/video/1",
            fileName = "sample.mp4",
            durationMs = durationMs,
            width = 1920,
            height = 1080,
            videoMime = "video/mp4",
            audioMime = "audio/mp4",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = false,
            segments = segments
        )
    }

    @Test
    public fun initialSessionStateIsEmpty() {
        val session = EditingSession()
        val snapshot = session.currentSnapshot

        assertTrue(snapshot.clips.isEmpty())
        assertEquals(0, snapshot.selectedClipIndex)
        assertNull(snapshot.selectedSegmentId)
        assertFalse(snapshot.isDirty)
        assertFalse(snapshot.canUndo)
        assertFalse(snapshot.canRedo)
    }

    @Test
    public fun setClipsInitializesStateAndSelectsFirstSegment() {
        val session = EditingSession()
        val clip = createTestClip()
        session.setClips(listOf(clip))

        val snapshot = session.currentSnapshot
        assertEquals(1, snapshot.clips.size)
        assertEquals(clip.segments.first().id, snapshot.selectedSegmentId)
        assertFalse(snapshot.isDirty)
    }

    @Test
    public fun splitSegmentAtCreatesNewSegmentAndUpdatesSelection() {
        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))

        val success = session.splitSegmentAt(5000L)
        assertTrue(success)

        val snapshot = session.currentSnapshot
        val updatedClip = snapshot.selectedClip!!
        assertEquals(2, updatedClip.segments.size)
        assertEquals(0L, updatedClip.segments[0].startMs)
        assertEquals(5000L, updatedClip.segments[0].endMs)
        assertEquals(5000L, updatedClip.segments[1].startMs)
        assertEquals(10000L, updatedClip.segments[1].endMs)
        assertEquals(updatedClip.segments[1].id, snapshot.selectedSegmentId)
        assertTrue(snapshot.isDirty)
        assertTrue(snapshot.canUndo)
    }

    @Test
    public fun splitSegmentAtFailsIfSegmentDurationIsBelowMinimumThreshold() {
        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))

        val success = session.splitSegmentAt(50L, minDurationMs = 100L)
        assertFalse(success)

        val snapshot = session.currentSnapshot
        assertEquals(1, snapshot.selectedClip!!.segments.size)
    }

    @Test
    public fun toggleSegmentDiscardTogglesKeepAndDiscard() {
        val session = EditingSession()
        val seg1 = TrimSegment(startMs = 0, endMs = 5000, action = SegmentAction.KEEP)
        val seg2 = TrimSegment(startMs = 5000, endMs = 10000, action = SegmentAction.KEEP)
        val clip = createTestClip(durationMs = 10000L, segments = listOf(seg1, seg2))
        session.setClips(listOf(clip))

        val success = session.toggleSegmentDiscard(seg1.id)
        assertTrue(success)

        val snapshot = session.currentSnapshot
        assertEquals(SegmentAction.DISCARD, snapshot.selectedClip!!.segments[0].action)
        assertTrue(snapshot.canUndo)
    }

    @Test
    public fun toggleSegmentDiscardPreventsDiscardingTheOnlyActiveSegment() {
        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))

        val segId = clip.segments.first().id
        val success = session.toggleSegmentDiscard(segId)
        assertFalse(success)

        assertEquals(SegmentAction.KEEP, session.currentSnapshot.selectedClip!!.segments.first().action)
    }

    @Test
    public fun undoAndRedoRestorePreviousSnapshotsCorrectly() {
        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))

        session.splitSegmentAt(4000L)
        assertEquals(2, session.currentSnapshot.selectedClip!!.segments.size)

        val undoSuccess = session.undo()
        assertTrue(undoSuccess)
        assertEquals(1, session.currentSnapshot.selectedClip!!.segments.size)
        assertTrue(session.currentSnapshot.canRedo)

        val redoSuccess = session.redo()
        assertTrue(redoSuccess)
        assertEquals(2, session.currentSnapshot.selectedClip!!.segments.size)
    }
}
