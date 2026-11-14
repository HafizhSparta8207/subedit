package com.subedit.core.session

import android.net.Uri
import com.subedit.core.domain.AssDialogueEvent
import com.subedit.core.domain.AssProjectItem
import com.subedit.core.domain.AssScriptInfo
import com.subedit.core.domain.AssStyle

/**
 * Immutable in-memory representation of an ASS script.
 */
data class AssScript(
    val scriptInfo: AssScriptInfo = AssScriptInfo(),
    val styles: List<AssStyle> = AssStyle.defaultSingleStyle(),
    val events: List<AssDialogueEvent> = emptyList(),
    val preservedSections: Map<String, List<String>> = scriptInfo.preservedSections,
) {
    fun toProjectItem(id: String, durationBadge: String, dateLabel: String): AssProjectItem {
        return AssProjectItem(
            id = id,
            title = scriptInfo.title,
            durationBadge = durationBadge,
            dateLabel = dateLabel,
            lineCount = events.size,
            scriptInfo = scriptInfo,
            styles = styles,
            events = events,
            preservedSections = preservedSections,
        )
    }
}

/**
 * Snapshot state naskah untuk riwayat Undo / Redo di [ProjectSessionManager].
 */
data class SessionHistorySnapshot(
    val script: AssScript,
    val activeEventId: Int?,
)

/**
 * Active session state shared across the Compose editor UI and the libmpv media player.
 */
data class ProjectSessionState(
    val projectId: String = "",
    val script: AssScript = AssScript(),
    val keyframesUs: List<Long> = emptyList(),
    val activeEventId: Int? = null,
    val playheadUs: Long = 0L,
    val isPlaying: Boolean = false,
    val isMediaLoaded: Boolean = false,
    val isMediaLoading: Boolean = false,
    val loadingProgress: Int = 0,
    val loadingStageMessage: String = "",
    val mediaDurationUs: Long = 0L,
    val videoUri: Uri? = null,
    val audioUri: Uri? = null,
    val contentRevision: Long = 0L,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val selectedEventId: Int?
        get() = activeEventId

    /**
     * Durasi virtual dari waktu `end` maksimum daftar event saat belum ada video/audio (`isMediaLoaded == false`).
     */
    val virtualDurationUs: Long
        get() {
            val maxEventEndUs = script.events.maxOfOrNull { it.endUs } ?: 0L
            return if (mediaDurationUs > 0L) {
                mediaDurationUs
            } else {
                maxOf(maxEventEndUs, 60_000_000L)
            }
        }
}
