package com.subedit.core.mpv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import com.subedit.core.session.ProjectSessionManager
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
    fun onMediaLoadingStarted() {}
    fun onMediaLoaded()
}

/**
 * Implementasi [BaseMPVView] kustom untuk editor SubEdit:
 * 1. Menangani decoding video AVC High 4:2:2 (YUV422P) dengan GPU context (`vo=gpu`, `gpu-context=android`)
 *    dan otomatis fallback software decoding libavcodec (`hwdec=auto-safe`).
 * 2. Rendering native libass dengan paritas 100% Aegisub desktop (`sub-ass=yes`, `sub-ass-override=no`, `sub-fonts-dir`),
 *    serta mempertahankan `sub-visibility=yes` dan `sid=auto` tetap aktif selama seek.
 * 3. Event observer sinkron di Main thread untuk `time-pos` -> [ProjectSessionManager.updatePlayhead],
 *    status loading media (`isMediaLoading`), dan kontrol scrubbing akurat (`relative+exact`, `absolute+exact`).
 */
class SubEditMpvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : BaseMPVView(context, attrs) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var playbackListener: MpvPlaybackListener? = null
    private var hasSubtitleTrack = false
    private var activeSubFilePath: String? = null
    private var activeMediaPath: String? = null
    private var isMpvInitialized = false
    private var isSurfaceCreated = false
    private var isSubtitleTemporarilyHidden = false
    private var isAwaitingFirstFrame = false
    private var isVoConfigured = false

    @Volatile
    var estimatedVfFps: Double = 0.0
        private set

    @Volatile
    var isMediaLoaded: Boolean = false
        private set

    @Volatile
    var hasLoadedVideo: Boolean = false
        private set

    @Volatile
    var timePos: Double = 0.0
        private set

    var isPlaying: Boolean = false
        private set

    private val loadingWatchdogRunnable = Runnable {
        if (isAwaitingFirstFrame || ProjectSessionManager.sessionState.value.isMediaLoading) {
            markFirstFrameRenderedOnMain()
        }
    }

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
        mpv.setOptionString("vd-lavc-threads", "0")

        // 2. BATAS CACHE MEMORI EKSPLISIT DEMUXER NATIVE LIBMPV (ANTI-OOM VIDEO PANJANG & .ASS VSI)
        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("demuxer-max-bytes", "32MiB")
        mpv.setOptionString("demuxer-max-back-bytes", "16MiB")

        // 3. OPSI AUDIO, KEEP-OPEN & KETAHANAN TRACK SUBTITLE SAAT SEEK
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("keep-open", "always")
        mpv.setOptionString("sub-auto", "no")
        mpv.setOptionString("sub-visibility", "yes")
        mpv.setOptionString("sid", "auto")
        mpv.setOptionString("hr-seek", "yes")

        // 4. OPSI RENDERING NATIVE LIBASS (100% PARITAS AEGISUB DESKTOP)
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
        mpv.setPropertyString("keep-open", "always")
        mpv.setPropertyString("sub-visibility", "yes")
        mpv.setPropertyString("sid", "auto")
    }

    override fun observeProperties() {
        mpv.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("vo-configured", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("estimated-vf-fps", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        isSurfaceCreated = true
        if (!isMpvInitialized) return
        runCatching {
            mpv.setPropertyString("vo", "gpu")
            if (width > 0 && height > 0) {
                mpv.setPropertyString("android-surface-size", "${width}x$height")
            }
        }
        val mediaPath = activeMediaPath
        if (mediaPath != null) {
            if (!isMediaLoaded) {
                dispatchLoadFileCommand(mediaPath)
            } else {
                runCatching {
                    mpv.command("seek", timePos.coerceAtLeast(0.0).toString(), "absolute+exact")
                }
                mainHandler.post {
                    requestLayout()
                    invalidate()
                }
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.surfaceChanged(holder, format, width, height)
        if (!isMpvInitialized) return
        if (isMediaLoaded && width > 0 && height > 0) {
            runCatching {
                mpv.command("seek", timePos.coerceAtLeast(0.0).toString(), "absolute+exact")
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceCreated = false
        super.surfaceDestroyed(holder)
    }

    private fun markFirstFrameRenderedOnMain() {
        mainHandler.removeCallbacks(loadingWatchdogRunnable)
        if (!isAwaitingFirstFrame && !ProjectSessionManager.sessionState.value.isMediaLoading) return
        isAwaitingFirstFrame = false
        isMediaLoaded = true
        hasLoadedVideo = true
        mainHandler.post {
            requestLayout()
            invalidate()
            ProjectSessionManager.setMediaLoaded(true)
            ensureSubtitleTrackVisibility()
            ProjectSessionManager.setMediaLoading(isLoading = false, progress = 100, stageMessage = "")
            playbackListener?.onMediaLoaded()
            activeSubFilePath?.let { subPath ->
                loadOrReloadSubtitle(subPath)
            }
        }
    }

    private fun setupEventObserver() {
        mpv.addObserver(object : MPV.EventObserver {
            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {}

            override fun eventProperty(property: String, value: Boolean) {
                when (property) {
                    "pause" -> {
                        if (!isMediaLoaded) return
                        isPlaying = !value
                        mainHandler.post {
                            ProjectSessionManager.updatePlaybackState(isPlaying)
                            playbackListener?.onPlaybackStateChanged(isPlaying)
                        }
                    }
                    "eof-reached" -> {
                        if (value && isMediaLoaded) {
                            isPlaying = false
                            mainHandler.post {
                                ProjectSessionManager.updatePlaybackState(false)
                                playbackListener?.onPlaybackStateChanged(false)
                            }
                        }
                    }
                    "vo-configured" -> {
                        isVoConfigured = value
                        // Tutup overlay loading segera setelah vo-configured aktif saat media telah dimuat
                        if (value && isMediaLoaded) {
                            markFirstFrameRenderedOnMain()
                        }
                    }
                }
            }

            override fun eventProperty(property: String, value: String) {}

            override fun eventProperty(property: String, value: Double) {
                when (property) {
                    "time-pos" -> {
                        if (!isMediaLoaded) return
                        val clampedSec = value.coerceAtLeast(0.0)
                        timePos = clampedSec
                        val timeUs = (clampedSec * 1_000_000.0).toLong().coerceAtLeast(0L)
                        if (isAwaitingFirstFrame || clampedSec > 0.0) {
                            markFirstFrameRenderedOnMain()
                        }
                        mainHandler.post {
                            ProjectSessionManager.updatePlayhead(timeUs)
                            playbackListener?.onTimePosChanged(timeUs)
                        }
                    }
                    "duration" -> {
                        if (!isMediaLoaded) return
                        val durUs = (value * 1_000_000.0).toLong().coerceAtLeast(0L)
                        mainHandler.post {
                            ProjectSessionManager.setMediaDuration(durUs)
                            playbackListener?.onDurationChanged(durUs)
                        }
                    }
                    "estimated-vf-fps" -> {
                        estimatedVfFps = value
                        if (value > 0.0 && isMediaLoaded) {
                            markFirstFrameRenderedOnMain()
                        }
                    }
                }
            }

            override fun eventProperty(property: String, value: MPVNode) {}

            override fun event(eventId: Int, node: MPVNode) {
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                        isMediaLoaded = true
                        hasLoadedVideo = true
                        isAwaitingFirstFrame = true
                        // Kirim seek awal 0 absolute+exact segera setelah file-loaded diterima
                        // agar GPU langsung menggambar frame pertama ke permukaan SurfaceView
                        runCatching {
                            mpv.command("seek", "0", "absolute+exact")
                        }
                        // Batas waktu pengaman (watchdog) maksimal 2,5 detik setelah file-loaded
                        mainHandler.removeCallbacks(loadingWatchdogRunnable)
                        mainHandler.postDelayed(loadingWatchdogRunnable, WATCHDOG_TIMEOUT_MS)
                        mainHandler.post {
                            if (holder.surface != null && holder.surface.isValid) {
                                runCatching {
                                    mpv.attachSurface(holder.surface)
                                    if (width > 0 && height > 0) {
                                        mpv.setPropertyString("android-surface-size", "${width}x$height")
                                    }
                                    mpv.setPropertyString("vo", "gpu")
                                    mpv.command("seek", "0", "absolute+exact")
                                }
                            }
                            requestLayout()
                            invalidate()
                            ProjectSessionManager.setMediaLoaded(true)
                            ensureSubtitleTrackVisibility()
                            activeSubFilePath?.let { subPath ->
                                loadOrReloadSubtitle(subPath)
                            }
                        }
                        if (isVoConfigured || timePos > 0.0 || estimatedVfFps > 0.0) {
                            markFirstFrameRenderedOnMain()
                        }
                    }
                    MPV_EVENT_PLAYBACK_RESTART -> {
                        isMediaLoaded = true
                        hasLoadedVideo = true
                        markFirstFrameRenderedOnMain()
                    }
                }
            }
        })
    }

    fun setPlaybackListener(listener: MpvPlaybackListener?) {
        this.playbackListener = listener
    }

    /**
     * Mem-polling metrik kesiapan frame (`timePos` dan `estimatedVfFps`) untuk watchdog loading.
     */
    fun isFirstFrameMetricReady(): Boolean {
        if (!isMpvInitialized || !isMediaLoaded) return false
        val queriedTime = runCatching { mpv.getPropertyDouble("time-pos") }.getOrNull()
        if (queriedTime != null && !queriedTime.isNaN() && queriedTime >= 0.0) {
            timePos = queriedTime
        }
        val queriedFps = runCatching { mpv.getPropertyDouble("estimated-vf-fps") }.getOrNull()
        if (queriedFps != null && !queriedFps.isNaN() && queriedFps > 0.0) {
            estimatedVfFps = queriedFps
        }
        return isVoConfigured || timePos > 0.0 || estimatedVfFps > 0.0
    }

    /**
     * Mem-polling posisi waktu aktual `time-pos` dalam satuan detik untuk loop 60 FPS (`16ms`)
     * pada `CenterPlayheadWaveformCanvas`.
     */
    fun pollTimePosSeconds(): Double {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return timePos
        val queried = runCatching { mpv.getPropertyDouble("time-pos") }.getOrNull()
        if (queried != null && !queried.isNaN() && queried >= 0.0) {
            timePos = queried
        }
        return timePos
    }

    /**
     * Mengatur visibilitas track subtitle libass (`sub-visibility=yes` atau `sub-visibility=no`).
     * Digunakan oleh gizmo visual typesetting saat siklus `onDragStart` (`no`) dan `onDragEnd` (`yes`)
     * untuk mencegah ghosting / teks ganda bertumpuk.
     */
    fun setSubtitleVisibility(visible: Boolean) {
        isSubtitleTemporarilyHidden = !visible
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        runCatching {
            mpv.setPropertyString("sub-visibility", if (visible) "yes" else "no")
        }
    }

    /**
     * Memastikan properti `sub-visibility=yes` dan `sid=auto` tetap aktif selama pemutaran dan seek
     * (kecuali saat disembunyikan sementara oleh drag gizmo).
     */
    private fun ensureSubtitleTrackVisibility() {
        if (!isMpvInitialized || !isMediaLoaded || isSubtitleTemporarilyHidden) return
        runCatching {
            mpv.setPropertyString("sub-visibility", "yes")
            if (!hasSubtitleTrack) {
                mpv.setPropertyString("sid", "auto")
            }
        }
    }

    private fun dispatchLoadFileCommand(pathOrUri: String, isAudioOnly: Boolean = false) {
        runCatching {
            if (holder.surface != null && holder.surface.isValid) {
                mpv.attachSurface(holder.surface)
                if (width > 0 && height > 0) {
                    mpv.setPropertyString("android-surface-size", "${width}x$height")
                }
                mpv.setPropertyString("vo", "gpu")
                mpv.command("loadfile", pathOrUri)
            } else if (isAudioOnly) {
                mpv.setPropertyString("vo", "null")
                mpv.command("loadfile", pathOrUri)
            }
            // Jika video dipilih namun SurfaceView belum selesai surfaceCreated,
            // activeMediaPath sudah tersimpan dan akan langsung dieksekusi pada callback surfaceCreated().
        }.onFailure { err ->
            Log.e(TAG, "Gagal mengirim perintah loadfile ke libmpv: ${err.message}", err)
        }
        // Paksa bangun siklus render SurfaceView setelah loadfile dikirim
        mainHandler.post {
            requestLayout()
            invalidate()
        }
    }

    /**
     * Memuat file video atau audio ke dalam pemutar mpv, memaksa bangun siklus render `SurfaceView`
     * (`requestLayout()` + `invalidate()`), dan menahan indikator `isMediaLoading = true` hingga
     * frame perdana dirender atau batas waktu pengaman (*watchdog* 2,5 detik setelah `file-loaded`) tercapai.
     */
    fun loadMedia(pathOrUri: String, isAudioOnly: Boolean = false) {
        if (pathOrUri.isBlank()) return
        activeMediaPath = pathOrUri
        isMediaLoaded = false
        hasLoadedVideo = false
        hasSubtitleTrack = false
        isAwaitingFirstFrame = true
        timePos = 0.0
        estimatedVfFps = 0.0
        mainHandler.removeCallbacks(loadingWatchdogRunnable)
        mainHandler.post {
            ProjectSessionManager.setMediaLoaded(false)
            ProjectSessionManager.setMediaLoading(
                isLoading = true,
                progress = 85,
                stageMessage = "Menginisialisasi demuxer & merender frame perdana...",
            )
            playbackListener?.onMediaLoadingStarted()
        }
        if (!isMpvInitialized) return
        dispatchLoadFileCommand(pathOrUri, isAudioOnly = isAudioOnly)
    }

    fun clearLoadedMediaState() {
        mainHandler.removeCallbacks(loadingWatchdogRunnable)
        activeMediaPath = null
        isMediaLoaded = false
        hasLoadedVideo = false
        hasSubtitleTrack = false
        isAwaitingFirstFrame = false
        timePos = 0.0
        estimatedVfFps = 0.0
        ProjectSessionManager.setMediaLoaded(false)
    }

    /**
     * Memulai pemutaran audio/video via `mpv.setPropertyBoolean("pause", false)`.
     * Guard penuh: tidak dieksekusi jika `isMediaLoaded == false`.
     */
    fun play() {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        ensureSubtitleTrackVisibility()
        mpv.setPropertyBoolean("pause", false)
        isPlaying = true
    }

    /**
     * Menghentikan sementara pemutaran via `mpv.setPropertyBoolean("pause", true)`.
     * Guard penuh: tidak dieksekusi jika `isMediaLoaded == false`.
     */
    fun pause() {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        mpv.setPropertyBoolean("pause", true)
        isPlaying = false
    }

    /**
     * Toggle play/pause pemutaran.
     * Guard penuh: tidak dieksekusi jika `isMediaLoaded == false`.
     */
    fun togglePlay() {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        if (isPlaying) pause() else play()
    }

    /**
     * Melompat ke titik waktu detik tertentu secara presisi (`seek <sec> absolute+exact`)
     * dengan tetap mempertahankan `sub-visibility=yes` dan `sid=auto` tanpa me-remove track subtitle.
     * Guard penuh: tidak dieksekusi jika `isMediaLoaded == false`.
     */
    fun seekToSeconds(seconds: Double) {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        val clampedSec = seconds.coerceAtLeast(0.0)
        timePos = clampedSec
        ensureSubtitleTrackVisibility()
        mpv.command("seek", clampedSec.toString(), "absolute+exact")
    }

    /**
     * Nudge waktu maju atau mundur dalam detik secara presisi (`seek <deltaSec> relative+exact`)
     * dengan tetap mempertahankan `keep-open=always` dan `sub-visibility=yes`.
     * Guard penuh: tidak dieksekusi jika `isMediaLoaded == false`.
     */
    fun seekRelativeExact(deltaSeconds: Double) {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        ensureSubtitleTrackVisibility()
        mpv.command("seek", deltaSeconds.toString(), "relative+exact")
    }

    /**
     * Memuat ulang berkas subtitle baru (saat impor berkas `.ass` / `.srt`) secara aman:
     * - Guard penuh: Dilarang mengirim perintah native ke `libmpv` jika `isMediaLoaded == false`.
     * - Mengeksekusi `sub-remove`, `sub-add <tempAssPath> select`, dan `sub-visibility=yes`
     *   secara berurutan di Main thread.
     */
    fun reloadFreshSubtitleFile(subFilePath: String) {
        if (subFilePath.isBlank()) return
        activeSubFilePath = subFilePath
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return

        val action = Runnable {
            if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return@Runnable
            try {
                if (hasSubtitleTrack) {
                    runCatching { mpv.command("sub-remove") }
                }
                mpv.setPropertyString("sid", "auto")
                mpv.command("sub-add", subFilePath, "select")
                hasSubtitleTrack = true
                if (!isSubtitleTemporarilyHidden) {
                    mpv.setPropertyString("sub-visibility", "yes")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal melakukan reloadFreshSubtitleFile ke libass: ${e.message}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    /**
     * Memuat atau me-reload track subtitle ASS langsung ke core libass:
     * - Guard penuh: Tidak pernah mengirim perintah `sub-add` atau `sub-reload` jika `isMediaLoaded == false`.
     * - Pertama kali memuat: `sub-add <path> select`
     * - Update real-time saat `contentRevision` berubah (pengetikan/drag/hapus/undo/redo): `sub-reload`
     */
    fun loadOrReloadSubtitle(subFilePath: String) {
        if (subFilePath.isBlank()) return
        activeSubFilePath = subFilePath
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return

        val action = Runnable {
            if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return@Runnable
            try {
                if (!isSubtitleTemporarilyHidden) {
                    mpv.setPropertyString("sub-visibility", "yes")
                }
                if (!hasSubtitleTrack) {
                    mpv.setPropertyString("sid", "auto")
                    mpv.command("sub-add", subFilePath, "select")
                    hasSubtitleTrack = true
                } else {
                    mpv.command("sub-reload")
                }
                if (!isSubtitleTemporarilyHidden) {
                    mpv.setPropertyString("sub-visibility", "yes")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal memuat/reload subtitle ke libass: ${e.message}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    companion object {
        private const val TAG = "SubEditMPVView"
        private const val MPV_EVENT_PLAYBACK_RESTART = 21
        private const val WATCHDOG_TIMEOUT_MS = 2_500L
    }
}
