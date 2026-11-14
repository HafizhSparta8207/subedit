package com.subedit.core.domain

object TimingConstraints {
    const val MIN_DURATION_MS: Long = 10L
    const val MIN_DURATION_US: Long = 10_000L

    fun clampStartMs(
        proposedStartMs: Long,
        currentEndMs: Long,
        minDurationMs: Long = MIN_DURATION_MS,
    ): Long {
        val safeMinDur = minDurationMs.coerceAtLeast(MIN_DURATION_MS)
        val maxAllowedStart = (currentEndMs - safeMinDur).coerceAtLeast(0L)
        return proposedStartMs.coerceIn(0L, maxAllowedStart)
    }

    fun clampEndMs(
        currentStartMs: Long,
        proposedEndMs: Long,
        durationMs: Long = 0L,
        minDurationMs: Long = MIN_DURATION_MS,
    ): Long {
        val safeStart = currentStartMs.coerceAtLeast(0L)
        val safeMinDur = minDurationMs.coerceAtLeast(MIN_DURATION_MS)
        val minAllowedEnd = safeStart + safeMinDur
        return if (durationMs > minAllowedEnd) {
            proposedEndMs.coerceIn(minAllowedEnd, durationMs)
        } else {
            proposedEndMs.coerceAtLeast(minAllowedEnd)
        }
    }

    fun clampStartUs(
        proposedStartUs: Long,
        currentEndUs: Long,
        minDurationUs: Long = MIN_DURATION_US,
    ): Long {
        val safeMinDur = minDurationUs.coerceAtLeast(MIN_DURATION_US)
        val maxAllowedStart = (currentEndUs - safeMinDur).coerceAtLeast(0L)
        return proposedStartUs.coerceIn(0L, maxAllowedStart)
    }

    fun clampEndUs(
        currentStartUs: Long,
        proposedEndUs: Long,
        durationUs: Long = 0L,
        minDurationUs: Long = MIN_DURATION_US,
    ): Long {
        val safeStart = currentStartUs.coerceAtLeast(0L)
        val safeMinDur = minDurationUs.coerceAtLeast(MIN_DURATION_US)
        val minAllowedEnd = safeStart + safeMinDur
        return if (durationUs > minAllowedEnd) {
            proposedEndUs.coerceIn(minAllowedEnd, durationUs)
        } else {
            proposedEndUs.coerceAtLeast(minAllowedEnd)
        }
    }
}
