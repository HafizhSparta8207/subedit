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
 * Tokenizer, parser visual transform, dan head-tag injector berstandar Aegisub.
 * Semua ekspresi reguler dikompilasi satu kali pada level objek (zero GC allocation in loop).
 */
object DialogueTokenizer {

    private val REGEX_ASS_TAGS = Regex("""\{.*?\}""")
    private val REGEX_ASS_TAG_GROUP = Regex("""\{([^}]*)\}""")
    private val REGEX_BREAK_TOKENS = Regex("""\\[Nnh]""")
    private val REGEX_LEAD_BLOCK = Regex("""^\{([^}]*)\}([\s\S]*)$""")

    private val REGEX_PARSE_POS = Regex("""\\pos\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)""")
    private val REGEX_PARSE_FRZ = Regex("""\\frz(-?\d+(?:\.\d+)?)""")
    private val REGEX_PARSE_FRX = Regex("""\\frx(-?\d+(?:\.\d+)?)""")
    private val REGEX_PARSE_FRY = Regex("""\\fry(-?\d+(?:\.\d+)?)""")
    private val REGEX_PARSE_FSCX = Regex("""\\fscx(\d+(?:\.\d+)?)""")
    private val REGEX_PARSE_FSCY = Regex("""\\fscy(\d+(?:\.\d+)?)""")
    private val REGEX_PARSE_CLIP = Regex(
        """\\clip\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)"""
    )

    private val STATIC_TAG_REGEX: Map<String, Regex> = mapOf(
        "pos" to Regex("""\\pos\([^)]*\)"""),
        "clip" to Regex("""\\clip\([^)]*\)"""),
        "frz" to Regex("""\\frz-?\d+(?:\.\d+)?"""),
        "frx" to Regex("""\\frx-?\d+(?:\.\d+)?"""),
        "fry" to Regex("""\\fry-?\d+(?:\.\d+)?"""),
        "fscx" to Regex("""\\fscx-?\d+(?:\.\d+)?"""),
        "fscy" to Regex("""\\fscy-?\d+(?:\.\d+)?"""),
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
     * - Menghapus blok tag `{...}`
     * - Mengubah `\N` menjadi baris baru (`\n`)
     * - Mengubah `\n` menjadi spasi dan `\h` menjadi non-breaking space (`\u00A0`)
     */
    fun formatForVideoDisplay(rawAss: String): String {
        if (rawAss.isEmpty()) return ""
        return rawAss
            .replace(REGEX_ASS_TAGS, "")
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
        return rawAss
            .replace(REGEX_ASS_TAGS, "")
            .replace(REGEX_BREAK_TOKENS, " ")
            .trim()
    }

    /**
     * Menghitung jumlah karakter bersih tanpa tag `{...}` dan tanpa token `\N`, `\n`, `\h`.
     */
    fun countCleanCharacters(rawAss: String): Int {
        if (rawAss.isEmpty()) return 0
        return rawAss
            .replace(REGEX_ASS_TAGS, "")
            .replace(REGEX_BREAK_TOKENS, "")
            .length
    }

    /**
     * Menyuntikkan atau memperbarui override tag visual (`\pos`, `\frz`, `\frx`, `\fry`,
     * `\fscx`, `\fscy`, `\clip`) tepat di dalam kurung kurawal pertama di PALING DEPAN baris.
     * Menjamin tidak ada tag duplikat dan tetap mempertahankan blok kurawal `{...}` lainnya.
     */
    fun injectOrUpdateHeadTags(
        rawText: String,
        tagUpdates: Map<String, String>,
    ): String {
        var headInner = ""
        var restBody = rawText

        val leadMatch = REGEX_LEAD_BLOCK.find(rawText)
        if (leadMatch != null) {
            headInner = leadMatch.groupValues[1]
            restBody = leadMatch.groupValues[2]
        }

        for ((tagName, formattedTag) in tagUpdates) {
            val regex = STATIC_TAG_REGEX[tagName] ?: continue
            // Bersihkan duplikat tag sejenis di dalam blok kurawal pada sisa kalimat tanpa menghapus kurawal komentar
            restBody = REGEX_ASS_TAG_GROUP.replace(restBody) { matchResult ->
                val inner = matchResult.groupValues[1].replace(regex, "")
                "{$inner}"
            }

            headInner = if (regex.containsMatchIn(headInner)) {
                headInner.replace(regex) { formattedTag }
            } else {
                if (tagName == "pos") {
                    formattedTag + headInner
                } else {
                    headInner + formattedTag
                }
            }
        }

        return "{$headInner}$restBody"
    }

    fun upsertPosTag(rawAss: String, x: Int, y: Int): String =
        injectOrUpdateHeadTags(rawAss, mapOf("pos" to "\\pos($x,$y)"))

    /**
     * Mem-parsing posisi anchor default dan transformasi visual dari tag ASS pada baris aktif.
     */
    fun parseVisualTransform(
        rawText: String,
        style: AssStyle,
        playResX: Int = 1920,
        playResY: Int = 1080,
    ): SubtitleVisualTransform {
        val an = style.alignment.coerceIn(1, 9)
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
        var clipRect: IntArray? = null

        REGEX_PARSE_POS.find(rawText)?.let { m ->
            posX = m.groupValues[1].toFloatOrNull() ?: defaultX
            posY = m.groupValues[2].toFloatOrNull() ?: defaultY
            hasCustomPos = true
        }
        REGEX_PARSE_FRZ.find(rawText)?.let { m ->
            frz = m.groupValues[1].toFloatOrNull() ?: 0f
        }
        REGEX_PARSE_FRX.find(rawText)?.let { m ->
            frx = m.groupValues[1].toFloatOrNull() ?: 0f
        }
        REGEX_PARSE_FRY.find(rawText)?.let { m ->
            fry = m.groupValues[1].toFloatOrNull() ?: 0f
        }
        REGEX_PARSE_FSCX.find(rawText)?.let { m ->
            fscx = m.groupValues[1].toFloatOrNull() ?: 100f
        }
        REGEX_PARSE_FSCY.find(rawText)?.let { m ->
            fscy = m.groupValues[1].toFloatOrNull() ?: 100f
        }
        REGEX_PARSE_CLIP.find(rawText)?.let { m ->
            val x1 = m.groupValues[1].toFloatOrNull()?.roundToInt() ?: 0
            val y1 = m.groupValues[2].toFloatOrNull()?.roundToInt() ?: 0
            val x2 = m.groupValues[3].toFloatOrNull()?.roundToInt() ?: playResX
            val y2 = m.groupValues[4].toFloatOrNull()?.roundToInt() ?: playResY
            clipRect = intArrayOf(x1, y1, x2, y2)
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
            clipRect = clipRect,
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
