package com.subedit.core.domain

import com.subedit.core.font.FontRepository
import com.subedit.core.session.ProjectSessionManager
import com.subedit.core.video.ChromaProxyConverter
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

        val updatedPos = DialogueTokenizer.injectOrUpdateHeadTags(
            rawText = withPos,
            tagUpdates = mapOf("pos" to "\\pos(500,400)", "frz" to "\\frz15"),
        )
        assertEquals("{\\pos(500,400)\\frz15}Bagaimana kau tahu\\Nkalau aku muak denganmu", updatedPos)

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

        val parsed = SubtitleParser.parseAss(assFileContent)
        assertEquals(1, parsed.events.size)
        assertEquals(rawAssSample, parsed.events[0].rawText)

        assertEquals(
            "Saya sangat gembira\nbertemu denganmu!",
            DialogueTokenizer.formatForVideoDisplay(parsed.events[0].rawText),
        )

        assertEquals(
            "Saya sangat gembira bertemu denganmu!",
            DialogueTokenizer.formatForWaveformLabel(parsed.events[0].rawText),
        )
    }

    @Test
    fun zeroStateProject_hasEmptyInitialHistoryAndCleanEditorState() {
        assertTrue(AssProjectItem.defaultProjects().isEmpty())

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
                startUs = 1_000_000L,
                endUs = 3_000_000L,
                isComment = false,
                rawText = "Line 1",
            ),
            AssDialogueEvent(
                id = 2,
                startUs = 3_000_000L,
                endUs = 5_000_000L,
                isComment = false,
                rawText = "Line 2",
            ),
        )

        assertEquals(null, ActiveSubtitleResolver.findActiveDialogue(events, 999L))

        val atStart = ActiveSubtitleResolver.findActiveDialogue(events, 1000L)
        assertEquals(1, atStart?.id)

        val mid1 = ActiveSubtitleResolver.findActiveDialogue(events, 2000L)
        assertEquals(1, mid1?.id)

        val atBoundary = ActiveSubtitleResolver.findActiveDialogue(events, 3000L)
        assertEquals(2, atBoundary?.id)

        assertEquals(2, ActiveSubtitleResolver.findActiveDialogue(events, 4999L)?.id)

        assertEquals(null, ActiveSubtitleResolver.findActiveDialogue(events, 5000L))

        assertEquals(null, ActiveSubtitleResolver.findActiveDialogue(events, 6000L))
    }

    @Test
    fun activeSubtitleResolver_ignoresCommentsAndResolvesOverlapping() {
        val events = listOf(
            AssDialogueEvent(
                id = 10,
                startUs = 1_000_000L,
                endUs = 4_000_000L,
                isComment = true,
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

        assertEquals(null, ActiveSubtitleResolver.findActiveDialogue(events, 1500L))

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

        val invertedEvent = AssDialogueEvent(
            id = 2,
            startUs = 5_000_000L,
            endUs = 2_000_000L,
            rawText = "Inverted",
        )
        assertEquals(0L, invertedEvent.durationUs)
        assertEquals("0.00s", invertedEvent.durationSecondsLabel)
    }

    @Test
    fun timingConstraints_enforcesNonNegativeStartMinTenMsDurationAndMaxMediaDuration() {
        assertEquals(0L, TimingConstraints.clampStartMs(proposedStartMs = -500L, currentEndMs = 2000L))
        assertEquals(0L, TimingConstraints.clampStartUs(proposedStartUs = -500_000L, currentEndUs = 2_000_000L))

        assertEquals(1990L, TimingConstraints.clampStartMs(proposedStartMs = 2500L, currentEndMs = 2000L))
        assertEquals(1_990_000L, TimingConstraints.clampStartUs(proposedStartUs = 2_500_000L, currentEndUs = 2_000_000L))

        assertEquals(1010L, TimingConstraints.clampEndMs(currentStartMs = 1000L, proposedEndMs = 900L, durationMs = 5000L))
        assertEquals(1_010_000L, TimingConstraints.clampEndUs(currentStartUs = 1_000_000L, proposedEndUs = 900_000L, durationUs = 5_000_000L))

        assertEquals(5000L, TimingConstraints.clampEndMs(currentStartMs = 1000L, proposedEndMs = 6500L, durationMs = 5000L))
        assertEquals(5_000_000L, TimingConstraints.clampEndUs(currentStartUs = 1_000_000L, proposedEndUs = 6_500_000L, durationUs = 5_000_000L))
    }

    @Test
    fun singleBlockTagUpsertParser_mergesLeadingBlocks_preservesInlineAndCommentBlocks_andTogglesFormat() {
        val rawWithMultipleBlocks = "{\\i1}{\\pos(400,500)}{とても嬉しくて...}{\\fscx120\\fscy191\\bord2.5\\blur1\\q2\\an8}Halo {\\b1}dunia{\\b0}!"
        val toggledBold = DialogueTokenizer.toggleFormatTag(rawWithMultipleBlocks, "b", styleDefaultEnabled = false)
        assertEquals(
            "{\\i1\\pos(400,500)\\fscx120\\fscy191\\bord2.5\\blur1\\q2\\an8\\b1}{とても嬉しくて...}Halo {\\b1}dunia{\\b0}!",
            toggledBold,
        )

        val toggledBoldOff = DialogueTokenizer.toggleFormatTag(toggledBold, "b", styleDefaultEnabled = false)
        assertEquals(
            "{\\i1\\pos(400,500)\\fscx120\\fscy191\\bord2.5\\blur1\\q2\\an8\\b0}{とても嬉しくて...}Halo {\\b1}dunia{\\b0}!",
            toggledBoldOff,
        )

        val withColor = DialogueTokenizer.applyColorOverrideTag(toggledBoldOff, "{\\c&HF8BD38&}")
        val withOutlineAlpha = DialogueTokenizer.applyColorOverrideTag(withColor, "{\\3c&H0000FF&\\3a&H80&}")
        assertEquals(
            "{\\i1\\pos(400,500)\\fscx120\\fscy191\\bord2.5\\blur1\\q2\\an8\\b0\\c&HF8BD38&\\3c&H0000FF&\\3a&H80&}{とても嬉しくて...}Halo {\\b1}dunia{\\b0}!",
            withOutlineAlpha,
        )

        val withVisualTransforms = DialogueTokenizer.injectOrUpdateHeadTags(
            rawText = withOutlineAlpha,
            tagUpdates = mapOf(
                "pos" to "\\pos(640,360)",
                "frz" to "\\frz45",
                "frx" to "\\frx15",
                "fry" to "\\fry-20",
                "fscx" to "\\fscx150",
                "fscy" to "\\fscy150",
            ),
        )
        assertEquals(
            "{\\i1\\pos(640,360)\\fscx150\\fscy150\\bord2.5\\blur1\\q2\\an8\\b0\\c&HF8BD38&\\3c&H0000FF&\\3a&H80&\\frz45\\frx15\\fry-20}{とても嬉しくて...}Halo {\\b1}dunia{\\b0}!",
            withVisualTransforms,
        )

        val parsedTransform = DialogueTokenizer.parseVisualTransform(
            rawText = withVisualTransforms,
            style = AssStyle.defaultSingleStyle().first(),
            playResX = 1920,
            playResY = 1080,
        )
        assertEquals(640f, parsedTransform.posX, 0.01f)
        assertEquals(360f, parsedTransform.posY, 0.01f)
        assertTrue(parsedTransform.hasCustomPos)
        assertEquals(8, parsedTransform.alignment)
        assertEquals(45f, parsedTransform.frz, 0.01f)
        assertEquals(15f, parsedTransform.frx, 0.01f)
        assertEquals(-20f, parsedTransform.fry, 0.01f)
        assertEquals(150f, parsedTransform.fscx, 0.01f)
        assertEquals(150f, parsedTransform.fscy, 0.01f)
    }

    @Test
    fun subtitleParser_preservesAllOriginalStyles23Columns_andRoundTripsAegisubDesktopMetadata() {
        val studioVsiAss = "\uFEFF[Script Info]\r\n" +
            "; Script generated by Aegisub 3.2.2\r\n" +
            "Title: VSI Production Script\r\n" +
            "Original Script: Crunchyroll\r\n" +
            "PlayResX: 1920\r\n" +
            "PlayResY: 1080\r\n" +
            "\r\n" +
            "[Aegisub Project Garbage]\r\n" +
            "Audio File: episode01.mkv\r\n" +
            "Video File: episode01.mkv\r\n" +
            "\r\n" +
            "[V4+ Styles]\r\n" +
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\r\n" +
            "Style: DefaultCR,Trebuchet MS,65,&H00FFFFFF,&H000000FF,&H00000000,&H96000000,0,0,0,0,100,100,0,0,1,3,1.5,2,90,90,50,1\r\n" +
            "Style: DefaultCR Italics,Trebuchet MS,65,&H00FFFFFF,&H000000FF,&H00000000,&H96000000,0,-1,0,0,100,100,0,0,1,3,1.5,2,90,90,50,1\r\n" +
            "Style: DefaultCR Tops,Trebuchet MS,65,&H00FFFFFF,&H000000FF,&H00000000,&H96000000,-1,0,0,0,95,105,1.2,15,3,3,1.5,8,90,90,50,1\r\n" +
            "\r\n" +
            "[Fonts]\r\n" +
            "fontname: custom_sign_0.ttf\r\n" +
            "\r\n" +
            "[Graphics]\r\n" +
            "filename: logo.png\r\n" +
            "\r\n" +
            "[Events]\r\n" +
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\r\n" +
            "Comment: 0,0:00:00.00,0:00:05.00,DefaultCR,Note,0,0,0,,{=0}Production note\r\n" +
            "Dialogue: 0,0:00:01.00,0:00:03.50,DefaultCR Tops,Sign,15,20,25,,{=0}{\\pos(960,200)\\q2}Judul Episode, Bagian 1\r\n" +
            "\r\n" +
            "[Aegisub Extradata]\r\n" +
            "Data: 0,key,value\r\n"

        val doc = SubtitleParser.parseAss(studioVsiAss)

        assertEquals(listOf("DefaultCR", "DefaultCR Italics", "DefaultCR Tops"), doc.styles.map { it.name })
        val topsStyle = doc.styles[2]
        assertEquals("96", topsStyle.shadowAlphaHex)
        assertEquals(95f, topsStyle.scaleX, 0.01f)
        assertEquals(105f, topsStyle.scaleY, 0.01f)
        assertEquals(1.2f, topsStyle.spacing, 0.01f)
        assertEquals(15f, topsStyle.angle, 0.01f)
        assertEquals(3, topsStyle.borderStyle)
        assertEquals(8, topsStyle.alignment)

        assertEquals(2, doc.events.size)
        assertTrue(doc.events[0].isComment)
        assertEquals("DefaultCR", doc.events[0].style)
        assertEquals("{=0}Production note", doc.events[0].rawText)

        assertFalse(doc.events[1].isComment)
        assertEquals("DefaultCR Tops", doc.events[1].style)
        assertEquals(15, doc.events[1].marginL)
        assertEquals(20, doc.events[1].marginR)
        assertEquals(25, doc.events[1].marginV)
        assertEquals("{=0}{\\pos(960,200)\\q2}Judul Episode, Bagian 1", doc.events[1].rawText)

        val exported = SubtitleParser.exportToAss(
            scriptInfo = doc.scriptInfo,
            styles = doc.styles,
            events = doc.events,
        )
        assertTrue(exported.contains("Original Script: Crunchyroll"))
        assertTrue(exported.contains("[Aegisub Project Garbage]\nAudio File: episode01.mkv"))
        assertTrue(exported.contains("[Fonts]\nfontname: custom_sign_0.ttf"))
        assertTrue(exported.contains("[Graphics]\nfilename: logo.png"))
        assertTrue(exported.contains("[Aegisub Extradata]\nData: 0,key,value"))
        assertTrue(
            exported.contains(
                "Style: DefaultCR Tops,Trebuchet MS,65,&H00FFFFFF,&H000000FF,&H00000000,&H96000000,-1,0,0,0,95,105,1.2,15,3,3,1.5,8,90,90,50,1"
            )
        )
        assertTrue(exported.contains("Comment: 0,0:00:00.00,0:00:05.00,DefaultCR,Note,0,0,0,,{=0}Production note"))

        ProjectSessionManager.applyImportedDocument(doc)
        val session = ProjectSessionManager.sessionState.value
        assertEquals(null, session.selectedEventId)
        assertFalse(session.canUndo)
        assertFalse(session.isMediaLoaded)
        assertEquals(60_000_000L, session.virtualDurationUs)
    }

    @Test
    fun chromaProxyConverter_detectsAvcHigh422SpsAndDownsamplesYuv422ToNv12WithoutLumaLoss() {
        val sps422AnnexB = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x7A.toByte(), 0x00, 0x1E)
        assertTrue(ChromaProxyConverter.isAvcHigh422Sps(sps422AnnexB))

        val sps422Avcc = byteArrayOf(0x01, 0x7A.toByte(), 0x00, 0x1E)
        assertTrue(ChromaProxyConverter.isAvcHigh422Sps(sps422Avcc))

        val sps420AnnexB = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x64.toByte(), 0x00, 0x1E)
        assertFalse(ChromaProxyConverter.isAvcHigh422Sps(sps420AnnexB))

        val width = 4
        val height = 2
        val yPlane = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80)
        val uPlane422 = byteArrayOf(100, 120, 110, 130.toByte())
        val vPlane422 = byteArrayOf(80, 90, 60, 70)
        val src422 = yPlane + uPlane422 + vPlane422
        val dstNv12 = ByteArray(width * height * 3 / 2)

        ChromaProxyConverter.downsampleYuv422PlanarToNv12(src422, dstNv12, width, height)
        for (i in yPlane.indices) {
            assertEquals(yPlane[i], dstNv12[i])
        }
        assertEquals(105.toByte(), dstNv12[8])
        assertEquals(70.toByte(), dstNv12[9])
        assertEquals(125.toByte(), dstNv12[10])
        assertEquals(80.toByte(), dstNv12[11])
    }
}
