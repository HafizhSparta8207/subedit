package com.subedit.core.font

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository pemuat dan cache font lokal dari `app/src/main/assets/fonts/` untuk menjamin
 * rendering teks di monitor video 16:9 dan Live Preview Style konsisten dengan Aegisub desktop.
 *
 * Daftar font standar Crunchyroll yang didukung di `assets/fonts/`:
 * - `Trebuchet MS` -> `fonts/Trebuchet_MS.ttf`
 * - `Arial` -> `fonts/Arial.ttf`
 * - `Arial Black` -> `fonts/Arial_Black.ttf`
 * - `Comic Sans MS` -> `fonts/Comic_Sans_MS.ttf`
 * - `Courier New` -> `fonts/Courier_New.ttf`
 * - `DejaVu Sans` -> `fonts/DejaVu_Sans.ttf`
 * - `Georgia` -> `fonts/Georgia.ttf`
 * - `Impact` -> `fonts/Impact.ttf`
 * - `Rubik` -> `fonts/Rubik.ttf`
 * - `Tahoma` -> `fonts/Tahoma.ttf`
 * - `Times New Roman` -> `fonts/Times_New_Roman.ttf`
 * - `Verdana` -> `fonts/Verdana.ttf`
 */
object FontRepository {

    val STANDARD_FONT_NAMES: List<String> = listOf(
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

    private val FONT_ASSET_MAP: Map<String, String> = mapOf(
        "trebuchet ms" to "fonts/Trebuchet_MS.ttf",
        "arial" to "fonts/Arial.ttf",
        "arial black" to "fonts/Arial_Black.ttf",
        "comic sans ms" to "fonts/Comic_Sans_MS.ttf",
        "courier new" to "fonts/Courier_New.ttf",
        "dejavu sans" to "fonts/DejaVu_Sans.ttf",
        "georgia" to "fonts/Georgia.ttf",
        "impact" to "fonts/Impact.ttf",
        "rubik" to "fonts/Rubik.ttf",
        "tahoma" to "fonts/Tahoma.ttf",
        "times new roman" to "fonts/Times_New_Roman.ttf",
        "verdana" to "fonts/Verdana.ttf",
    )

    private val fontFamilyCache = ConcurrentHashMap<String, FontFamily>()
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    /**
     * Mengembalikan path file font di dalam `assets/` untuk nama font ASS yang diberikan.
     */
    fun resolveAssetFileName(fontName: String): String =
        FONT_ASSET_MAP[fontName.trim().lowercase()] ?: "fonts/Trebuchet_MS.ttf"

    /**
     * Mengambil [FontFamily] Compose dari `assets/fonts/` dengan in-memory cache.
     * Aman dipanggil berulang kali tanpa membebani I/O maupun memori.
     */
    fun getFontFamily(context: Context, fontName: String): FontFamily {
        val key = fontName.trim().lowercase()
        fontFamilyCache[key]?.let { return it }

        val assetPath = FONT_ASSET_MAP[key]
        if (assetPath != null) {
            val loaded = runCatching {
                val tf = typefaceCache.getOrPut(key) {
                    Typeface.createFromAsset(context.assets, assetPath)
                }
                FontFamily(tf)
            }.getOrNull()

            if (loaded != null) {
                fontFamilyCache[key] = loaded
                return loaded
            }
        }

        val fallback = when (key) {
            "courier new" -> FontFamily.Monospace
            "georgia", "times new roman" -> FontFamily.Serif
            "comic sans ms" -> FontFamily.Cursive
            else -> FontFamily.SansSerif
        }
        fontFamilyCache[key] = fallback
        return fallback
    }

    /**
     * Helper Composable untuk meresolusi [FontFamily] dari nama font ASS.
     */
    @Composable
    fun rememberAssFontFamily(fontName: String): FontFamily {
        val context = LocalContext.current
        return remember(context, fontName) {
            getFontFamily(context, fontName)
        }
    }
}
