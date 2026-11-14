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

object MpvMediaHelper {
    private const val TAG = "SubEditMPV"
    private const val LIVE_PREVIEW_SUB_FILENAME = "subedit_live_preview.ass"

    @Volatile
    private var activeMediaPfd: ParcelFileDescriptor? = null

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
