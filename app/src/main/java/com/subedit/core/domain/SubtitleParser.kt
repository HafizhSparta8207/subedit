package com.subedit.core.domain

/**
 * Parser dan Serializer penuh untuk file subtitle `.ass` (SubStation Alpha v4+) dan `.srt` (SubRip).
 */
object SubtitleParser {

    data class ParsedSubtitleDocument(
        val scriptInfo: AssScriptInfo,
        val styles: List<AssStyle>,
        val events: List<AssDialogueEvent>,
    )

    /**
     * Memeriksa apakah nama file memiliki ekstensi yang didukung (`.ass` atau `.srt`).
     */
    fun isSupportedSubtitleFileName(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) return false
        val lower = fileName.trim().lowercase()
        return lower.endsWith(".ass") || lower.endsWith(".srt")
    }

    /**
     * Mem-parsing isi file `.ass` atau `.srt` menjadi [ParsedSubtitleDocument].
     */
    fun parseSubtitleContent(fileName: String, content: String): ParsedSubtitleDocument {
        val isSrt = fileName.trim().lowercase().endsWith(".srt") ||
            (!content.contains("[Events]", ignoreCase = true) && content.contains("-->"))
        return if (isSrt) {
            parseSrt(content)
        } else {
            parseAss(content)
        }
    }

    fun parseAss(assContent: String): ParsedSubtitleDocument {
        var title = "SubEdit Project"
        var playResX = 1920
        var playResY = 1080
        var layoutResX = 0
        var layoutResY = 0
        var ycbcrMatrix = "None"
        var wrapStyle = 0
        val parsedStyles = ArrayList<AssStyle>()
        val parsedEvents = ArrayList<AssDialogueEvent>(256)
        var nextId = 1

        assContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(';')) return@forEach

            when {
                line.startsWith("Title:", ignoreCase = true) -> {
                    title = line.substringAfter(':').trim().ifEmpty { "SubEdit Project" }
                }
                line.startsWith("PlayResX:", ignoreCase = true) -> {
                    playResX = line.substringAfter(':').trim().toIntOrNull()?.coerceIn(320, 7680) ?: 1920
                }
                line.startsWith("PlayResY:", ignoreCase = true) -> {
                    playResY = line.substringAfter(':').trim().toIntOrNull()?.coerceIn(240, 4320) ?: 1080
                }
                line.startsWith("LayoutResX:", ignoreCase = true) -> {
                    layoutResX = line.substringAfter(':').trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                }
                line.startsWith("LayoutResY:", ignoreCase = true) -> {
                    layoutResY = line.substringAfter(':').trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                }
                line.startsWith("YCbCr Matrix:", ignoreCase = true) -> {
                    ycbcrMatrix = line.substringAfter(':').trim().ifEmpty { "None" }
                }
                line.startsWith("WrapStyle:", ignoreCase = true) -> {
                    wrapStyle = line.substringAfter(':').trim().toIntOrNull()?.coerceIn(0, 3) ?: 0
                }
                line.startsWith("Style:", ignoreCase = true) -> {
                    val body = line.substringAfter(':').trim()
                    parseAssStyleLine(body)?.let { parsedStyles.add(it) }
                }
                line.startsWith("Dialogue:", ignoreCase = true) ||
                    line.startsWith("Comment:", ignoreCase = true) -> {
                    val isComment = line.startsWith("Comment:", ignoreCase = true)
                    val body = line.substringAfter(':').trimStart()
                    val parts = body.split(',', limit = 10)
                    if (parts.size >= 10) {
                        val layer = parts[0].trim().toIntOrNull() ?: 0
                        val startUs = SubTime.parseAssOrNull(parts[1]) ?: 0L
                        val endUs = SubTime.parseAssOrNull(parts[2]) ?: (startUs + 2_000_000L)
                        val defaultStyleName = parsedStyles.firstOrNull()?.name ?: "Default"
                        val style = parts[3].trim().ifEmpty { defaultStyleName }
                        val actor = parts[4].trim()
                        val effect = parts[8].trim()
                        val text = parts[9]
                        parsedEvents.add(
                            AssDialogueEvent(
                                id = nextId++,
                                layer = layer,
                                startUs = startUs,
                                endUs = endUs.coerceAtLeast(startUs + 100_000L),
                                isComment = isComment,
                                style = style,
                                actor = actor,
                                effect = effect,
                                rawText = text,
                            )
                        )
                    }
                }
            }
        }

        val finalStyles = if (parsedStyles.isEmpty()) {
            AssStyle.defaultSingleStyle()
        } else {
            parsedStyles
        }

        return ParsedSubtitleDocument(
            scriptInfo = AssScriptInfo(
                title = title,
                playResX = playResX,
                playResY = playResY,
                layoutResX = layoutResX,
                layoutResY = layoutResY,
                ycbcrMatrix = ycbcrMatrix,
                wrapStyle = wrapStyle,
            ),
            styles = finalStyles,
            events = parsedEvents,
        )
    }

    /**
     * Mem-parsing file SubRip (`.srt`) dan mengonversi newline menjadi `\N` ASS.
     */
    fun parseSrt(srtContent: String): ParsedSubtitleDocument {
        val normalized = srtContent.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) {
            return ParsedSubtitleDocument(
                scriptInfo = AssScriptInfo(),
                styles = AssStyle.defaultSingleStyle(),
                events = emptyList(),
            )
        }

        val blocks = normalized.split(Regex("""\n\s*\n"""))
        val events = ArrayList<AssDialogueEvent>(blocks.size)
        val htmlTagRegex = Regex("""</?[^>]+>""")
        var nextId = 1

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            val timeLineIndex = lines.indexOfFirst { "-->" in it }
            if (timeLineIndex == -1 || timeLineIndex >= lines.lastIndex) continue

            val timeParts = lines[timeLineIndex].split("-->")
            if (timeParts.size < 2) continue

            val startUs = SubTime.parseAssOrNull(timeParts[0].trim()) ?: continue
            val endUs = SubTime.parseAssOrNull(timeParts[1].trim().substringBefore(' '))
                ?: (startUs + 2_000_000L)

            val textLines = lines.subList(timeLineIndex + 1, lines.size)
            val assText = textLines.joinToString("\\N") { line ->
                line
                    .replace("<i>", "{\\i1}", ignoreCase = true)
                    .replace("</i>", "{\\i0}", ignoreCase = true)
                    .replace("<b>", "{\\b1}", ignoreCase = true)
                    .replace("</b>", "{\\b0}", ignoreCase = true)
                    .replace("<u>", "{\\u1}", ignoreCase = true)
                    .replace("</u>", "{\\u0}", ignoreCase = true)
                    .replace(htmlTagRegex, "")
            }

            events.add(
                AssDialogueEvent(
                    id = nextId++,
                    startUs = startUs,
                    endUs = endUs.coerceAtLeast(startUs + 100_000L),
                    isComment = false,
                    style = "Default",
                    actor = "",
                    effect = "",
                    rawText = assText,
                )
            )
        }

        return ParsedSubtitleDocument(
            scriptInfo = AssScriptInfo(title = "SRT Imported Project"),
            styles = AssStyle.defaultSingleStyle(),
            events = events,
        )
    }

    private fun parseAssStyleLine(rawBody: String): AssStyle? {
        val parts = rawBody.split(',').map { it.trim() }
        if (parts.size < 22) return null

        return AssStyle(
            name = parts[0].ifEmpty { "Default" },
            fontname = parts[1].ifEmpty { "Trebuchet MS" },
            fontsize = (parts[2].toFloatOrNull() ?: 48f).coerceIn(8f, 200f),
            primaryColorHex = AssColorConverter.assToHex(parts[3], "#FFFFFF"),
            secondaryColorHex = AssColorConverter.assToHex(parts[4], "#0000FF"),
            outlineColorHex = AssColorConverter.assToHex(parts[5], "#000000"),
            shadowColorHex = AssColorConverter.assToHex(parts[6], "#000000"),
            bold = parts[7] == "-1" || parts[7] == "1",
            italic = parts[8] == "-1" || parts[8] == "1",
            underline = parts[9] == "-1" || parts[9] == "1",
            strikeout = parts[10] == "-1" || parts[10] == "1",
            outline = (parts[16].toFloatOrNull() ?: 2.5f).coerceAtLeast(0f),
            shadow = (parts[17].toFloatOrNull() ?: 1.5f).coerceAtLeast(0f),
            alignment = (parts[18].toIntOrNull() ?: 2).coerceIn(1, 9),
            marginL = (parts[19].toIntOrNull() ?: 60).coerceAtLeast(0),
            marginR = (parts[20].toIntOrNull() ?: 60).coerceAtLeast(0),
            marginV = (parts[21].toIntOrNull() ?: 45).coerceAtLeast(0),
            encoding = 1, // Dikunci ke 1 (Default UTF-8)
        )
    }

    fun serializeAssStyleLine(s: AssStyle): String {
        val c1 = AssColorConverter.hexToAss(s.primaryColorHex, "00")
        val c2 = AssColorConverter.hexToAss(s.secondaryColorHex, "00")
        val c3 = AssColorConverter.hexToAss(s.outlineColorHex, "00")
        val c4 = AssColorConverter.hexToAss(s.shadowColorHex, "00")
        val b = if (s.bold) -1 else 0
        val i = if (s.italic) -1 else 0
        val u = if (s.underline) -1 else 0
        val st = if (s.strikeout) -1 else 0
        val fontSizeIntOrFloat = if (s.fontsize % 1f == 0f) s.fontsize.toInt().toString() else s.fontsize.toString()
        return "Style: ${s.name},${s.fontname},$fontSizeIntOrFloat,$c1,$c2,$c3,$c4,$b,$i,$u,$st,100,100,0,0,1,${s.outline},${s.shadow},${s.alignment},${s.marginL},${s.marginR},${s.marginV},1"
    }

    /**
     * Mengekspor state skrip aktif menjadi isi file `.ass` standar Aegisub.
     */
    fun exportToAss(
        scriptInfo: AssScriptInfo,
        styles: List<AssStyle>,
        events: List<AssDialogueEvent>,
    ): String = buildString(4096) {
        appendLine("[Script Info]")
        appendLine("; Script generated by SubEdit Mobile ASS Subtitle Editor")
        appendLine("Title: ${scriptInfo.title}")
        appendLine("ScriptType: v4.00+")
        appendLine("WrapStyle: ${scriptInfo.wrapStyle}")
        appendLine("ScaledBorderAndShadow: ${if (scriptInfo.scaledBorderAndShadow) "yes" else "no"}")
        appendLine("YCbCr Matrix: ${scriptInfo.ycbcrMatrix.ifBlank { "None" }}")
        appendLine("PlayResX: ${scriptInfo.playResX}")
        appendLine("PlayResY: ${scriptInfo.playResY}")
        if (scriptInfo.layoutResX > 0 && scriptInfo.layoutResY > 0) {
            appendLine("LayoutResX: ${scriptInfo.layoutResX}")
            appendLine("LayoutResY: ${scriptInfo.layoutResY}")
        }
        appendLine()
        appendLine("[V4+ Styles]")
        appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        val activeStyles = styles.ifEmpty { AssStyle.defaultSingleStyle() }
        for (style in activeStyles) {
            appendLine(serializeAssStyleLine(style))
        }
        appendLine()
        appendLine("[Events]")
        appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        for (ev in events) {
            val prefix = if (ev.isComment) "Comment" else "Dialogue"
            append(prefix).append(": ")
            append(ev.layer).append(',')
            append(SubTime.formatAss(ev.startUs)).append(',')
            append(SubTime.formatAss(ev.endUs)).append(',')
            append(ev.style).append(',')
            append(ev.actor).append(",0,0,0,")
            append(ev.effect).append(',')
            appendLine(ev.rawText)
        }
    }
}
