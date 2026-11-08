package com.subedit.core.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Engine parser dan magnetic snapping untuk keyframe video (`.txt` / `.log` Aegisub v1, Crunchyroll, & XviD).
 */
object KeyframeEngine {

    const val DEFAULT_SNAP_THRESHOLD_US: Long = 100_000L // 100 ms
    const val DEFAULT_FALLBACK_FPS: Double = 23.976

    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val DIGITS_REGEX = Regex("""\d+""")

    /**
     * Mem-parsing file keyframe format v1 Crunchyroll / Aegisub menjadi daftar waktu dalam detik (`Double`).
     *
     * Aturan:
     * - Abaikan baris komentar `#...` dan baris kosong.
     * - Jika baris diawali `fps`, tangkap nilai FPS jika bukan `0`. Jika bernilai `0`, gunakan [fallbackFps] (default: `23.976`).
     * - Pada baris data frame (contoh: `171 I -1` atau `171`), ambil angka pertama sebagai integer (`frameNumber`)
     *   dan konversikan ke detik waktu riil: `timeSec = frameNumber / fps`.
     */
    fun parseKeyframes(
        content: String,
        fallbackFps: Double = DEFAULT_FALLBACK_FPS,
    ): List<Double> {
        if (content.isBlank()) return emptyList()

        val validFallbackFps = if (fallbackFps > 0.0) fallbackFps else DEFAULT_FALLBACK_FPS
        var fps: Double = validFallbackFps
        var hasExplicitFpsHeader = false
        val keyframes = ArrayList<Double>(256)

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEach

            if (line.startsWith("fps", ignoreCase = true)) {
                hasExplicitFpsHeader = true
                val fpsString = line.substring(3).trimStart(':', ' ', '\t')
                val parsedFps = fpsString.split(WHITESPACE_REGEX).firstOrNull()?.toDoubleOrNull()
                fps = if (parsedFps != null && parsedFps > 0.0) parsedFps else validFallbackFps
                return@forEach
            }

            // Dukungan log XviD / DivX: "type I" pada frame ke-N
            if (line.startsWith("i ", ignoreCase = true) || line.contains("type:i", ignoreCase = true)) {
                val frameNum = DIGITS_REGEX.find(line)?.value?.toLongOrNull()
                if (frameNum != null && frameNum >= 0L) {
                    keyframes.add(frameNum.toDouble() / fps)
                }
                return@forEach
            }

            if (':' in line) {
                SubTime.parseAssOrNull(line)?.let { us ->
                    keyframes.add(us.toDouble() / SubTime.US_PER_SEC.toDouble())
                }
                return@forEach
            }

            val parts = line.split(WHITESPACE_REGEX)
            val firstToken = parts.firstOrNull().orEmpty()

            // Jika tanpa header fps dan token pertama berupa angka desimal detik murni
            if (!hasExplicitFpsHeader && parts.size == 1 && '.' in firstToken) {
                val seconds = firstToken.toDoubleOrNull()
                if (seconds != null && seconds >= 0.0) {
                    keyframes.add(seconds)
                }
                return@forEach
            }

            // Baris data frame (contoh: "0 I -1", "4 I -1", "171 I -1", atau "171")
            val frameNumber = firstToken.toLongOrNull()
            if (frameNumber != null && frameNumber >= 0L) {
                val timeSec = frameNumber.toDouble() / fps
                keyframes.add(timeSec)
            }
        }

        if (keyframes.size <= 1) return keyframes
        keyframes.sort()
        return keyframes
    }

    /**
     * Mem-parsing file keyframe (`.txt` / `.log`) menjadi daftar mikrodetik (`Long`) yang terurut dan unik.
     */
    fun parseKeyframesTxt(
        content: String,
        fallbackFps: Double = DEFAULT_FALLBACK_FPS,
    ): List<Long> {
        val secondsList = parseKeyframes(content, fallbackFps)
        if (secondsList.isEmpty()) return emptyList()

        val results = ArrayList<Long>(secondsList.size)
        for (timeSec in secondsList) {
            results.add((timeSec * SubTime.US_PER_SEC).roundToLong())
        }

        if (results.size <= 1) return results
        results.sort()
        return deduplicateSorted(results)
    }

    fun frameToMicroseconds(frameNumber: Long, fps: Double): Long {
        require(frameNumber >= 0L) { "Nomor frame tidak boleh negatif: $frameNumber" }
        require(fps > 0.0) { "FPS harus lebih besar dari 0: $fps" }
        val timeSec = frameNumber.toDouble() / fps
        return (timeSec * SubTime.US_PER_SEC).roundToLong()
    }

    fun snapToKeyframe(
        timeUs: Long,
        keyframesUs: List<Long>,
        thresholdUs: Long = DEFAULT_SNAP_THRESHOLD_US,
    ): Long = findSnappedKeyframeOrNull(timeUs, keyframesUs, thresholdUs) ?: timeUs

    fun findSnappedKeyframeOrNull(
        timeUs: Long,
        keyframesUs: List<Long>,
        thresholdUs: Long = DEFAULT_SNAP_THRESHOLD_US,
    ): Long? {
        if (keyframesUs.isEmpty() || thresholdUs < 0L) return null

        val idx = keyframesUs.binarySearch(timeUs)
        if (idx >= 0) return keyframesUs[idx]

        val insertionPoint = -idx - 1
        var bestKeyframe = Long.MIN_VALUE
        var minDistance = Long.MAX_VALUE

        if (insertionPoint - 1 >= 0) {
            val leftKf = keyframesUs[insertionPoint - 1]
            val dist = abs(timeUs - leftKf)
            if (dist < minDistance) {
                minDistance = dist
                bestKeyframe = leftKf
            }
        }

        if (insertionPoint < keyframesUs.size) {
            val rightKf = keyframesUs[insertionPoint]
            val dist = abs(rightKf - timeUs)
            if (dist < minDistance) {
                minDistance = dist
                bestKeyframe = rightKf
            }
        }

        return if (minDistance <= thresholdUs) bestKeyframe else null
    }

    private fun deduplicateSorted(sorted: List<Long>): List<Long> {
        val unique = ArrayList<Long>(sorted.size)
        var last = Long.MIN_VALUE
        for (value in sorted) {
            if (unique.isEmpty() || value != last) {
                unique.add(value)
                last = value
            }
        }
        return unique
    }
}
