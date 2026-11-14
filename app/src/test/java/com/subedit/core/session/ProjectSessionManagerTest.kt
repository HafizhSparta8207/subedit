package com.subedit.core.session

import com.subedit.core.domain.AssDialogueEvent
import com.subedit.core.domain.AssProjectItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ProjectSessionManagerTest {

    @Before
    fun setup() {
        ProjectSessionManager.clearSession()
    }

    @Test
    fun projectSessionManager_initializesAndResolvesActiveLine() {
        val project = AssProjectItem.createZeroStateProject(
            projectIndex = 1,
            title = "Session_Test",
        ).copy(
            events = listOf(
                AssDialogueEvent(
                    id = 101,
                    startUs = 1_000_000L,
                    endUs = 3_000_000L,
                    rawText = "First line",
                ),
                AssDialogueEvent(
                    id = 102,
                    startUs = 3_500_000L,
                    endUs = 6_000_000L,
                    rawText = "Second line",
                ),
            )
        )

        ProjectSessionManager.initializeSession(project)

        val state = ProjectSessionManager.sessionState.value
        assertEquals("Session_Test", state.script.scriptInfo.title)
        assertEquals(2, state.script.events.size)
        assertEquals(101, state.activeEventId)
        assertEquals(1_000_000L, state.playheadUs)

        // Update playhead to second line
        val activeLine = ProjectSessionManager.updatePlayhead(4_000_000L)
        assertNotNull(activeLine)
        assertEquals(102, activeLine?.id)
        assertEquals(102, ProjectSessionManager.sessionState.value.activeEventId)

        // Update playhead to empty region
        val emptyLine = ProjectSessionManager.updatePlayhead(7_000_000L)
        assertNull(emptyLine)
    }

    @Test
    fun projectSessionManager_updatesScriptContentAndIncrementsRevision() {
        val project = AssProjectItem.createZeroStateProject(1)
        ProjectSessionManager.initializeSession(project)

        val initialRevision = ProjectSessionManager.sessionState.value.contentRevision

        ProjectSessionManager.updateScript { current ->
            current.copy(
                events = listOf(
                    AssDialogueEvent(
                        id = 1,
                        startUs = 0L,
                        endUs = 2_000_000L,
                        rawText = "Added line",
                    )
                )
            )
        }

        val updatedState = ProjectSessionManager.sessionState.value
        assertEquals(1, updatedState.script.events.size)
        assertEquals("Added line", updatedState.script.events[0].rawText)
        assertEquals(initialRevision + 1L, updatedState.contentRevision)
    }

    @Test
    fun projectSessionManager_playheadDoesNotIncrementContentRevision_andMediaLoadingStateUpdates() {
        val project = AssProjectItem.createZeroStateProject(1).copy(
            events = listOf(
                AssDialogueEvent(
                    id = 10,
                    startUs = 1_000_000L,
                    endUs = 4_000_000L,
                    rawText = "Initial text",
                )
            )
        )
        ProjectSessionManager.initializeSession(project)
        val initialRevision = ProjectSessionManager.sessionState.value.contentRevision

        ProjectSessionManager.setMediaLoading(true)
        assertEquals(true, ProjectSessionManager.sessionState.value.isMediaLoading)

        ProjectSessionManager.setMediaLoading(false)
        assertEquals(false, ProjectSessionManager.sessionState.value.isMediaLoading)

        // Pergerakan playhead tidak boleh mengubah contentRevision (agar sub-reload tidak dipanggil saat seek)
        ProjectSessionManager.updatePlayhead(2_500_000L)
        assertEquals(initialRevision, ProjectSessionManager.sessionState.value.contentRevision)
    }

    @Test
    fun projectSessionManager_supportsTwoWayTextEditAndUndoRedoWithRevisionIncrement() {
        val project = AssProjectItem.createZeroStateProject(1).copy(
            events = listOf(
                AssDialogueEvent(
                    id = 5,
                    startUs = 1_000_000L,
                    endUs = 3_000_000L,
                    rawText = "Teks Awal",
                )
            )
        )
        ProjectSessionManager.initializeSession(project)

        // Simpan snapshot undo lalu perbarui teks dialog aktif
        ProjectSessionManager.updateActiveDialogueText("Teks Baru Diedit", recordUndo = true)
        val afterEdit = ProjectSessionManager.sessionState.value
        assertEquals("Teks Baru Diedit", afterEdit.script.events.first().rawText)
        assertEquals(true, afterEdit.canUndo)
        assertEquals(false, afterEdit.canRedo)
        val revAfterEdit = afterEdit.contentRevision

        // Undo harus mengembalikan teks sebelumnya dan menaikkan contentRevision untuk memicu sub-reload
        val undone = ProjectSessionManager.undo()
        assertNotNull(undone)
        val afterUndo = ProjectSessionManager.sessionState.value
        assertEquals("Teks Awal", afterUndo.script.events.first().rawText)
        assertEquals(false, afterUndo.canUndo)
        assertEquals(true, afterUndo.canRedo)
        assertEquals(revAfterEdit + 1L, afterUndo.contentRevision)

        // Redo harus menerapkan kembali perubahan dan menaikkan contentRevision untuk memicu sub-reload
        val redone = ProjectSessionManager.redo()
        assertNotNull(redone)
        val afterRedo = ProjectSessionManager.sessionState.value
        assertEquals("Teks Baru Diedit", afterRedo.script.events.first().rawText)
        assertEquals(true, afterRedo.canUndo)
        assertEquals(false, afterRedo.canRedo)
        assertEquals(revAfterEdit + 2L, afterRedo.contentRevision)
    }
}
