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
        )
    }
}

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
    val videoUri: Uri? = null,
    val audioUri: Uri? = null,
    val contentRevision: Long = 0L,
)
