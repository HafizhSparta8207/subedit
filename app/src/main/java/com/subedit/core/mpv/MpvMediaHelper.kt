package com.subedit.core.mpv

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.subedit.core.domain.AssDialogueEvent
import com.subedit.core.domain.AssScriptInfo
import com.subedit.core.domain.AssStyle
import com.subedit.core.domain.SubtitleParser
import `is`.xyz.mpv.Utils
import java.io.File
import java.io.FileOutputStream

/**
 * Utilitas manajemen media, ekstraksi font lokal, dan sinkronisasi live preview ASS untuk engine libmpv.
 */
object MpvMediaHelper {

    private const val TAG = "SubEditMPV"
    private const val LIVE_PREVIEW_SUB_FILENAME = "subedit_live_preview.ass"

    /**
     * Mempersiapkan direktori font lokal di internal storage (`filesDir/fonts`)
     * dan menyalin font standar Crunchyroll dari assets agar dapat diakses langsung oleh native libass.
     */
    fun prepareFontsDirectory(context: Context): File {
        val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
        val assetManager = context.assets

        val assetFontFiles = runCatching { assetManager.list("fonts") }.getOrNull() ?: emptyArray()
        for (fontFileName in assetFontFiles) {
            val targetFile = File(fontsDir, fontFileName)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                runCatching {
                    assetManager.open("fonts/$fontFileName").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Gagal menyalin font $fontFileName ke internal storage: ${err.message}")
                }
            }
        }
        return fontsDir
    }

    /**
     * Menyelesaikan [Uri] Android menjadi path file riil sistem jika memungkinkan,
     * atau mengembalikan string URI untuk dibuka secara streaming oleh core FFmpeg bawaan libmpv.
     */
    fun resolveUriToPath(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path ?: uri.toString()
        }

        // 1. Coba dapatkan real path dari ParcelFileDescriptor (misal file lokal di shared storage)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val realPath = Utils.findRealPath(pfd.fd)
                if (!realPath.isNullOrEmpty() && File(realPath).exists()) {
                    return realPath
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "Tidak dapat mengekstrak real path dari file descriptor: ${e.message}")
        }

        // 2. Fallback: Core FFmpeg di libmpv mendukung content:// URI secara langsung
        return uri.toString()
    }

    /**
     * Menulis skrip subtitle ASS terkini ke file cache lokal (`cacheDir/subedit_live_preview.ass`)
     * agar dapat dibaca dan dirender secara instan oleh libass melalui perintah `sub-add` / `sub-reload`.
     */
    fun writeLiveAssPreview(
        context: Context,
        scriptInfo: AssScriptInfo,
        styles: List<AssStyle>,
        events: List<AssDialogueEvent>,
    ): File {
        val targetFile = File(context.cacheDir, LIVE_PREVIEW_SUB_FILENAME)
        val content = SubtitleParser.exportToAss(
            scriptInfo = scriptInfo,
            styles = styles,
            events = events,
        )
        targetFile.writeText(content, Charsets.UTF_8)
        return targetFile
    }
}
