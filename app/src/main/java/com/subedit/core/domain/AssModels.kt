package com.subedit.core.domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Daftar 12 Font standar Crunchyroll & Aegisub yang dibundel di `assets/fonts/`.
 */
val CRUNCHYROLL_STANDARD_FONTS: List<String> = listOf(
    "Trebuchet MS",
    "Arial",
    "Arial Black",
    "Comic Sans MS",
    "Courier New",
    "DejaVu Sans",
    "Georgia",
    "Impact",
    "Rubik",
    "Tahoma",
    "Times New Roman",
    "Verdana",
)

/**
 * Preset Resolusi Skrip (`PlayResX x PlayResY`) dan Layout Resolution sesuai standar Aegisub.
 */
@Immutable
data class ScriptResolutionPreset(
    val width: Int,
    val height: Int,
    val description: String,
) {
    val label: String
        get() = if (width == 0 && height == 0) "0x0 (Default)" else "${width}x${height} ($description)"
}

val SCRIPT_RESOLUTION_PRESETS: List<ScriptResolutionPreset> = listOf(
    ScriptResolutionPreset(640, 480, "SD fullscreen"),
    ScriptResolutionPreset(704, 480, "SD anamorphic"),
    ScriptResolutionPreset(640, 360, "SD widescreen"),
    ScriptResolutionPreset(704, 396, "SD widescreen"),
    ScriptResolutionPreset(640, 352, "SD widescreen MOD16"),
    ScriptResolutionPreset(704, 400, "SD widescreen MOD16"),
    ScriptResolutionPreset(1024, 576, "SuperPAL widescreen"),
    ScriptResolutionPreset(1280, 720, "HD"),
    ScriptResolutionPreset(1920, 1080, "FHD"),
    ScriptResolutionPreset(2560, 1440, "QHD"),
    ScriptResolutionPreset(3840, 2160, "4K UHD"),
    ScriptResolutionPreset(1080, 1920, "FHD vertical"),
)

val LAYOUT_RESOLUTION_PRESETS: List<ScriptResolutionPreset> =
    listOf(ScriptResolutionPreset(0, 0, "Default")) + SCRIPT_RESOLUTION_PRESETS

/**
 * Daftar pilihan YCbCr Matrix pada inisialisasi proyek baru & Script Properties.
 */
@Immutable
data class YCbCrMatrixOption(
    val value: String,
    val displayLabel: String,
)

val YCBCR_MATRIX_OPTIONS: List<YCbCrMatrixOption> = listOf(
    YCbCrMatrixOption("None", "None"),
    YCbCrMatrixOption("TV.601", "TV.601"),
    YCbCrMatrixOption("PC.601", "PC.601"),
    YCbCrMatrixOption("TV.709", "TV.709 (Recommended)"),
    YCbCrMatrixOption("PC.709", "PC.709"),
    YCbCrMatrixOption("TV.FCC", "TV.FCC"),
    YCbCrMatrixOption("PC.FCC", "PC.FCC"),
    YCbCrMatrixOption("TV.240M", "TV.240M"),
    YCbCrMatrixOption("PC.240M", "PC.240M"),
)

/**
 * Batas aman jumlah karakter per baris (CPL Warning System).
 */
const val DEFAULT_MAX_SAFE_CPL: Int = 42

/**
 * Label deskriptif untuk Alignment Matrix 3x3 (`\an1` s/d `\an9`).
 */
val ASS_ALIGNMENT_LABELS: Map<Int, String> = mapOf(
    1 to "\\an1 (Bottom Left)",
    2 to "\\an2 (Bottom Center)",
    3 to "\\an3 (Bottom Right)",
    4 to "\\an4 (Middle Left)",
    5 to "\\an5 (Middle Center)",
    6 to "\\an6 (Middle Right)",
    7 to "\\an7 (Top Left)",
    8 to "\\an8 (Top Center)",
    9 to "\\an9 (Top Right)",
)

/**
 * Metadata `[Script Info]` pada dokumen ASS.
 */
@Immutable
data class AssScriptInfo(
    val title: String = "Project_Subtitle_1",
    val playResX: Int = 1920,
    val playResY: Int = 1080,
    val layoutResX: Int = 0,
    val layoutResY: Int = 0,
    val ycbcrMatrix: String = "None",
    val wrapStyle: Int = 0,
    val scaledBorderAndShadow: Boolean = true,
)

/**
 * Definisi lengkap `[V4+ Styles]` sesuai standar Aegisub & Crunchyroll.
 * Nilai [encoding] dikunci secara internal ke `1` (Default UTF-8 Unicode).
 */
@Immutable
data class AssStyle(
    val name: String = "Default",
    val fontname: String = "Trebuchet MS",
    val fontsize: Float = 48f,
    val primaryColorHex: String = "#FFFFFF",
    val secondaryColorHex: String = "#0000FF",
    val outlineColorHex: String = "#000000",
    val shadowColorHex: String = "#000000",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeout: Boolean = false,
    val outline: Float = 2.5f,
    val shadow: Float = 1.5f,
    val alignment: Int = 2, // 1..9 numpad layout
    val marginL: Int = 60,
    val marginR: Int = 60,
    val marginV: Int = 45,
    val encoding: Int = 1, // Terkunci internal ke 1 (UTF-8 Default)
) {
    val primaryComposeColor: Color
        get() = AssColorConverter.hexToComposeColor(primaryColorHex, Color.White)

    val secondaryComposeColor: Color
        get() = AssColorConverter.hexToComposeColor(secondaryColorHex, Color.Blue)

    val outlineComposeColor: Color
        get() = AssColorConverter.hexToComposeColor(outlineColorHex, Color.Black)

    val shadowComposeColor: Color
        get() = AssColorConverter.hexToComposeColor(shadowColorHex, Color.Black)

    val composeFontFamily: FontFamily
        get() = when (fontname.lowercase()) {
            "courier new" -> FontFamily.Monospace
            "georgia", "times new roman" -> FontFamily.Serif
            "comic sans ms" -> FontFamily.Cursive
            else -> FontFamily.SansSerif
        }

    companion object {
        /**
         * Style tunggal `Default` untuk inisialisasi proyek baru yang bersih (Zero-State).
         */
        fun defaultSingleStyle(): List<AssStyle> = listOf(
            AssStyle(
                name = "Default",
                fontname = "Trebuchet MS",
                fontsize = 48f,
                primaryColorHex = "#FFFFFF",
                secondaryColorHex = "#0000FF",
                outlineColorHex = "#000000",
                shadowColorHex = "#000000",
                bold = false,
                italic = false,
                outline = 2.5f,
                shadow = 1.5f,
                alignment = 2,
                marginL = 60,
                marginR = 60,
                marginV = 45,
            )
        )

        fun defaultCrunchyrollStyles(): List<AssStyle> = listOf(
            AssStyle(
                name = "DefaultCR Default",
                fontname = "Trebuchet MS",
                fontsize = 48f,
                primaryColorHex = "#FFFFFF",
                secondaryColorHex = "#0000FF",
                outlineColorHex = "#000000",
                shadowColorHex = "#000000",
                bold = false,
                italic = false,
                outline = 2.5f,
                shadow = 1.5f,
                alignment = 2,
                marginL = 60,
                marginR = 60,
                marginV = 45,
            ),
            AssStyle(
                name = "DefaultCR Italics",
                fontname = "Trebuchet MS",
                fontsize = 48f,
                primaryColorHex = "#FFFFFF",
                secondaryColorHex = "#0000FF",
                outlineColorHex = "#000000",
                shadowColorHex = "#000000",
                bold = false,
                italic = true,
                outline = 2.5f,
                shadow = 1.5f,
                alignment = 2,
                marginL = 60,
                marginR = 60,
                marginV = 45,
            ),
            AssStyle(
                name = "DefaultCR Signs",
                fontname = "Arial Black",
                fontsize = 44f,
                primaryColorHex = "#FFFBEB",
                secondaryColorHex = "#38BDF8",
                outlineColorHex = "#0F172A",
                shadowColorHex = "#000000",
                bold = true,
                italic = false,
                outline = 3.0f,
                shadow = 1.0f,
                alignment = 8,
                marginL = 60,
                marginR = 60,
                marginV = 40,
            ),
            AssStyle(
                name = "DefaultCR Flashback",
                fontname = "Trebuchet MS",
                fontsize = 48f,
                primaryColorHex = "#E2E8F0",
                secondaryColorHex = "#0000FF",
                outlineColorHex = "#1E293B",
                shadowColorHex = "#000000",
                bold = false,
                italic = false,
                outline = 2.5f,
                shadow = 2.0f,
                alignment = 2,
                marginL = 60,
                marginR = 60,
                marginV = 45,
            ),
        )
    }
}

/**
 * Konverter warna dua arah antara format Hex (`#RRGGBB`), format Style ASS (`&H00BBGGRR`),
 * dan format Inline Override Tag ASS (`&HBBGGRR&` & `&HAA&`).
 */
object AssColorConverter {

    fun assToHex(assColor: String, fallbackHex: String = "#FFFFFF"): String {
        val cleaned = assColor.trim()
            .removePrefix("&")
            .removePrefix("H")
            .removePrefix("h")
            .removeSuffix("&")
        if (cleaned.length != 6 && cleaned.length != 8) return fallbackHex
        if (!cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return fallbackHex

        val bgr = if (cleaned.length == 8) cleaned.substring(2) else cleaned
        val bb = bgr.substring(0, 2)
        val gg = bgr.substring(2, 4)
        val rr = bgr.substring(4, 6)
        return "#${rr.uppercase()}${gg.uppercase()}${bb.uppercase()}"
    }

    fun hexToAss(hexColor: String, alphaHex: String = "00"): String {
        val cleaned = hexColor.trim().removePrefix("#").padStart(6, '0').take(6).uppercase()
        val rr = cleaned.substring(0, 2)
        val gg = cleaned.substring(2, 4)
        val bb = cleaned.substring(4, 6)
        return "&H${alphaHex.uppercase()}$bb$gg$rr"
    }

    /**
     * Mengonversi `#RRGGBB` ke format tag inline ASS `&HBBGGRR&` (Blue-Green-Red).
     */
    fun hexToAssBgrTag(hexColor: String): String {
        val cleaned = hexColor.trim().removePrefix("#").padStart(6, '0').take(6).uppercase()
        val rr = cleaned.substring(0, 2)
        val gg = cleaned.substring(2, 4)
        val bb = cleaned.substring(4, 6)
        return "&H$bb$gg$rr&"
    }

    /**
     * Mengonversi nilai transparansi integer (`0..255`) ke format Hex ASS `&HAA&`.
     */
    fun alphaIntToAssHex(alpha: Int): String {
        val clamped = alpha.coerceIn(0, 255)
        return "&H${clamped.toString(16).uppercase().padStart(2, '0')}&"
    }

    /**
     * Mem-parsing string `&HAA&` menjadi integer `0..255`.
     */
    fun assAlphaHexToInt(alphaHex: String): Int? {
        val cleaned = alphaHex.trim()
            .removePrefix("&")
            .removePrefix("H")
            .removePrefix("h")
            .removeSuffix("&")
        if (cleaned.isEmpty() || cleaned.length > 2) return null
        return cleaned.toIntOrNull(16)?.coerceIn(0, 255)
    }

    /**
     * Membangun string tag warna inline ASS (`{\c&HBBGGRR&}`, `{\2c&HBBGGRR&}`, `{\3c&HBBGGRR&}`, `{\4c&HBBGGRR&}`)
     * beserta tag alpha jika `alpha > 0`.
     */
    fun formatColorOverrideTag(tagType: String, hexColor: String, alpha: Int = 0): String {
        val cleanType = when (tagType.trim().removePrefix("\\")) {
            "2c", "3c", "4c" -> tagType.trim().removePrefix("\\")
            else -> "c"
        }
        val bgr = hexToAssBgrTag(hexColor)
        return if (alpha > 0) {
            val alphaTag = when (cleanType) {
                "2c" -> "2a"
                "3c" -> "3a"
                "4c" -> "4a"
                else -> "1a"
            }
            "{\\$cleanType$bgr\\$alphaTag${alphaIntToAssHex(alpha)}}"
        } else {
            "{\\$cleanType$bgr}"
        }
    }

    fun hexToComposeColor(hexColor: String, fallback: Color = Color.White): Color {
        val cleaned = hexColor.trim().removePrefix("#")
        if (cleaned.length != 6) return fallback
        val rgb = cleaned.toLongOrNull(16) ?: return fallback
        return Color(0xFF000000L or rgb)
    }
}

/**
 * Model immutable untuk satu baris dialog ASS (`Dialogue:` atau `Comment:` di bagian `[Events]`).
 */
@Immutable
data class AssDialogueEvent(
    val id: Int,
    val layer: Int = 0,
    val startUs: Long,
    val endUs: Long,
    val isComment: Boolean = false,
    val style: String = "Default",
    val actor: String = "",
    val effect: String = "",
    val rawText: String,
) {
    val durationUs: Long
        get() = (endUs - startUs).coerceAtLeast(0L)

    val startAss: String
        get() = SubTime.formatAss(startUs)

    val endAss: String
        get() = SubTime.formatAss(endUs)

    val durationSecondsLabel: String
        get() = SubTime.formatDurationSeconds(durationUs)

    val cleanCharCount: Int
        get() = DialogueTokenizer.countCleanCharacters(rawText)
}

/**
 * Alias dan accessor standar libaegisub untuk [AssDialogueEvent].
 */
typealias AssEvent = AssDialogueEvent

val AssDialogueEvent.comment: Boolean
    get() = isComment

val AssDialogueEvent.start: SubTime
    get() = SubTime(startUs)

val AssDialogueEvent.end: SubTime
    get() = SubTime(endUs)

/**
 * Mode tool Typesetting Visual di bawah Audio Timeline Canvas (6 Tombol tanpa tombol KF).
 */
enum class VisualTypesetTool(val symbol: String, val label: String) {
    STANDARD("↖️", "Pointer Mode"),
    POS("✥", "\\pos(x,y)"),
    ROTATE_Z("🔄", "\\frz"),
    ROTATE_XY("📐", "\\frx/\\fry"),
    SCALE("⤢", "\\fscx/\\fscy"),
    CLIP("✂️", "\\clip"),
}

/**
 * State transformasi visual yang diekstrak dari tag baris aktif untuk dirender pada monitor video 16:9.
 */
@Immutable
data class SubtitleVisualTransform(
    val posX: Float = 960f,
    val posY: Float = 965f,
    val hasCustomPos: Boolean = false,
    val alignment: Int = 2,
    val frz: Float = 0f,
    val frx: Float = 0f,
    val fry: Float = 0f,
    val fscx: Float = 100f,
    val fscy: Float = 100f,
    val clipRect: IntArray? = null, // [x1, y1, x2, y2] dalam koordinat PlayRes
)

/**
 * Model ringkasan proyek pada layar Home (`HomeScreen.kt`).
 */
@Immutable
data class AssProjectItem(
    val id: String,
    val title: String,
    val durationBadge: String,
    val dateLabel: String,
    val lineCount: Int,
    val scriptInfo: AssScriptInfo = AssScriptInfo(title = title),
    val styles: List<AssStyle> = AssStyle.defaultSingleStyle(),
    val events: List<AssDialogueEvent> = emptyList(),
    val keyframesUs: List<Long> = emptyList(),
) {
    companion object {
        /**
         * State awal beranda saat instalasi pertama: Daftar proyek kosong (`emptyList()`).
         * Proyek hanya muncul di riwayat setelah pengguna membuat dan menyimpannya.
         */
        fun defaultProjects(): List<AssProjectItem> = emptyList()

        /**
         * Membuat proyek baru yang benar-benar bersih (Zero-State Editor):
         * - Tanpa dummy dialog (`events = emptyList()`)
         * - Style hanya berisi `Default` (`styles = AssStyle.defaultSingleStyle()`)
         * - Keyframes kosong (`keyframesUs = emptyList()`)
         */
        fun createZeroStateProject(
            projectIndex: Int = 1,
            title: String = "Project_Subtitle_$projectIndex",
            playResX: Int = 1920,
            playResY: Int = 1080,
            layoutResX: Int = 0,
            layoutResY: Int = 0,
            ycbcrMatrix: String = "None",
        ): AssProjectItem {
            val cleanTitle = title.trim().ifEmpty { "Project_Subtitle_$projectIndex" }
            return AssProjectItem(
                id = "proj_${System.currentTimeMillis()}",
                title = cleanTitle,
                durationBadge = "00:00",
                dateLabel = "Baru",
                lineCount = 0,
                scriptInfo = AssScriptInfo(
                    title = cleanTitle,
                    playResX = playResX,
                    playResY = playResY,
                    layoutResX = layoutResX,
                    layoutResY = layoutResY,
                    ycbcrMatrix = ycbcrMatrix,
                ),
                styles = AssStyle.defaultSingleStyle(),
                events = emptyList(),
                keyframesUs = emptyList(),
            )
        }

        fun defaultSampleEvents(): List<AssDialogueEvent> = listOf(
            AssDialogueEvent(
                id = 30,
                startUs = 14_500_000L,
                endUs = 17_200_000L,
                isComment = false,
                style = "DefaultCR Default",
                actor = "Frieren",
                effect = "",
                rawText = "Kenapa kau diam saja?",
            ),
            AssDialogueEvent(
                id = 31,
                startUs = 17_690_000L,
                endUs = 19_690_000L,
                isComment = false,
                style = "DefaultCR Default",
                actor = "Fern",
                effect = "",
                rawText = "Bagaimana kau tahu\\Nkalau aku muak denganmu",
            ),
            AssDialogueEvent(
                id = 32,
                startUs = 22_000_000L,
                endUs = 24_800_000L,
                isComment = false,
                style = "DefaultCR Default",
                actor = "Stark",
                effect = "",
                rawText = "Jangan berpura-pura\\Ntidak mendengarku.",
            ),
        )
    }
}
