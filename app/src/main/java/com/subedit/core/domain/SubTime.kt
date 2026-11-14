package com.subedit.core.domain

@JvmInline
value class SubTime(val microseconds: Long) : Comparable<SubTime> {
    val centiseconds: Long
        get() = (microseconds + (US_PER_CS / 2L)) / US_PER_CS

    val milliseconds: Long
        get() = microseconds / US_PER_MS

    val millis: Long
        get() = milliseconds

    val secondsFloat: Float
        get() = microseconds.toFloat() / US_PER_SEC.toFloat()

    operator fun plus(other: SubTime): SubTime = SubTime(microseconds + other.microseconds)
    operator fun minus(other: SubTime): SubTime = SubTime(microseconds - other.microseconds)

    fun coerceAtLeast(minimumValue: SubTime): SubTime =
        SubTime(microseconds.coerceAtLeast(minimumValue.microseconds))

    fun coerceIn(minimumValue: SubTime, maximumValue: SubTime): SubTime =
        SubTime(microseconds.coerceIn(minimumValue.microseconds, maximumValue.microseconds))

    override fun compareTo(other: SubTime): Int = microseconds.compareTo(other.microseconds)

    fun toAssString(): String = formatAss(microseconds)

    override fun toString(): String = toAssString()

    companion object {
        const val US_PER_MS: Long = 1_000L
        const val US_PER_CS: Long = 10_000L
        const val US_PER_SEC: Long = 1_000_000L
        const val US_PER_MIN: Long = 60L * US_PER_SEC
        const val US_PER_HOUR: Long = 60L * US_PER_MIN

        val ZERO = SubTime(0L)

        fun formatAss(timeUs: Long): String {
            val clampedUs = if (timeUs < 0L) 0L else timeUs
            val totalCs = (clampedUs + (US_PER_CS / 2L)) / US_PER_CS

            val cs = totalCs % 100L
            val totalSeconds = totalCs / 100L
            val seconds = totalSeconds % 60L
            val totalMinutes = totalSeconds / 60L
            val minutes = totalMinutes % 60L
            val hours = totalMinutes / 60L

            return buildString(10) {
                append(hours)
                append(':')
                if (minutes < 10L) append('0')
                append(minutes)
                append(':')
                if (seconds < 10L) append('0')
                append(seconds)
                append('.')
                if (cs < 10L) append('0')
                append(cs)
            }
        }

        fun formatDurationSeconds(durationUs: Long): String {
            val clamped = durationUs.coerceAtLeast(0L)
            val totalCs = (clamped + (US_PER_CS / 2L)) / US_PER_CS
            val secs = totalCs / 100L
            val cs = totalCs % 100L
            return buildString(6) {
                append(secs)
                append('.')
                if (cs < 10L) append('0')
                append(cs)
                append('s')
            }
        }

        fun parseAss(rawTime: String): Long {
            val trimmed = rawTime.trim()
            require(trimmed.isNotEmpty()) { "String waktu tidak boleh kosong." }

            val firstColon = trimmed.indexOf(':')
            val secondColon = if (firstColon != -1) trimmed.indexOf(':', firstColon + 1) else -1
            val dotIndex = if (secondColon != -1) {
                val dot = trimmed.indexOf('.', secondColon + 1)
                if (dot != -1) dot else trimmed.indexOf(',', secondColon + 1)
            } else {
                -1
            }

            require(firstColon > 0 && secondColon > firstColon + 1 && dotIndex > secondColon + 1) {
                "Format waktu tidak valid: '$rawTime' (harus H:MM:SS.CS atau HH:MM:SS,mmm)"
            }

            val hours = parsePositiveLongSlice(trimmed, 0, firstColon, rawTime)
            val minutes = parsePositiveLongSlice(trimmed, firstColon + 1, secondColon, rawTime)
            val seconds = parsePositiveLongSlice(trimmed, secondColon + 1, dotIndex, rawTime)

            val fractionStart = dotIndex + 1
            val fractionLength = trimmed.length - fractionStart
            require(fractionLength in 1..6) {
                "Bagian fraksi detik tidak valid pada: '$rawTime'"
            }
            val fractionRaw = parsePositiveLongSlice(trimmed, fractionStart, trimmed.length, rawTime)

            require(minutes in 0L..59L && seconds in 0L..59L) {
                "Menit atau detik di luar rentang 0..59 pada: '$rawTime'"
            }

            val fractionUs = when (fractionLength) {
                1 -> fractionRaw * 100_000L
                2 -> fractionRaw * US_PER_CS
                3 -> fractionRaw * US_PER_MS
                4 -> fractionRaw * 100L
                5 -> fractionRaw * 10L
                else -> fractionRaw
            }

            return (hours * US_PER_HOUR) +
                (minutes * US_PER_MIN) +
                (seconds * US_PER_SEC) +
                fractionUs
        }

        fun parseAssOrNull(rawTime: String): Long? =
            runCatching { parseAss(rawTime) }.getOrNull()

        fun fromAssString(assTime: String): SubTime = SubTime(parseAss(assTime))
        fun fromCentiseconds(cs: Long): SubTime = SubTime(cs * US_PER_CS)
        fun fromMilliseconds(ms: Long): SubTime = SubTime(ms * US_PER_MS)

        private fun parsePositiveLongSlice(
            source: String,
            startIndex: Int,
            endIndex: Int,
            fullInput: String,
        ): Long {
            require(startIndex < endIndex) { "Bagian angka kosong pada waktu: '$fullInput'" }
            var result = 0L
            for (i in startIndex until endIndex) {
                val ch = source[i]
                if (ch !in '0'..'9') {
                    throw IllegalArgumentException("Karakter non-digit '$ch' pada waktu: '$fullInput'")
                }
                result = (result * 10L) + (ch - '0')
            }
            return result
        }
    }
}
