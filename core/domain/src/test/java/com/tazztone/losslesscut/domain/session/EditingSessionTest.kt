package com.tazztone.losslesscut.domain.session

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        assertNull(snapshot.selectedClip)
        assertNull(snapshot.selectedSegment)
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
        assertNotNull(snapshot.selectedClip)
        assertNotNull(snapshot.selectedSegment)
        assertFalse(snapshot.isDirty)
    }

    @Test
    public fun setClipsHandlesEmptyListAndIndexCoercion() {
        val session = EditingSession()
        session.setClips(emptyList(), selectedIndex = 5)
        assertEquals(0, session.currentSnapshot.selectedClipIndex)
        assertNull(session.currentSnapshot.selectedClip)
        assertNull(session.currentSnapshot.selectedSegment)

        val clip1 = createTestClip(durationMs = 5000L)
        val clip2 = createTestClip(durationMs = 8000L)
        session.setClips(listOf(clip1, clip2), selectedIndex = 10)
        assertEquals(1, session.currentSnapshot.selectedClipIndex)
        assertEquals(clip2.id, session.currentSnapshot.selectedClip?.id)
    }

    @Test
    public fun updateClipsListPreservesSelectionOrFallsBack() {
        val session = EditingSession()
        val clip1 = createTestClip()
        val clip2 = createTestClip()
        session.setClips(listOf(clip1))

        val initialSegId = clip1.segments.first().id
        val updatedClip1 = clip1.copy(fileName = "updated.mp4")
        session.updateClipsList(listOf(updatedClip1))

        assertTrue(session.currentSnapshot.isDirty)
        assertEquals(initialSegId, session.currentSnapshot.selectedSegmentId)
        assertTrue(session.currentSnapshot.canUndo)

        val newSeg = TrimSegment(startMs = 0, endMs = 2000)
        val clipWithNewSeg = clip2.copy(segments = listOf(newSeg))
        session.updateClipsList(listOf(clipWithNewSeg), selectedIndex = 0)
        assertEquals(newSeg.id, session.currentSnapshot.selectedSegmentId)
    }

    @Test
    public fun selectClipAndSelectSegmentBoundaries() {
        val session = EditingSession()
        session.selectClip(0)
        assertEquals(0, session.currentSnapshot.selectedClipIndex)

        val clip1 = createTestClip(durationMs = 5000L)
        val clip2 = createTestClip(durationMs = 8000L)
        session.setClips(listOf(clip1, clip2))

        session.selectClip(-1)
        assertEquals(0, session.currentSnapshot.selectedClipIndex)
        session.selectClip(10)
        assertEquals(0, session.currentSnapshot.selectedClipIndex)

        session.selectClip(1)
        assertEquals(1, session.currentSnapshot.selectedClipIndex)
        assertEquals(clip2.segments.first().id, session.currentSnapshot.selectedSegmentId)

        session.selectSegment(UUID.randomUUID())
        assertEquals(clip2.segments.first().id, session.currentSnapshot.selectedSegmentId)

        session.selectSegment(null)
        assertNull(session.currentSnapshot.selectedSegmentId)
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
    public fun splitSegmentAtFailsIfNoClipOrPositionOutOfBounds() {
        val emptySession = EditingSession()
        assertFalse(emptySession.splitSegmentAt(5000L))

        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))
        assertFalse(session.splitSegmentAt(15000L))
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

        val toggleBack = session.toggleSegmentDiscard(seg1.id)
        assertTrue(toggleBack)
        assertEquals(SegmentAction.KEEP, session.currentSnapshot.selectedClip!!.segments[0].action)
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
    public fun toggleSegmentDiscardFailsOnNoClipOrInvalidSegmentId() {
        val emptySession = EditingSession()
        assertFalse(emptySession.toggleSegmentDiscard(UUID.randomUUID()))

        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))
        assertFalse(session.toggleSegmentDiscard(UUID.randomUUID()))
    }

    @Test
    public fun updateSegmentBoundsCoercesMinDurationAndUpdatesTimes() {
        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))
        val segId = clip.segments.first().id

        session.updateSegmentBounds(segId, startMs = 1000L, endMs = 1050L, minDurationMs = 100L)
        val snapshot = session.currentSnapshot
        val updatedSeg = snapshot.selectedClip!!.segments.first()
        assertEquals(1000L, updatedSeg.startMs)
        assertEquals(1100L, updatedSeg.endMs)
        assertTrue(snapshot.isDirty)

        val emptySession = EditingSession()
        emptySession.updateSegmentBounds(UUID.randomUUID(), startMs = 0L, endMs = 5000L)

        session.updateSegmentBounds(UUID.randomUUID(), startMs = 0L, endMs = 5000L)
    }

    @Test
    public fun coalescedSegmentBoundsEditsUndoAsOneOperation() {
        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))
        val segmentId = clip.segments.first().id

        session.updateSegmentBounds(segmentId, 1000L, 9000L, coalesceHistory = true)
        session.updateSegmentBounds(segmentId, 1500L, 8500L, coalesceHistory = true)
        session.updateSegmentBounds(segmentId, 2000L, 8000L, coalesceHistory = true)
        session.finishSegmentBoundsEdit()

        assertEquals(2000L, session.currentSnapshot.selectedSegment?.startMs)
        assertTrue(session.undo())
        assertEquals(0L, session.currentSnapshot.selectedSegment?.startMs)
        assertEquals(10000L, session.currentSnapshot.selectedSegment?.endMs)
        assertFalse(session.undo())
    }

    @Test
    public fun applySegmentsUpdatesClipAndPreservesOrResetsSelection() {
        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))

        session.applySegments(clip.segments)
        assertFalse(session.currentSnapshot.isDirty)

        val newSeg1 = TrimSegment(startMs = 0, endMs = 3000)
        val newSeg2 = TrimSegment(startMs = 3000, endMs = 10000)
        session.applySegments(listOf(newSeg1, newSeg2))

        assertTrue(session.currentSnapshot.isDirty)
        assertEquals(2, session.currentSnapshot.selectedClip!!.segments.size)
        assertEquals(newSeg1.id, session.currentSnapshot.selectedSegmentId)

        val emptySession = EditingSession()
        emptySession.applySegments(listOf(newSeg1))
    }

    @Test
    public fun reorderClipsHandlesAllIndexPermutations() {
        val session = EditingSession()
        val clip0 = createTestClip(durationMs = 1000L)
        val clip1 = createTestClip(durationMs = 2000L)
        val clip2 = createTestClip(durationMs = 3000L)
        session.setClips(listOf(clip0, clip1, clip2), selectedIndex = 1)

        assertFalse(session.reorderClips(-1, 1))
        assertFalse(session.reorderClips(0, 10))
        assertFalse(session.reorderClips(1, 1))

        assertTrue(session.reorderClips(1, 2))
        assertEquals(2, session.currentSnapshot.selectedClipIndex)
        assertEquals(clip1.id, session.currentSnapshot.selectedClip?.id)

        assertTrue(session.reorderClips(0, 2))
        assertEquals(1, session.currentSnapshot.selectedClipIndex)
        assertEquals(clip1.id, session.currentSnapshot.selectedClip?.id)
    }

    @Test
    public fun undoAndRedoRestorePreviousSnapshotsCorrectly() {
        val session = EditingSession()
        assertFalse(session.undo())
        assertFalse(session.redo())

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

    @Test
    public fun newEditAfterUndoClearsRedoStack() {
        val session = EditingSession()
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))

        session.splitSegmentAt(4000L)
        assertTrue(session.undo())
        assertTrue(session.currentSnapshot.canRedo)

        session.splitSegmentAt(2000L)
        assertFalse(session.currentSnapshot.canRedo)
        assertFalse(session.redo())
    }

    @Test
    public fun markDirtyAndClearDirtyMutatesState() {
        val session = EditingSession()
        session.markDirty()
        assertTrue(session.currentSnapshot.isDirty)

        session.clearDirty()
        assertFalse(session.currentSnapshot.isDirty)
    }

    @Test
    public fun pushHistoryEvictsOldestSnapshotWhenExceedingLimit() {
        val session = EditingSession(historyLimit = 2)
        val clip = createTestClip(durationMs = 10000L)
        session.setClips(listOf(clip))

        session.splitSegmentAt(2000L)
        session.splitSegmentAt(4000L)
        session.splitSegmentAt(6000L)

        assertTrue(session.undo())
        assertTrue(session.undo())
        assertFalse(session.undo())
    }
}
