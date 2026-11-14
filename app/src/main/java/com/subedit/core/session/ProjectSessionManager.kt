package com.subedit.core.session

import android.net.Uri
import com.subedit.core.domain.ActiveSubtitleResolver
import com.subedit.core.domain.AssDialogueEvent
import com.subedit.core.domain.AssProjectItem
import com.subedit.core.domain.AssStyle
import com.subedit.core.domain.SubtitleParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ProjectSessionManager {
    private const val MAX_HISTORY_SIZE = 40

    private val _sessionState = MutableStateFlow(ProjectSessionState())
    val sessionState: StateFlow<ProjectSessionState> = _sessionState.asStateFlow()

    private val undoStack = ArrayDeque<SessionHistorySnapshot>()
    private val redoStack = ArrayDeque<SessionHistorySnapshot>()

    @Synchronized
    fun initializeSession(
        project: AssProjectItem,
        videoUri: Uri? = null,
        audioUri: Uri? = null,
        preserveSelection: Boolean = true,
    ) {
        undoStack.clear()
        redoStack.clear()
        val initialPlayhead = project.events.firstOrNull()?.startUs ?: 0L
        val initialActiveId = if (preserveSelection) project.events.firstOrNull()?.id else null
        val hasMedia = (videoUri != null || audioUri != null)
        _sessionState.value = ProjectSessionState(
            projectId = project.id,
            script = AssScript(
                scriptInfo = project.scriptInfo,
                styles = project.styles.ifEmpty { AssStyle.defaultSingleStyle() },
                events = project.events,
                preservedSections = project.preservedSections,
            ),
            keyframesUs = project.keyframesUs,
            activeEventId = initialActiveId,
            playheadUs = initialPlayhead,
            isPlaying = false,
            isMediaLoaded = false,
            isMediaLoading = hasMedia,
            loadingProgress = if (hasMedia) 5 else 0,
            loadingStageMessage = if (hasMedia) "Membuka file descriptor & menginisialisasi dekoder..." else "",
            mediaDurationUs = 0L,
            videoUri = videoUri,
            audioUri = audioUri,
            contentRevision = 1L,
            canUndo = false,
            canRedo = false,
        )
    }

    @Synchronized
    fun applyImportedDocument(parsedDoc: SubtitleParser.ParsedSubtitleDocument) {
        undoStack.clear()
        redoStack.clear()
        _sessionState.update { current ->
            current.copy(
                script = AssScript(
                    scriptInfo = parsedDoc.scriptInfo,
                    styles = parsedDoc.styles.ifEmpty { AssStyle.defaultSingleStyle() },
                    events = parsedDoc.events,
                    preservedSections = parsedDoc.preservedSections,
                ),
                activeEventId = null,
                contentRevision = current.contentRevision + 1L,
                canUndo = false,
                canRedo = false,
            )
        }
    }

    fun setMediaLoaded(loaded: Boolean) {
        _sessionState.update { it.copy(isMediaLoaded = loaded) }
    }

    fun isMediaLoaded(): Boolean = _sessionState.value.isMediaLoaded

    @Synchronized
    fun resetUndoHistory() {
        undoStack.clear()
        redoStack.clear()
        _sessionState.update {
            it.copy(canUndo = false, canRedo = false)
        }
    }

    fun updatePlayhead(timeUs: Long): AssDialogueEvent? {
        val currentState = _sessionState.value
        val activeLine = ActiveSubtitleResolver.findActiveDialogueUs(currentState.script.events, timeUs)
        _sessionState.update { current ->
            current.copy(
                playheadUs = timeUs,
                activeEventId = activeLine?.id ?: current.activeEventId,
            )
        }
        return activeLine
    }

    fun setActiveEventId(id: Int?) {
        _sessionState.update { it.copy(activeEventId = id) }
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        _sessionState.update { it.copy(isPlaying = isPlaying) }
    }

    fun setMediaLoading(isLoading: Boolean, progress: Int? = null, stageMessage: String? = null) {
        _sessionState.update { current ->
            current.copy(
                isMediaLoading = isLoading,
                loadingProgress = progress?.coerceIn(0, 100) ?: if (!isLoading) 100 else current.loadingProgress,
                loadingStageMessage = stageMessage ?: if (!isLoading) "" else current.loadingStageMessage,
            )
        }
    }

    fun updateLoadingProgress(progress: Int, stageMessage: String? = null) {
        _sessionState.update { current ->
            val clamped = progress.coerceIn(0, 100)
            val effectiveProgress = if (current.isMediaLoading) maxOf(current.loadingProgress, clamped) else clamped
            current.copy(
                loadingProgress = effectiveProgress,
                loadingStageMessage = stageMessage ?: current.loadingStageMessage,
            )
        }
    }

    @Synchronized
    fun addEvent(
        initialText: String = "",
        startUs: Long = _sessionState.value.playheadUs,
        endUs: Long = startUs + 2_000_000L,
        styleName: String? = null,
    ): AssDialogueEvent {
        pushUndoSnapshot()
        val current = _sessionState.value
        val nextId = (current.script.events.maxOfOrNull { it.id } ?: 0) + 1
        val resolvedStyle = styleName ?: current.script.styles.firstOrNull()?.name ?: "Default"
        val newEvent = AssDialogueEvent(
            id = nextId,
            startUs = startUs.coerceAtLeast(0L),
            endUs = endUs.coerceAtLeast(startUs + 10_000L),
            isComment = false,
            style = resolvedStyle,
            actor = "",
            effect = "",
            rawText = initialText,
        )
        _sessionState.update { state ->
            val updatedEvents = (state.script.events + newEvent).sortedBy { it.startUs }
            state.copy(
                script = state.script.copy(events = updatedEvents),
                activeEventId = newEvent.id,
                contentRevision = state.contentRevision + 1L,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        return newEvent
    }

    @Synchronized
    fun deleteEvent(eventId: Int? = _sessionState.value.activeEventId): Int? {
        val targetId = eventId ?: return _sessionState.value.activeEventId
        val current = _sessionState.value
        if (current.script.events.none { it.id == targetId }) return current.activeEventId

        pushUndoSnapshot()
        var nextActiveId: Int? = null
        _sessionState.update { state ->
            val remaining = state.script.events.filterNot { it.id == targetId }
            nextActiveId = ActiveSubtitleResolver.findActiveDialogueUs(remaining, state.playheadUs)?.id
            state.copy(
                script = state.script.copy(events = remaining),
                activeEventId = nextActiveId,
                contentRevision = state.contentRevision + 1L,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        return nextActiveId
    }

    fun setMediaDuration(durationUs: Long) {
        _sessionState.update { it.copy(mediaDurationUs = durationUs.coerceAtLeast(0L)) }
    }

    @Synchronized
    fun pushUndoSnapshot() {
        val current = _sessionState.value
        undoStack.addLast(
            SessionHistorySnapshot(
                script = current.script,
                activeEventId = current.activeEventId,
            )
        )
        if (undoStack.size > MAX_HISTORY_SIZE) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        _sessionState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = false,
            )
        }
    }

    @Synchronized
    fun undo(): SessionHistorySnapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        val current = _sessionState.value
        redoStack.addLast(
            SessionHistorySnapshot(
                script = current.script,
                activeEventId = current.activeEventId,
            )
        )
        _sessionState.update { state ->
            state.copy(
                script = previous.script,
                activeEventId = previous.activeEventId,
                contentRevision = state.contentRevision + 1L,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        return previous
    }

    @Synchronized
    fun redo(): SessionHistorySnapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        val current = _sessionState.value
        undoStack.addLast(
            SessionHistorySnapshot(
                script = current.script,
                activeEventId = current.activeEventId,
            )
        )
        _sessionState.update { state ->
            state.copy(
                script = next.script,
                activeEventId = next.activeEventId,
                contentRevision = state.contentRevision + 1L,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        return next
    }

    @Synchronized
    fun updateScript(recordUndo: Boolean = false, transform: (AssScript) -> AssScript) {
        if (recordUndo) {
            pushUndoSnapshot()
        }
        _sessionState.update { current ->
            val updated = transform(current.script)
            current.copy(
                script = updated,
                contentRevision = current.contentRevision + 1L,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
    }

    @Synchronized
    fun updateActiveDialogue(
        recordUndo: Boolean = false,
        transform: (AssDialogueEvent) -> AssDialogueEvent,
    ): AssDialogueEvent? {
        val activeId = _sessionState.value.activeEventId ?: return null
        var updatedEvent: AssDialogueEvent? = null
        updateScript(recordUndo = recordUndo) { script ->
            val list = script.events.toMutableList()
            val idx = list.indexOfFirst { it.id == activeId }
            if (idx != -1) {
                val modified = transform(list[idx])
                list[idx] = modified
                updatedEvent = modified
            }
            script.copy(events = list)
        }
        return updatedEvent
    }

    @Synchronized
    fun updateActiveDialogueText(newText: String, recordUndo: Boolean = false): AssDialogueEvent? {
        return updateActiveDialogue(recordUndo = recordUndo) { it.copy(rawText = newText) }
    }

    fun getActiveDialogue(): AssDialogueEvent? {
        val state = _sessionState.value
        val id = state.activeEventId ?: return null
        return state.script.events.firstOrNull { it.id == id }
    }

    fun getCurrentScript(): AssScript = _sessionState.value.script

    @Synchronized
    fun clearSession() {
        undoStack.clear()
        redoStack.clear()
        _sessionState.value = ProjectSessionState()
    }
}
