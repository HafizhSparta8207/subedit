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

    @Volatile
    private var activeMediaPfd: ParcelFileDescriptor? = null

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
     * Membuka [Uri] Android secara langsung menggunakan path riil atau protokol `fd://`
     * tanpa menyalin ulang file video besar ke cache internal.
     */
    @Synchronized
    fun resolveUriToPath(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path ?: uri.toString()
        }

        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val realPath = runCatching { Utils.findRealPath(pfd.fd) }.getOrNull()
                if (!realPath.isNullOrEmpty()) {
                    val realFile = File(realPath)
                    if (realFile.exists() && realFile.canRead()) {
                        runCatching { pfd.close() }
                        return realPath
                    }
                }
                // Gunakan file descriptor langsung (fd://) tanpa menyalin file besar ke cache
                closeActiveFileDescriptor()
                activeMediaPfd = pfd
                return "fd://${pfd.fd}"
            }
        } catch (e: Exception) {
            Log.v(TAG, "Tidak dapat membuka file descriptor langsung: ${e.message}")
        }

        return uri.toString()
    }

    @Synchronized
    fun closeActiveFileDescriptor() {
        runCatching { activeMediaPfd?.close() }
        activeMediaPfd = null
    }

    /**
     * Menulis skrip subtitle ASS terkini ke file cache lokal (`cacheDir/subedit_live_preview.ass`)
     * dan melakukan sinkronisasi flush ke disk (`fos.flush()` + `fd.sync()`) sebelum perintah
     * `sub-add` / `sub-reload` dikirimkan ke native libmpv/libass.
     * Tetap mencetak `[Script Info]`, `[V4+ Styles]`, dan header `[Events]` meskipun daftar `events` kosong.
     */
    @Synchronized
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
        val bytes = content.toByteArray(Charsets.UTF_8)
        FileOutputStream(targetFile, false).use { fos ->
            fos.write(bytes)
            fos.flush()
            runCatching { fos.fd.sync() }
        }
        return targetFile
    }
}
