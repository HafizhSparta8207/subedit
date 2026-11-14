package com.subedit.core.domain

/**
 * Validasi batas waktu dialog untuk mencegah durasi baris bernilai nol, negatif,
 * atau melampaui batas durasi media.
 *
 * Aturan invariants:
 * - `start >= 0`
 * - `end >= start + 10ms` (10_000 mikrodetik)
 * - `end <= durationMs` (bila durasi media diketahui > 0)
 */
object TimingConstraints {

    const val MIN_DURATION_MS: Long = 10L
    const val MIN_DURATION_US: Long = 10_000L

    /**
     * Memvalidasi batas waktu mulai (Lead-In) dalam satuan milidetik:
     * memastikan `start >= 0` dan `start <= end - 10ms`.
     */
    fun clampStartMs(
        proposedStartMs: Long,
        currentEndMs: Long,
        minDurationMs: Long = MIN_DURATION_MS,
    ): Long {
        val safeMinDur = minDurationMs.coerceAtLeast(MIN_DURATION_MS)
        val maxAllowedStart = (currentEndMs - safeMinDur).coerceAtLeast(0L)
        return proposedStartMs.coerceIn(0L, maxAllowedStart)
    }

    /**
     * Memvalidasi batas waktu selesai (Lead-Out) dalam satuan milidetik:
     * memastikan `end >= start + 10ms` dan `end <= durationMs` (jika `durationMs > 0`).
     */
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

    /**
     * Memvalidasi batas waktu mulai (Lead-In) dalam satuan mikrodetik:
     * memastikan `startUs >= 0` dan `startUs <= endUs - 10_000Us` (10ms).
     */
    fun clampStartUs(
        proposedStartUs: Long,
        currentEndUs: Long,
        minDurationUs: Long = MIN_DURATION_US,
    ): Long {
        val safeMinDur = minDurationUs.coerceAtLeast(MIN_DURATION_US)
        val maxAllowedStart = (currentEndUs - safeMinDur).coerceAtLeast(0L)
        return proposedStartUs.coerceIn(0L, maxAllowedStart)
    }

    /**
     * Memvalidasi batas waktu selesai (Lead-Out) dalam satuan mikrodetik:
     * memastikan `endUs >= startUs + 10_000Us` (10ms) dan `endUs <= durationUs` (jika `durationUs > 0`).
     */
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
