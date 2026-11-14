package com.subedit.core.domain

object ActiveSubtitleResolver {
    fun findActiveDialogue(events: List<AssEvent>, currentPositionMs: Long): AssEvent? {
        return events.firstOrNull { event ->
            !event.comment &&
            event.start.millis <= currentPositionMs &&
            currentPositionMs < event.end.millis
        }
    }

    fun findActiveDialogueUs(events: List<AssDialogueEvent>, currentPositionUs: Long): AssDialogueEvent? {
        return events.firstOrNull { event ->
            !event.isComment &&
            event.startUs <= currentPositionUs &&
            currentPositionUs < event.endUs
        }
    }

    fun isEventActive(event: AssEvent, currentPositionMs: Long): Boolean {
        return !event.comment &&
            event.start.millis <= currentPositionMs &&
            currentPositionMs < event.end.millis
    }

    fun isEventActiveUs(event: AssDialogueEvent, currentPositionUs: Long): Boolean {
        return !event.isComment &&
            event.startUs <= currentPositionUs &&
            currentPositionUs < event.endUs
    }
}
