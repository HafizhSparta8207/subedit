package com.subedit.core.mpv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import java.io.File

/**
 * Listener callback sinkronisasi event pemutaran libmpv ke layer Jetpack Compose.
 */
interface MpvPlaybackListener {
    fun onTimePosChanged(timeUs: Long)
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun onDurationChanged(durationUs: Long)
    fun onMediaLoaded()
}

/**
 * Implementasi [BaseMPVView] kustom untuk editor SubEdit:
 * 1. Menangani decoding video AVC High 4:2:2 (YUV422P) dengan GPU context (`vo=gpu`, `gpu-context=android`)
 *    dan otomatis fallback software decoding libavcodec (`hwdec=auto-safe`).
 * 2. Rendering native libass dengan paritas 100% Aegisub desktop (`sub-ass=yes`, `sub-ass-override=no`, `sub-fonts-dir`).
 * 3. Event observer sinkron untuk `time-pos`, `pause`, dan kontrol scrubbing akurat (`relative+exact`, `absolute+exact`).
 */
class SubEditMpvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : BaseMPVView(context, attrs) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var playbackListener: MpvPlaybackListener? = null
    private var hasSubtitleTrack = false
    private var activeSubFilePath: String? = null
    private var isMpvInitialized = false

    var isPlaying: Boolean = false
        private set

    init {
        runCatching {
            // Salin cert dan subfont bawaan mpv ke internal files directory
            Utils.copyAssets(context)
            MpvMediaHelper.prepareFontsDirectory(context)

            val configDir = File(context.filesDir, "mpv").apply { mkdirs() }.path
            val cacheDir = context.cacheDir.path

            initialize(configDir, cacheDir)
            isMpvInitialized = true

            setupEventObserver()
        }.onFailure { err ->
            Log.e(TAG, "Gagal menginisialisasi native libmpv: ${err.message}", err)
        }
    }

    override fun initOptions() {
        val fontsDir = File(context.filesDir, "fonts")

        // 1. OPSI DEKODING AVC HIGH 4:2:2 (YUV422P) & GPU CONTEXT ANDROID
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("hwdec", "auto-safe")
        mpv.setOptionString("hwdec-codecs", "h264,hevc,vp9,av1")

        // 2. OPSI AUDIO & UMUM
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("keep-open", "always")
        mpv.setOptionString("sub-auto", "no")

        // 3. OPSI RENDERING NATIVE LIBASS (100% PARITAS AEGISUB DESKTOP)
        mpv.setOptionString("sub-ass", "yes")
        mpv.setOptionString("sub-ass-override", "no")
        mpv.setOptionString("sub-scale-with-window", "yes")
        if (fontsDir.exists()) {
            mpv.setOptionString("sub-fonts-dir", fontsDir.absolutePath)
        }
    }

    override fun postInitOptions() {
        // Default mulai dalam kondisi pause agar tidak langsung berputar tanpa interaksi pengguna
        mpv.setPropertyBoolean("pause", true)
    }

    override fun observeProperties() {
        mpv.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)
    }

    private fun setupEventObserver() {
        mpv.addObserver(object : MPV.EventObserver {
            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {}

            override fun eventProperty(property: String, value: Boolean) {
                when (property) {
                    "pause" -> {
                        isPlaying = !value
                        mainHandler.post {
                            playbackListener?.onPlaybackStateChanged(isPlaying)
                        }
                    }
                    "eof-reached" -> {
                        if (value) {
                            isPlaying = false
                            mainHandler.post {
                                playbackListener?.onPlaybackStateChanged(false)
                            }
                        }
                    }
                }
            }

            override fun eventProperty(property: String, value: String) {}

            override fun eventProperty(property: String, value: Double) {
                when (property) {
                    "time-pos" -> {
                        val timeUs = (value * 1_000_000.0).toLong().coerceAtLeast(0L)
                        mainHandler.post {
                            playbackListener?.onTimePosChanged(timeUs)
                        }
                    }
                    "duration" -> {
                        val durUs = (value * 1_000_000.0).toLong().coerceAtLeast(0L)
                        mainHandler.post {
                            playbackListener?.onDurationChanged(durUs)
                        }
                    }
                }
            }

            override fun eventProperty(property: String, value: MPVNode) {}

            override fun event(eventId: Int, node: MPVNode) {
                if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
                    mainHandler.post {
                        playbackListener?.onMediaLoaded()
                        // Jika file subtitle sudah siap sebelum video selesai dimuat, kaitkan langsung
                        activeSubFilePath?.let { subPath ->
                            loadOrReloadSubtitle(subPath)
                        }
                    }
                }
            }
        })
    }

    fun setPlaybackListener(listener: MpvPlaybackListener?) {
        this.playbackListener = listener
    }

    /**
     * Memuat file video atau audio ke dalam pemutar mpv.
     */
    fun loadMedia(pathOrUri: String) {
        if (pathOrUri.isBlank() || !isMpvInitialized) return
        hasSubtitleTrack = false
        playFile(pathOrUri)
    }

    /**
     * Memulai pemutaran audio/video via `mpv.setPropertyBoolean("pause", false)`.
     */
    fun play() {
        if (!isMpvInitialized) return
        mpv.setPropertyBoolean("pause", false)
        isPlaying = true
    }

    /**
     * Menghentikan sementara pemutaran via `mpv.setPropertyBoolean("pause", true)`.
     */
    fun pause() {
        if (!isMpvInitialized) return
        mpv.setPropertyBoolean("pause", true)
        isPlaying = false
    }

    /**
     * Toggle play/pause pemutaran.
     */
    fun togglePlay() {
        if (isPlaying) pause() else play()
    }

    /**
     * Melompat ke titik waktu detik tertentu secara presisi (`seek <sec> absolute+exact`).
     */
    fun seekToSeconds(seconds: Double) {
        if (!isMpvInitialized) return
        val clampedSec = seconds.coerceAtLeast(0.0)
        mpv.command("seek", clampedSec.toString(), "absolute+exact")
    }

    /**
     * Nudge waktu maju atau mundur dalam detik secara presisi (`seek <deltaSec> relative+exact`).
     */
    fun seekRelativeExact(deltaSeconds: Double) {
        if (!isMpvInitialized) return
        mpv.command("seek", deltaSeconds.toString(), "relative+exact")
    }

    /**
     * Memuat atau me-reload track subtitle ASS langsung ke core libass:
     * - Pertama kali memuat: `sub-add <path> select`
     * - Update real-time saat pengetikan/drag: `sub-reload`
     */
    fun loadOrReloadSubtitle(subFilePath: String) {
        if (!isMpvInitialized || subFilePath.isBlank()) return
        activeSubFilePath = subFilePath

        try {
            if (!hasSubtitleTrack) {
                mpv.command("sub-add", subFilePath, "select")
                hasSubtitleTrack = true
            } else {
                mpv.command("sub-reload")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memuat/reload subtitle ke libass: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SubEditMPVView"
    }
}
