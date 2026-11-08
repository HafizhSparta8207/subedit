package com.subedit.core.domain

import com.subedit.core.font.FontRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubEditDomainTest {

    @Test
    fun subTime_formatAndParseAssAndSrt_roundTripWithoutRoundingError() {
        val sampleAss = "1:23:45.67"
        val timeUs = SubTime.parseAss(sampleAss)
        assertEquals(5_025_670_000L, timeUs)
        assertEquals(sampleAss, SubTime.formatAss(timeUs))

        // SRT millisecond comma format
        val srtTimeUs = SubTime.parseAss("00:01:17,690")
        assertEquals(77_690_000L, srtTimeUs)
        assertEquals("0:01:17.69", SubTime.formatAss(srtTimeUs))
    }

    @Test
    fun dialogueTokenizer_injectsHeadTagsAndFormatsMultiLine() {
        val original = "Bagaimana kau tahu\\Nkalau aku muak denganmu"
        val withPos = DialogueTokenizer.injectOrUpdateHeadTags(
            rawText = original,
            tagUpdates = mapOf("pos" to "\\pos(960,965)"),
        )
        assertEquals("{\\pos(960,965)}Bagaimana kau tahu\\Nkalau aku muak denganmu", withPos)

        // Mengganti \pos lama tanpa menduplikasi tag
        val updatedPos = DialogueTokenizer.injectOrUpdateHeadTags(
            rawText = withPos,
            tagUpdates = mapOf("pos" to "\\pos(500,400)", "frz" to "\\frz15"),
        )
        assertEquals("{\\pos(500,400)\\frz15}Bagaimana kau tahu\\Nkalau aku muak denganmu", updatedPos)

        // Render teks multi-line untuk monitor video (\N -> \n)
        assertEquals(
            "Bagaimana kau tahu\nkalau aku muak denganmu",
            DialogueTokenizer.formatForVideoDisplay(updatedPos),
        )
    }

    @Test
    fun subtitleParser_validatesExtensionsAndParsesAssAndSrt() {
        assertTrue(SubtitleParser.isSupportedSubtitleFileName("episode_01.ass"))
        assertTrue(SubtitleParser.isSupportedSubtitleFileName("movie.SRT"))
        assertFalse(SubtitleParser.isSupportedSubtitleFileName("document.txt"))
        assertFalse(SubtitleParser.isSupportedSubtitleFileName("subtitles.vtt"))

        val srtSample = """
            1
            00:00:14,500 --> 00:00:17,200
            <i>Kenapa kau diam saja?</i>
            Baris kedua
        """.trimIndent()
        val parsedSrt = SubtitleParser.parseSrt(srtSample)
        assertEquals(1, parsedSrt.events.size)
        assertEquals("{\\i1}Kenapa kau diam saja?{\\i0}\\NBaris kedua", parsedSrt.events[0].rawText)
    }

    @Test
    fun keyframeEngine_parsesAegisubKeyframesAndSnapsWithinThreshold() {
        val content = """
            # keyframe format v1
            fps 25.0
            0
            25
            50
        """.trimIndent()
        val keyframes = KeyframeEngine.parseKeyframesTxt(content)
        assertEquals(listOf(0L, 1_000_000L, 2_000_000L), keyframes)
        assertEquals(1_000_000L, KeyframeEngine.snapToKeyframe(1_060_000L, keyframes, 100_000L))
    }

    @Test
    fun keyframeEngine_parsesCrunchyrollV1FormatWithFpsZeroFallback() {
        val v1Content = """
            # keyframe format v1
            fps 0

            0 I -1
            4 I -1
            171 I -1
        """.trimIndent()
        val keyframesSec = KeyframeEngine.parseKeyframes(v1Content, fallbackFps = 23.976)
        assertEquals(3, keyframesSec.size)
        assertEquals(0.0, keyframesSec[0], 0.0001)
        assertEquals(4.0 / 23.976, keyframesSec[1], 0.0001)
        assertEquals(171.0 / 23.976, keyframesSec[2], 0.0001)

        val keyframesUs = KeyframeEngine.parseKeyframesTxt(v1Content, fallbackFps = 23.976)
        assertEquals(listOf(0L, 166_834L, 7_132_132L), keyframesUs)
    }

    @Test
    fun dialogueTokenizer_preservesCurlyBracesInRawDataAndStripsForDisplay() {
        val rawAssSample = """{\i1}{とても嬉しくて...}Saya sangat gembira\Nbertemu denganmu!"""
        val assFileContent = """
            [Script Info]
            Title: Test Preservation
            PlayResX: 1920
            PlayResY: 1080

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:04.50,Default,,0,0,0,,$rawAssSample
        """.trimIndent()

        // 1. State dialog WAJIB mempertahankan seluruh kurung kurawal {...} utuh
        val parsed = SubtitleParser.parseAss(assFileContent)
        assertEquals(1, parsed.events.size)
        assertEquals(rawAssSample, parsed.events[0].rawText)

        // 2. Monitor Video: sembunyikan {...} dan ubah \N menjadi newline (\n)
        assertEquals(
            "Saya sangat gembira\nbertemu denganmu!",
            DialogueTokenizer.formatForVideoDisplay(parsed.events[0].rawText),
        )

        // 3. Label Kanvas Waveform: sembunyikan {...} dan ubah \N menjadi spasi
        assertEquals(
            "Saya sangat gembira bertemu denganmu!",
            DialogueTokenizer.formatForWaveformLabel(parsed.events[0].rawText),
        )
    }

    @Test
    fun zeroStateProject_hasEmptyInitialHistoryAndCleanEditorState() {
        // First install / clean initial state: defaultProjects() harus kosong []
        assertTrue(AssProjectItem.defaultProjects().isEmpty())

        // Zero-State project dari modal "New Project Properties"
        val zeroProj = AssProjectItem.createZeroStateProject(
            projectIndex = 1,
            title = "Project_Subtitle_1",
            playResX = 1920,
            playResY = 1080,
            layoutResX = 0,
            layoutResY = 0,
            ycbcrMatrix = "None",
        )
        assertEquals("Project_Subtitle_1", zeroProj.title)
        assertTrue(zeroProj.events.isEmpty())
        assertEquals(1, zeroProj.styles.size)
        assertEquals("Default", zeroProj.styles.first().name)
        assertEquals(1920, zeroProj.scriptInfo.playResX)
        assertEquals(1080, zeroProj.scriptInfo.playResY)
        assertEquals(0, zeroProj.scriptInfo.layoutResX)
        assertEquals(0, zeroProj.scriptInfo.layoutResY)
        assertEquals("None", zeroProj.scriptInfo.ycbcrMatrix)
    }

    @Test
    fun assColorConverter_formatsAssBgrAndOverrideTagsWithAlpha() {
        assertEquals("&HF8BD38&", AssColorConverter.hexToAssBgrTag("#38BDF8"))
        assertEquals("{\\c&HF8BD38&}", AssColorConverter.formatColorOverrideTag("\\c", "#38BDF8", alpha = 0))
        assertEquals(
            "{\\3c&H0000FF&\\3a&H80&}",
            AssColorConverter.formatColorOverrideTag("\\3c", "#FF0000", alpha = 128),
        )
    }

    @Test
    fun subtitleParser_exportsAndParsesLayoutResAndYcbcrMatrix() {
        val info = AssScriptInfo(
            title = "Project_Subtitle_2",
            playResX = 1920,
            playResY = 1080,
            layoutResX = 1280,
            layoutResY = 720,
            ycbcrMatrix = "TV.709",
            wrapStyle = 0,
        )
        val exported = SubtitleParser.exportToAss(
            scriptInfo = info,
            styles = AssStyle.defaultSingleStyle(),
            events = emptyList(),
        )
        assertTrue(exported.contains("LayoutResX: 1280"))
        assertTrue(exported.contains("LayoutResY: 720"))
        assertTrue(exported.contains("YCbCr Matrix: TV.709"))

        val reParsed = SubtitleParser.parseAss(exported)
        assertEquals(1280, reParsed.scriptInfo.layoutResX)
        assertEquals(720, reParsed.scriptInfo.layoutResY)
        assertEquals("TV.709", reParsed.scriptInfo.ycbcrMatrix)
        assertEquals(1, reParsed.styles.size)
        assertEquals("Default", reParsed.styles.first().name)
    }

    @Test
    fun fontRepository_mapsAllTwelveCrunchyrollStandardFonts() {
        assertEquals(12, CRUNCHYROLL_STANDARD_FONTS.size)
        CRUNCHYROLL_STANDARD_FONTS.forEach { fontName ->
            val fileName = FontRepository.resolveAssetFileName(fontName)
            assertTrue("Font $fontName harus terpetakan ke file .ttf", fileName.endsWith(".ttf"))
        }
    }

    @Test
    fun activeSubtitleResolver_matchesDialogueWithinBounds_andExcludesAtEnd() {
        val events = listOf(
            AssDialogueEvent(
                id = 1,
                startUs = 1_000_000L, // 1000 ms
                endUs = 3_000_000L,   // 3000 ms
                isComment = false,
                rawText = "Line 1",
            ),
            AssDialogueEvent(
                id = 2,
                startUs = 3_000_000L, // 3000 ms
                endUs = 5_000_000L,   // 5000 ms
                isComment = false,
                rawText = "Line 2",
            ),
        )

        // Sebelum Line 1
        assertEquals(null, ActiveSubtitleResolver.findActiveDialogue(events, 999L))

        // Tepat di start Line 1 (inklusif: start <= t)
        val atStart = ActiveSubtitleResolver.findActiveDialogue(events, 1000L)
        assertEquals(1, atStart?.id)

        // Di tengah Line 1
        val mid1 = ActiveSubtitleResolver.findActiveDialogue(events, 2000L)
        assertEquals(1, mid1?.id)

        // Tepat di batas akhir Line 1 / awal Line 2:
        // Sesuai konvensi Aegisub/ASS: t < end untuk Line 1, start <= t untuk Line 2
        val atBoundary = ActiveSubtitleResolver.findActiveDialogue(events, 3000L)
        assertEquals(2, atBoundary?.id)

        // Di akhir Line 2
        assertEquals(2, ActiveSubtitleResolver.findActiveDialogue(events, 4999L)?.id)

        // Tepat di end Line 2 (eksklusif: t < end)
        assertEquals(null, ActiveSubtitleResolver.findActiveDialogue(events, 5000L))

        // Setelah seluruh event
        assertEquals(null, ActiveSubtitleResolver.findActiveDialogue(events, 6000L))
    }

    @Test
    fun activeSubtitleResolver_ignoresCommentsAndResolvesOverlapping() {
        val events = listOf(
            AssDialogueEvent(
                id = 10,
                startUs = 1_000_000L,
                endUs = 4_000_000L,
                isComment = true, // Komentar / catatan subtitler
                rawText = "Notes / comment",
            ),
            AssDialogueEvent(
                id = 11,
                startUs = 2_000_000L,
                endUs = 4_000_000L,
                isComment = false,
                rawText = "Dialog aktif",
            ),
        )

        // Pada posisi 1500ms, hanya event comment yang aktif -> harus diabaikan (return null)
        assertEquals(null, ActiveSubtitleResolver.findActiveDialogue(events, 1500L))

        // Pada posisi 2500ms, event comment dan dialog overlap -> harus memilih dialog non-comment (id 11)
        val active = ActiveSubtitleResolver.findActiveDialogue(events, 2500L)
        assertEquals(11, active?.id)
        assertEquals("Dialog aktif", active?.rawText)
    }

    @Test
    fun activeSubtitleResolver_microsecondPrecisionMatchesExactly() {
        val event = AssDialogueEvent(
            id = 20,
            startUs = 1_234_567L,
            endUs = 3_456_789L,
            isComment = false,
            rawText = "High precision",
        )
        val list = listOf(event)

        assertFalse(ActiveSubtitleResolver.isEventActiveUs(event, 1_234_566L))
        assertTrue(ActiveSubtitleResolver.isEventActiveUs(event, 1_234_567L))
        assertTrue(ActiveSubtitleResolver.isEventActiveUs(event, 2_000_000L))
        assertFalse(ActiveSubtitleResolver.isEventActiveUs(event, 3_456_789L))
        assertEquals(20, ActiveSubtitleResolver.findActiveDialogueUs(list, 1_234_567L)?.id)
    }

    @Test
    fun assDialogueEvent_durationAndBoundariesCalculation() {
        val normalEvent = AssDialogueEvent(
            id = 1,
            startUs = 1_000_000L,
            endUs = 3_500_000L,
            rawText = "Test",
        )
        assertEquals(2_500_000L, normalEvent.durationUs)
        assertEquals("0:00:01.00", normalEvent.startAss)
        assertEquals("0:00:03.50", normalEvent.endAss)
        assertEquals("2.50s", normalEvent.durationSecondsLabel)

        // Batas durasi jika endUs < startUs (proteksi coerceAtLeast 0)
        val invertedEvent = AssDialogueEvent(
            id = 2,
            startUs = 5_000_000L,
            endUs = 2_000_000L,
            rawText = "Inverted",
        )
        assertEquals(0L, invertedEvent.durationUs)
        assertEquals("0.00s", invertedEvent.durationSecondsLabel)
    }
}
