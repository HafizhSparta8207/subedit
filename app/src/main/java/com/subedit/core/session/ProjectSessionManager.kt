package com.subedit.core.session

import android.net.Uri
import com.subedit.core.domain.ActiveSubtitleResolver
import com.subedit.core.domain.AssDialogueEvent
import com.subedit.core.domain.AssProjectItem
import com.subedit.core.domain.AssStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for the active subtitle project session in memory.
 * Ensures the Jetpack Compose editor UI and the libmpv player layer observe
 * and operate on an identical memory instance.
 */
object ProjectSessionManager {

    private val _sessionState = MutableStateFlow(ProjectSessionState())
    val sessionState: StateFlow<ProjectSessionState> = _sessionState.asStateFlow()

    /**
     * Initializes or resets the session from an [AssProjectItem] instance.
     */
    fun initializeSession(
        project: AssProjectItem,
        videoUri: Uri? = null,
        audioUri: Uri? = null,
    ) {
        val initialPlayhead = project.events.firstOrNull()?.startUs ?: 0L
        val initialActiveId = project.events.firstOrNull()?.id
        _sessionState.value = ProjectSessionState(
            projectId = project.id,
            script = AssScript(
                scriptInfo = project.scriptInfo,
                styles = project.styles.ifEmpty { AssStyle.defaultSingleStyle() },
                events = project.events,
            ),
            keyframesUs = project.keyframesUs,
            activeEventId = initialActiveId,
            playheadUs = initialPlayhead,
            isPlaying = false,
            videoUri = videoUri,
            audioUri = audioUri,
            contentRevision = 1L,
        )
    }

    /**
     * Updates playhead timestamp and syncs active dialogue line using [ActiveSubtitleResolver].
     * Returns the active dialogue event if one matches the new timestamp.
     */
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

    /**
     * Sets the active dialogue event ID explicitly (e.g. from table or timeline selection).
     */
    fun setActiveEventId(id: Int?) {
        _sessionState.update { it.copy(activeEventId = id) }
    }

    /**
     * Updates playback playing/paused status.
     */
    fun updatePlaybackState(isPlaying: Boolean) {
        _sessionState.update { it.copy(isPlaying = isPlaying) }
    }

    /**
     * Updates the script content (metadata, styles, or dialogue lines) and increments the content revision.
     */
    fun updateScript(transform: (AssScript) -> AssScript) {
        _sessionState.update { current ->
            val updated = transform(current.script)
            current.copy(
                script = updated,
                contentRevision = current.contentRevision + 1L,
            )
        }
    }

    /**
     * Returns the currently active dialogue event.
     */
    fun getActiveDialogue(): AssDialogueEvent? {
        val state = _sessionState.value
        val id = state.activeEventId ?: return null
        return state.script.events.firstOrNull { it.id == id }
    }

    /**
     * Returns the current script snapshot.
     */
    fun getCurrentScript(): AssScript = _sessionState.value.script

    /**
     * Resets the active session.
     */
    fun clearSession() {
        _sessionState.value = ProjectSessionState()
    }
}
