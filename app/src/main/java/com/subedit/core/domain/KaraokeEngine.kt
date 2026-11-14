package com.subedit.core.domain

enum class KaraokeSplitMode {
    WORD,
    SYLLABLE,
}

data class KaraokeSegment(
    val index: Int,
    val text: String,
    val durationCs: Long,
    val startOffsetUs: Long,
    val endOffsetUs: Long,
)

object KaraokeEngine {
    private val karaokeTagRegex = Regex("""\{\\k[fo]?\s*(\d+)\}""", RegexOption.IGNORE_CASE)
    private val vowels = setOf('a', 'i', 'u', 'e', 'o', 'A', 'I', 'U', 'E', 'O')

    fun generateEvenKaraokeAss(
        rawText: String,
        startUs: Long,
        endUs: Long,
        mode: KaraokeSplitMode = KaraokeSplitMode.WORD,
    ): String {
        val durationUs = (endUs - startUs).coerceAtLeast(0L)
        return generateEvenKaraokeAss(
            rawText = rawText,
            totalDurationUs = durationUs,
            mode = mode,
        )
    }

    fun generateEvenKaraokeAss(
        rawText: String,
        totalDurationUs: Long,
        mode: KaraokeSplitMode = KaraokeSplitMode.WORD,
    ): String {
        val leadingNonKaraokeTag = extractLeadingNonKaraokeTags(rawText)
        val plainText = DialogueTokenizer.extractEditableText(rawText)

        val units = splitUnits(plainText, mode)
        if (units.isEmpty()) return rawText

        val totalCs = ((totalDurationUs.coerceAtLeast(0L) + (SubTime.US_PER_CS / 2L)) / SubTime.US_PER_CS)
        val count = units.size
        val baseCs = totalCs / count
        val remainder = (totalCs % count).toInt()

        return buildString(rawText.length + count * 8) {
            if (leadingNonKaraokeTag.isNotEmpty()) {
                append(leadingNonKaraokeTag)
            }
            for (i in units.indices) {
                val cs = baseCs + if (i < remainder) 1L else 0L
                append("{\\k")
                append(cs)
                append('}')
                append(units[i])
            }
        }
    }

    fun splitUnits(plainText: String, mode: KaraokeSplitMode): List<String> {
        val cleaned = plainText.trim()
        if (cleaned.isEmpty()) return emptyList()

        if ('|' in cleaned || '/' in cleaned) {
            return cleaned.split('|', '/').filter { it.isNotEmpty() }
        }

        return when (mode) {
            KaraokeSplitMode.WORD -> splitByWordsPreservingTrailingSpace(cleaned)
            KaraokeSplitMode.SYLLABLE -> splitIntoSyllables(cleaned)
        }
    }

    fun parseKaraokeSegments(rawAss: String): List<KaraokeSegment> {
        val matches = karaokeTagRegex.findAll(rawAss).toList()
        if (matches.isEmpty()) return emptyList()

        val segments = ArrayList<KaraokeSegment>(matches.size)
        var accumulatedUs = 0L

        for (i in matches.indices) {
            val match = matches[i]
            val cs = match.groupValues[1].toLongOrNull() ?: 0L
            val textStart = match.range.last + 1
            val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else rawAss.length
            val rawSlice = rawAss.substring(textStart, textEnd)
            val cleanSlice = DialogueTokenizer.extractEditableText(rawSlice)

            val segDurationUs = cs * SubTime.US_PER_CS
            val startOffsetUs = accumulatedUs
            val endOffsetUs = startOffsetUs + segDurationUs
            accumulatedUs = endOffsetUs

            segments.add(
                KaraokeSegment(
                    index = i,
                    text = cleanSlice,
                    durationCs = cs,
                    startOffsetUs = startOffsetUs,
                    endOffsetUs = endOffsetUs,
                )
            )
        }

        return segments
    }

    private fun splitByWordsPreservingTrailingSpace(text: String): List<String> {
        val result = ArrayList<String>()
        var tokenStart = 0
        var i = 0
        val n = text.length
        while (i < n) {
            while (i < n && !text[i].isWhitespace()) i++
            while (i < n && text[i].isWhitespace()) i++
            if (i > tokenStart) {
                result.add(text.substring(tokenStart, i))
                tokenStart = i
            }
        }
        return result
    }

    private fun splitIntoSyllables(text: String): List<String> {
        val wordsWithSpaces = splitByWordsPreservingTrailingSpace(text)
        val syllables = ArrayList<String>(wordsWithSpaces.size * 2)

        for (wordToken in wordsWithSpaces) {
            val trimmedWord = wordToken.trimEnd()
            val trailingSpace = wordToken.substring(trimmedWord.length)

            if (trimmedWord.length <= 3) {
                syllables.add(wordToken)
                continue
            }

            val wordSyllables = splitSingleWordSyllables(trimmedWord)
            for (idx in wordSyllables.indices) {
                if (idx == wordSyllables.lastIndex) {
                    syllables.add(wordSyllables[idx] + trailingSpace)
                } else {
                    syllables.add(wordSyllables[idx])
                }
            }
        }
        return syllables
    }

    private fun splitSingleWordSyllables(word: String): List<String> {
        val parts = ArrayList<String>(4)
        var start = 0
        var i = 0
        val len = word.length

        while (i < len) {
            if (word[i] in vowels) {
                var nextVowel = i + 1
                while (nextVowel < len && word[nextVowel] !in vowels) {
                    nextVowel++
                }
                if (nextVowel < len) {
                    val consonantCount = nextVowel - i - 1
                    val splitIndex = when {
                        consonantCount <= 1 -> i + 1
                        else -> {
                            val c1 = word[i + 1].lowercaseChar()
                            val c2 = word[i + 2].lowercaseChar()
                            val isDigraph = (c1 == 'n' && (c2 == 'g' || c2 == 'y')) ||
                                (c1 == 's' && c2 == 'y') ||
                                (c1 == 'k' && c2 == 'h')
                            if (isDigraph && consonantCount == 2) i + 1 else i + 2
                        }
                    }
                    parts.add(word.substring(start, splitIndex))
                    start = splitIndex
                    i = nextVowel
                    continue
                }
            }
            i++
        }

        if (start < len) {
            parts.add(word.substring(start))
        }
        return if (parts.isEmpty()) listOf(word) else parts
    }

    private fun extractLeadingNonKaraokeTags(rawAss: String): String {
        val tokens = DialogueTokenizer.tokenize(rawAss)
        val firstToken = tokens.firstOrNull() as? DialogueToken.TagToken ?: return ""
        val cleanedTagContent = firstToken.raw
            .removePrefix("{")
            .removeSuffix("}")
            .replace(Regex("""\\k[fo]?\s*\d+""", RegexOption.IGNORE_CASE), "")
            .trim()
        return if (cleanedTagContent.isEmpty()) "" else "{$cleanedTagContent}"
    }
}
