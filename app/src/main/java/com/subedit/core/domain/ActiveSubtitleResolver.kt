package com.subedit.core.domain

/**
 * Pure function to resolve the active dialogue line based on the video playhead position.
 * Implements the standard ASS v4+ and Aegisub timing rule: start <= time < end.
 */
object ActiveSubtitleResolver {

    /**
     * Finds the active dialogue line given the playhead position in milliseconds.
     */
    fun findActiveDialogue(events: List<AssEvent>, currentPositionMs: Long): AssEvent? {
        return events.firstOrNull { event ->
            !event.comment &&
            event.start.millis <= currentPositionMs &&
            currentPositionMs < event.end.millis
        }
    }

    /**
     * Finds the active dialogue line given the playhead position in microseconds.
     */
    fun findActiveDialogueUs(events: List<AssDialogueEvent>, currentPositionUs: Long): AssDialogueEvent? {
        return events.firstOrNull { event ->
            !event.isComment &&
            event.startUs <= currentPositionUs &&
            currentPositionUs < event.endUs
        }
    }

    /**
     * Checks if a single dialogue event is active at the specified timestamp in milliseconds.
     */
    fun isEventActive(event: AssEvent, currentPositionMs: Long): Boolean {
        return !event.comment &&
            event.start.millis <= currentPositionMs &&
            currentPositionMs < event.end.millis
    }

    /**
     * Checks if a single dialogue event is active at the specified timestamp in microseconds.
     */
    fun isEventActiveUs(event: AssDialogueEvent, currentPositionUs: Long): Boolean {
        return !event.isComment &&
            event.startUs <= currentPositionUs &&
            currentPositionUs < event.endUs
    }
}
