package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.TrimSegment
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class HistoryManagerTest {

    @Test
    fun testUndoRedo() {
        val manager = HistoryManager(limit = 10)
        
        val clip1 = createMockClip("uri1")
        val clip2 = createMockClip("uri2")
        
        val state1 = listOf(clip1)
        val state2 = listOf(clip1, clip2)
        
        // Save initial state
        manager.save(state1)
        
        // Undo should return state1 if current is state2
        val undone = manager.undo(state2)
        assertEquals(state1, undone)
        assertTrue(manager.canRedo)
        
        // Redo should return state2
        val redone = manager.redo(state1)
        assertEquals(state2, redone)
        assertFalse(manager.canRedo)
    }

    @Test
    fun testHistoryLimit() {
        val manager = HistoryManager(limit = 2)
        val state1 = listOf(createMockClip("1"))
        val state2 = listOf(createMockClip("2"))
        val state3 = listOf(createMockClip("3"))
        val state4 = listOf(createMockClip("4"))
        
        manager.save(state1)
        manager.save(state2)
        manager.save(state3) // state1 should be removed
        
        manager.undo(state4) // returns 3
        manager.undo(state3) // returns 2
        assertNull(manager.undo(state2)) // history should be empty now
    }

    private fun createMockClip(uri: String) = MediaClip(
        uri = uri,
        fileName = "file",
        durationMs = 1000,
        width = 0,
        height = 0,
        videoMime = null,
        audioMime = null,
        sampleRate = 0,
        channelCount = 0,
        fps = 0f,
        rotation = 0,
        isAudioOnly = false,
        segments = listOf(TrimSegment(startMs = 0, endMs = 1000))
    )
}
