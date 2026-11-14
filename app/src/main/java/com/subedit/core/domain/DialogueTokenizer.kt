package com.subedit.core.domain

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Representasi token pada satu baris dialog ASS:
 * - [TagToken]: Blok override tag ASS di dalam kurung kurawal `{...}`.
 * - [TextToken]: Teks dialog murni di luar kurung kurawal.
 */
sealed class DialogueToken {
    data class TagToken(val raw: String) : DialogueToken()
    data class TextToken(val text: String) : DialogueToken()
}

/**
 * Tokenizer, parser visual transform, dan Single-Block Tag Upsert Parser berstandar Aegisub.
 * Semua ekspresi reguler dikompilasi satu kali pada level objek (zero GC allocation in loop).
 */
object DialogueTokenizer {

    private val REGEX_ASS_TAGS = Regex("""\{[^}]*\}""")
    private val REGEX_UNCLOSED_TRAILING_TAG = Regex("""\{[^}]*$""")
    private val REGEX_BREAK_TOKENS = Regex("""\\[Nnh]""")
    private val REGEX_TAG_KEY_PREFIX = Regex("""^([1-4]?[a-zA-Z]+)""")

    private val REGEX_PARSE_AN = Regex("""\\an([1-9])(?![0-9a-zA-Z])""")
    private val REGEX_PARSE_POS = Regex("""\\pos\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)""")
    private val REGEX_PARSE_FRZ = Regex("""\\frz?(-?\d+(?:\.\d+)?)""")
    private val REGEX_PARSE_FRX = Regex("""\\frx(-?\d+(?:\.\d+)?)""")
    private val REGEX_PARSE_FRY = Regex("""\\fry(-?\d+(?:\.\d+)?)""")
    private val REGEX_PARSE_FSCX = Regex("""\\fscx(\d+(?:\.\d+)?)""")
    private val REGEX_PARSE_FSCY = Regex("""\\fscy(\d+(?:\.\d+)?)""")

    private data class LeadingParseResult(
        val tagMap: LinkedHashMap<String, String>,
        val preservedComments: List<String>,
        val body: String,
    )

    /**
     * Memecah string ASS mentah menjadi daftar berurutan [DialogueToken] secara O(N).
     */
    fun tokenize(rawAss: String): List<DialogueToken> {
        if (rawAss.isEmpty()) return emptyList()

        val tokens = ArrayList<DialogueToken>(8)
        var index = 0
        val length = rawAss.length

        while (index < length) {
            val openBrace = rawAss.indexOf('{', index)
            if (openBrace == -1) {
                tokens.add(DialogueToken.TextToken(rawAss.substring(index)))
                break
            }

            if (openBrace > index) {
                tokens.add(DialogueToken.TextToken(rawAss.substring(index, openBrace)))
            }

            val closeBrace = rawAss.indexOf('}', openBrace + 1)
            if (closeBrace == -1) {
                tokens.add(DialogueToken.TextToken(rawAss.substring(openBrace)))
                break
            }

            tokens.add(DialogueToken.TagToken(rawAss.substring(openBrace, closeBrace + 1)))
            index = closeBrace + 1
        }

        return tokens
    }

    /**
     * Menggabungkan kembali daftar [DialogueToken] menjadi string ASS utuh.
     */
    fun detokenize(tokens: List<DialogueToken>): String {
        if (tokens.isEmpty()) return ""
        val totalLen = tokens.sumOf { token ->
            when (token) {
                is DialogueToken.TagToken -> token.raw.length
                is DialogueToken.TextToken -> token.text.length
            }
        }
        return buildString(totalLen) {
            for (token in tokens) {
                when (token) {
                    is DialogueToken.TagToken -> append(token.raw)
                    is DialogueToken.TextToken -> append(token.text)
                }
            }
        }
    }

    /**
     * Mengekstrak hanya bagian [DialogueToken.TextToken].
     */
    fun extractEditableText(
        tokens: List<DialogueToken>,
        segmentDelimiter: String = "",
    ): String {
        val textTokens = tokens.filterIsInstance<DialogueToken.TextToken>()
        if (textTokens.isEmpty()) return ""
        if (textTokens.size == 1) return textTokens[0].text
        return textTokens.joinToString(separator = segmentDelimiter) { it.text }
    }

    fun extractEditableText(rawAss: String, segmentDelimiter: String = ""): String =
        extractEditableText(tokenize(rawAss), segmentDelimiter)

    /**
     * Mengganti teks terjemahan hanya pada [DialogueToken.TextToken] tanpa merusak [DialogueToken.TagToken].
     */
    fun updateEditableText(
        tokens: List<DialogueToken>,
        editedText: String,
    ): List<DialogueToken> {
        val sanitized = sanitizePlainText(editedText)
        val textCount = tokens.count { it is DialogueToken.TextToken }

        if (textCount == 0) {
            return tokens + DialogueToken.TextToken(sanitized)
        }

        if (textCount == 1) {
            return tokens.map { token ->
                when (token) {
                    is DialogueToken.TagToken -> token
                    is DialogueToken.TextToken -> DialogueToken.TextToken(sanitized)
                }
            }
        }

        val explicitParts = if ('|' in sanitized) sanitized.split('|') else null
        var textIdx = 0

        return tokens.map { token ->
            when (token) {
                is DialogueToken.TagToken -> token
                is DialogueToken.TextToken -> {
                    val replacement = when {
                        explicitParts != null && explicitParts.size == textCount ->
                            explicitParts[textIdx]
                        textIdx == 0 -> sanitized
                        else -> ""
                    }
                    textIdx++
                    DialogueToken.TextToken(replacement)
                }
            }
        }
    }

    fun replaceEditableText(rawAss: String, editedText: String): String =
        detokenize(updateEditableText(tokenize(rawAss), editedText))

    /**
     * Memformat teks ASS untuk dirender pada Monitor Video 16:9 sesuai standar Libass:
     * - Menghapus seluruh blok tag `{...}` dengan regex global `\{[^}]*\}`
     * - Mengubah `\N` menjadi baris baru (`\n`)
     * - Mengubah `\n` menjadi spasi dan `\h` menjadi non-breaking space (`\u00A0`)
     */
    fun formatForVideoDisplay(rawAss: String): String {
        if (rawAss.isEmpty()) return ""
        var cleaned = rawAss
        while (REGEX_ASS_TAGS.containsMatchIn(cleaned)) {
            cleaned = cleaned.replace(REGEX_ASS_TAGS, "")
        }
        return cleaned
            .replace(REGEX_UNCLOSED_TRAILING_TAG, "")
            .replace("\\N", "\n")
            .replace("\\n", " ")
            .replace("\\h", "\u00A0")
    }

    /**
     * Memformat label baris dialog di atas kanvas waveform audio sesuai standar Subtitle Edit:
     * - Menyembunyikan kurung kurawal `{...}` HANYA saat rendering label kanvas tanpa mengubah data asli
     * - Mengubah `\N`, `\n`, `\h` menjadi spasi tunggal dan melakukan `.trim()`
     */
    fun formatForWaveformLabel(rawAss: String): String {
        if (rawAss.isEmpty()) return ""
        var cleaned = rawAss
        while (REGEX_ASS_TAGS.containsMatchIn(cleaned)) {
            cleaned = cleaned.replace(REGEX_ASS_TAGS, "")
        }
        return cleaned
            .replace(REGEX_UNCLOSED_TRAILING_TAG, "")
            .replace(REGEX_BREAK_TOKENS, " ")
            .trim()
    }

    /**
     * Menghitung jumlah karakter bersih tanpa tag `{...}` dan tanpa token `\N`, `\n`, `\h`.
     */
    fun countCleanCharacters(rawAss: String): Int {
        if (rawAss.isEmpty()) return 0
        var cleaned = rawAss
        while (REGEX_ASS_TAGS.containsMatchIn(cleaned)) {
            cleaned = cleaned.replace(REGEX_ASS_TAGS, "")
        }
        return cleaned
            .replace(REGEX_UNCLOSED_TRAILING_TAG, "")
            .replace(REGEX_BREAK_TOKENS, "")
            .length
    }

    /**
     * Mem-parsing seluruh blok tag override `{...}` yang berurutan di awal baris dialog
     * (sebelum karakter teks pertama) ke dalam satu struktur [LinkedHashMap] (`key -> \tagValue`),
     * sambil tetap mempertahankan blok tag inline yang berada di tengah kalimat.
     */
    fun parseHeadTagMap(rawText: String): LinkedHashMap<String, String> {
        return parseLeadingBlocks(rawText).tagMap
    }

    /**
     * Menyuntikkan atau memperbarui override tag (`\b`, `\i`, `\u`, `\s`, `\c`, `\2c`, `\3c`, `\4c`,
     * `\pos`, `\move`, `\frz`, `\fscx`, `\fscy`, `\clip`, dll.) ke dalam satu struktur
     * `LinkedHashMap<String, String>` terpusat, menimpa nilai kunci yang sesuai, lalu merakitnya kembali
     * menjadi satu blok `{...}` tunggal di awal dialog tanpa menyentuh blok tag inline di tengah kalimat.
     */
    fun injectOrUpdateHeadTags(
        rawText: String,
        tagUpdates: Map<String, String>,
    ): String {
        val parsed = parseLeadingBlocks(rawText)
        val tagMap = parsed.tagMap

        for ((rawKey, rawValue) in tagUpdates) {
            val extractedTokens = extractIndividualTags(rawValue)
            if (extractedTokens.isNotEmpty()) {
                for (token in extractedTokens) {
                    val canonicalKey = resolveCanonicalTagKey(token) ?: normalizeExplicitKey(rawKey)
                    if (canonicalKey.isNotEmpty()) {
                        val normalizedToken = if (canonicalKey == "c" && token.startsWith("\\1c")) {
                            "\\c" + token.removePrefix("\\1c")
                        } else {
                            token
                        }
                        if (canonicalKey == "move") tagMap.remove("pos")
                        if (canonicalKey == "pos") tagMap.remove("move")
                        tagMap[canonicalKey] = normalizedToken
                    }
                }
            } else {
                val canonicalKey = normalizeExplicitKey(rawKey)
                val trimmedVal = rawValue.trim().removeSurrounding("{", "}")
                if (canonicalKey.isNotEmpty() && trimmedVal.isNotEmpty()) {
                    val formattedVal = if (trimmedVal.startsWith("\\")) trimmedVal else "\\$trimmedVal"
                    if (canonicalKey == "move") tagMap.remove("pos")
                    if (canonicalKey == "pos") tagMap.remove("move")
                    tagMap[canonicalKey] = formattedVal
                }
            }
        }

        return assembleSingleHeadBlock(
            tagMap = tagMap,
            preservedComments = parsed.preservedComments,
            body = parsed.body,
        )
    }

    /**
     * Toggle tag format `B` (`\b1`/`\b0`), `I` (`\i1`/`\i0`), `U` (`\u1`/`\u0`), atau `S` (`\s1`/`\s0`)
     * di dalam map blok `{...}` tunggal di awal dialog tanpa menyisipkan string di posisi kursor.
     */
    fun toggleFormatTag(
        rawText: String,
        tagKey: String,
        styleDefaultEnabled: Boolean = false,
    ): String {
        val cleanKey = normalizeExplicitKey(tagKey)
        if (cleanKey !in setOf("b", "i", "u", "s")) return rawText

        val currentMap = parseHeadTagMap(rawText)
        val existingToken = currentMap[cleanKey]
        val isCurrentlyEnabled = when (existingToken) {
            "\\${cleanKey}1" -> true
            "\\${cleanKey}0" -> false
            else -> styleDefaultEnabled
        }
        val nextValue = if (isCurrentlyEnabled) "\\${cleanKey}0" else "\\${cleanKey}1"
        return injectOrUpdateHeadTags(rawText, mapOf(cleanKey to nextValue))
    }

    /**
     * Menimpa tag warna (`\c`, `\2c`, `\3c`, `\4c` beserta alpha `\1a`–`\4a` bila ada)
     * ke dalam satu blok `{...}` tunggal di awal dialog tanpa menyisipkan di posisi kursor.
     */
    fun applyColorOverrideTag(
        rawText: String,
        formattedColorTagBlock: String,
    ): String {
        val parsed = parseLeadingBlocks(rawText)
        val tagMap = parsed.tagMap
        val tokens = extractIndividualTags(formattedColorTagBlock)

        var updatedColorSlot: String? = null
        var hasAlphaToken = false

        for (token in tokens) {
            val key = resolveCanonicalTagKey(token) ?: continue
            val normalizedToken = if (key == "c" && token.startsWith("\\1c")) {
                "\\c" + token.removePrefix("\\1c")
            } else {
                token
            }
            tagMap[key] = normalizedToken
            if (key in setOf("c", "2c", "3c", "4c")) {
                updatedColorSlot = key
            } else if (key in setOf("1a", "2a", "3a", "4a")) {
                hasAlphaToken = true
            }
        }

        // Jika warna baru bersifat solid (tanpa token alpha), bersihkan alpha slot lama yang sejenis
        if (updatedColorSlot != null && !hasAlphaToken) {
            val correspondingAlphaKey = when (updatedColorSlot) {
                "c" -> "1a"
                "2c" -> "2a"
                "3c" -> "3a"
                "4c" -> "4a"
                else -> null
            }
            if (correspondingAlphaKey != null) {
                tagMap.remove(correspondingAlphaKey)
            }
        }

        return assembleSingleHeadBlock(
            tagMap = tagMap,
            preservedComments = parsed.preservedComments,
            body = parsed.body,
        )
    }

    fun upsertPosTag(rawAss: String, x: Int, y: Int): String =
        injectOrUpdateHeadTags(rawAss, mapOf("pos" to "\\pos($x,$y)"))

    /**
     * Mem-parsing seluruh blok `{...}` yang berurutan di awal baris (sebelum karakter teks pertama),
     * termasuk memulihkan blok yang terpecah/bersarang, ke dalam [LeadingParseResult].
     */
    private fun parseLeadingBlocks(rawText: String): LeadingParseResult {
        val tagMap = LinkedHashMap<String, String>()
        val preservedComments = ArrayList<String>(1)
        var index = 0
        val len = rawText.length

        while (index < len && rawText[index] == '{') {
            var depth = 0
            var closeIdx = -1
            var cursor = index
            while (cursor < len) {
                val ch = rawText[cursor]
                if (ch == '{') {
                    depth++
                } else if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        closeIdx = cursor
                        break
                    }
                }
                cursor++
            }

            if (closeIdx == -1) break

            val rawGroup = rawText.substring(index + 1, closeIdx)
            if (rawGroup.contains('\\') || rawGroup.isBlank()) {
                // Jika terdapat blok kurawal bersarang akibat penyisipan kursor lama (misal {\fsc{\c&HFFFFFF&}y191}),
                // ekstrak blok dalam terlebih dahulu lalu pulihkan blok luarnya.
                val nestedMatches = REGEX_ASS_TAGS.findAll(rawGroup).toList()
                val repairedOuter = if (nestedMatches.isNotEmpty()) {
                    var temp = rawGroup
                    for (m in nestedMatches) {
                        temp = temp.replace(m.value, "")
                    }
                    temp
                } else {
                    rawGroup
                }

                for (token in extractIndividualTags(repairedOuter)) {
                    val key = resolveCanonicalTagKey(token) ?: continue
                    val normalizedToken = if (key == "c" && token.startsWith("\\1c")) {
                        "\\c" + token.removePrefix("\\1c")
                    } else {
                        token
                    }
                    tagMap[key] = normalizedToken
                }

                for (m in nestedMatches) {
                    for (token in extractIndividualTags(m.value)) {
                        val key = resolveCanonicalTagKey(token) ?: continue
                        val normalizedToken = if (key == "c" && token.startsWith("\\1c")) {
                            "\\c" + token.removePrefix("\\1c")
                        } else {
                            token
                        }
                        tagMap[key] = normalizedToken
                    }
                }
            } else {
                // Pertahankan blok komentar murni tanpa backslash (misal {とても嬉しくて...})
                preservedComments.add("{$rawGroup}")
            }

            index = closeIdx + 1
        }

        val body = rawText.substring(index)
        return LeadingParseResult(
            tagMap = tagMap,
            preservedComments = preservedComments,
            body = body,
        )
    }

    private fun assembleSingleHeadBlock(
        tagMap: LinkedHashMap<String, String>,
        preservedComments: List<String>,
        body: String,
    ): String {
        val headBlock = if (tagMap.isEmpty()) {
            ""
        } else {
            buildString {
                append('{')
                for (tag in tagMap.values) {
                    append(tag)
                }
                append('}')
            }
        }
        val commentsBlock = if (preservedComments.isEmpty()) "" else preservedComments.joinToString("")
        return headBlock + commentsBlock + body
    }

    /**
     * Memecah isi blok `{...}` menjadi daftar token `\tag...` individual.
     */
    private fun extractIndividualTags(rawBlockOrTag: String): List<String> {
        val cleaned = rawBlockOrTag.trim().removePrefix("{").removeSuffix("}")
        if (cleaned.isEmpty() || !cleaned.contains('\\')) return emptyList()

        val result = ArrayList<String>(6)
        var i = cleaned.indexOf('\\')
        val len = cleaned.length

        while (i in 0 until len) {
            if (cleaned.startsWith("\\t(", i)) {
                var parenDepth = 0
                var end = i + 2
                while (end < len) {
                    val c = cleaned[end]
                    if (c == '(') parenDepth++
                    else if (c == ')') {
                        parenDepth--
                        if (parenDepth <= 0) {
                            end++
                            break
                        }
                    }
                    end++
                }
                val token = cleaned.substring(i, end).trim().replace("{", "").replace("}", "")
                if (token.length > 1) result.add(token)
                i = cleaned.indexOf('\\', end)
            } else {
                val nextSlash = cleaned.indexOf('\\', i + 1)
                val end = if (nextSlash == -1) len else nextSlash
                val token = cleaned.substring(i, end).trim().replace("{", "").replace("}", "")
                if (token.length > 1) result.add(token)
                i = nextSlash
            }
        }
        return result
    }

    /**
     * Menentukan kunci kanonis untuk satu token tag ASS (misal `\pos(960,965)` -> `"pos"`).
     * Pemisahan nama tag & argumen yang ketat memastikan prefiks yang mirip tidak pernah bertabrakan:
     * - `\b` tidak menyentuh `\bord`, `\blur`, `\be`
     * - `\fs` tidak menyentuh `\fscx`, `\fscy`, `\fsp`
     * - `\frz`, `\frx`, `\fry` terpisah dan tidak menyentuh `\fn`
     * - `\p` tidak menyentuh `\pos`
     */
    private fun resolveCanonicalTagKey(tagToken: String): String? {
        val body = tagToken.trim().removePrefix("\\")
        if (body.isEmpty()) return null

        return when {
            body.startsWith("pos(") -> "pos"
            body.startsWith("move(") -> "move"
            body.startsWith("iclip(") || body.startsWith("clip(") -> "clip"
            body.startsWith("fad(") || body.startsWith("fade(") -> "fad"
            body.startsWith("org(") -> "org"
            body.startsWith("t(") -> "t"
            body.startsWith("1c") || (body.startsWith("c") && (body.length == 1 || body[1] == '&')) -> "c"
            body.startsWith("2c") -> "2c"
            body.startsWith("3c") -> "3c"
            body.startsWith("4c") -> "4c"
            body.startsWith("1a") -> "1a"
            body.startsWith("2a") -> "2a"
            body.startsWith("3a") -> "3a"
            body.startsWith("4a") -> "4a"
            body.startsWith("alpha") -> "alpha"
            body.startsWith("fscx") -> "fscx"
            body.startsWith("fscy") -> "fscy"
            body.startsWith("fsp") -> "fsp"
            body.startsWith("frz") -> "frz"
            body.startsWith("frx") -> "frx"
            body.startsWith("fry") -> "fry"
            body.startsWith("fr") && (body.length == 2 || body[2].isDigit() || body[2] == '-' || body[2] == '.') -> "frz"
            body.startsWith("fax") -> "fax"
            body.startsWith("fay") -> "fay"
            body.startsWith("fe") && (body.length == 2 || body[2].isDigit()) -> "fe"
            body.startsWith("fn") -> "fn"
            body.startsWith("fs") && (body.length == 2 || body[2].isDigit() || body[2] == '+' || body[2] == '-' || body[2] == '.') -> "fs"
            body.startsWith("an") && body.length >= 3 && body[2].isDigit() -> "an"
            body.startsWith("a") && (body.length == 1 || body[1].isDigit()) -> "a"
            body.startsWith("xbord") -> "xbord"
            body.startsWith("ybord") -> "ybord"
            body.startsWith("bord") -> "bord"
            body.startsWith("xshad") -> "xshad"
            body.startsWith("yshad") -> "yshad"
            body.startsWith("shad") -> "shad"
            body.startsWith("blur") -> "blur"
            body.startsWith("be") && (body.length == 2 || body[2].isDigit() || body[2] == '.') -> "be"
            body.startsWith("b") && (body.length == 1 || body[1].isDigit()) -> "b"
            body.startsWith("i") && (body.length == 1 || body[1].isDigit()) -> "i"
            body.startsWith("u") && (body.length == 1 || body[1].isDigit()) -> "u"
            body.startsWith("s") && (body.length == 1 || body[1].isDigit()) -> "s"
            body.startsWith("pbo") -> "pbo"
            body.startsWith("p") && (body.length == 1 || body[1].isDigit()) -> "p"
            body.startsWith("q") && (body.length == 1 || body[1].isDigit()) -> "q"
            else -> REGEX_TAG_KEY_PREFIX.find(body)?.groupValues?.get(1)?.lowercase()
        }
    }

    private fun normalizeExplicitKey(rawKey: String): String {
        val cleaned = rawKey.trim().removePrefix("\\").lowercase()
        return if (cleaned == "1c") "c" else cleaned
    }

    /**
     * Mem-parsing posisi anchor default dan transformasi visual dari tag ASS pada baris aktif.
     */
    fun parseVisualTransform(
        rawText: String,
        style: AssStyle,
        playResX: Int = 1920,
        playResY: Int = 1080,
    ): SubtitleVisualTransform {
        val headTags = parseHeadTagMap(rawText)
        val overrideAn = headTags["an"]?.removePrefix("\\an")?.trim()?.toIntOrNull()
            ?: REGEX_PARSE_AN.find(rawText)?.groupValues?.get(1)?.toIntOrNull()
        val an = (overrideAn ?: style.alignment).coerceIn(1, 9)
        val defaultX = when (an) {
            1, 4, 7 -> max(40, style.marginL).toFloat()
            3, 6, 9 -> (playResX - max(40, style.marginR)).toFloat()
            else -> playResX * 0.5f
        }
        val defaultY = when {
            an >= 7 -> max(55f, style.marginV * 1.6f)
            an >= 4 -> playResY * 0.5f
            else -> playResY - max(70f, style.marginV * 2.1f)
        }

        var posX = defaultX
        var posY = defaultY
        var hasCustomPos = false
        var frz = 0f
        var frx = 0f
        var fry = 0f
        var fscx = 100f
        var fscy = 100f

        val posSource = headTags["pos"] ?: rawText
        REGEX_PARSE_POS.find(posSource)?.let { m ->
            posX = m.groupValues[1].toFloatOrNull() ?: defaultX
            posY = m.groupValues[2].toFloatOrNull() ?: defaultY
            hasCustomPos = true
        }
        val frzSource = headTags["frz"] ?: rawText
        REGEX_PARSE_FRZ.find(frzSource)?.let { m ->
            frz = m.groupValues[1].toFloatOrNull() ?: 0f
        }
        val frxSource = headTags["frx"] ?: rawText
        REGEX_PARSE_FRX.find(frxSource)?.let { m ->
            frx = m.groupValues[1].toFloatOrNull() ?: 0f
        }
        val frySource = headTags["fry"] ?: rawText
        REGEX_PARSE_FRY.find(frySource)?.let { m ->
            fry = m.groupValues[1].toFloatOrNull() ?: 0f
        }
        val fscxSource = headTags["fscx"] ?: rawText
        REGEX_PARSE_FSCX.find(fscxSource)?.let { m ->
            fscx = m.groupValues[1].toFloatOrNull() ?: 100f
        }
        val fscySource = headTags["fscy"] ?: rawText
        REGEX_PARSE_FSCY.find(fscySource)?.let { m ->
            fscy = m.groupValues[1].toFloatOrNull() ?: 100f
        }

        return SubtitleVisualTransform(
            posX = posX,
            posY = posY,
            hasCustomPos = hasCustomPos,
            alignment = an,
            frz = frz,
            frx = frx,
            fry = fry,
            fscx = fscx,
            fscy = fscy,
        )
    }

    private fun sanitizePlainText(input: String): String {
        if ('{' !in input && '}' !in input) return input
        return buildString(input.length) {
            for (ch in input) {
                when (ch) {
                    '{' -> append('(')
                    '}' -> append(')')
                    else -> append(ch)
                }
            }
        }
    }
}
