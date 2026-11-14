package com.subedit.core.font

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import java.util.concurrent.ConcurrentHashMap

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

    fun resolveAssetFileName(fontName: String): String =
        FONT_ASSET_MAP[fontName.trim().lowercase()] ?: "fonts/Trebuchet_MS.ttf"

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

    @Composable
    fun rememberAssFontFamily(fontName: String): FontFamily {
        val context = LocalContext.current
        return remember(context, fontName) {
            getFontFamily(context, fontName)
        }
    }
}
