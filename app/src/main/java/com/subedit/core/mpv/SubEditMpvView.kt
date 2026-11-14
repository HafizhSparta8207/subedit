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

interface MpvPlaybackListener {
    fun onTimePosChanged(timeUs: Long)
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun onDurationChanged(durationUs: Long)
    fun onMediaLoadingStarted() {}
    fun onMediaLoaded()
}

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

        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("hwdec", "auto-safe")
        mpv.setOptionString("hwdec-codecs", "h264,hevc,vp9,av1")
        mpv.setOptionString("vd-lavc-threads", "0")

        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("demuxer-max-bytes", "32MiB")
        mpv.setOptionString("demuxer-max-back-bytes", "16MiB")

        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("keep-open", "always")
        mpv.setOptionString("sub-auto", "no")
        mpv.setOptionString("sub-visibility", "yes")
        mpv.setOptionString("sid", "auto")
        mpv.setOptionString("hr-seek", "yes")

        mpv.setOptionString("sub-ass", "yes")
        mpv.setOptionString("sub-ass-override", "no")
        mpv.setOptionString("sub-scale-with-window", "yes")
        if (fontsDir.exists()) {
            mpv.setOptionString("sub-fonts-dir", fontsDir.absolutePath)
        }
    }

    override fun postInitOptions() {
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
                        runCatching {
                            mpv.command("seek", "0", "absolute+exact")
                        }
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

    fun pollTimePosSeconds(): Double {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return timePos
        val queried = runCatching { mpv.getPropertyDouble("time-pos") }.getOrNull()
        if (queried != null && !queried.isNaN() && queried >= 0.0) {
            timePos = queried
        }
        return timePos
    }

    fun setSubtitleVisibility(visible: Boolean) {
        isSubtitleTemporarilyHidden = !visible
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        runCatching {
            mpv.setPropertyString("sub-visibility", if (visible) "yes" else "no")
        }
    }

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
        }.onFailure { err ->
            Log.e(TAG, "Gagal mengirim perintah loadfile ke libmpv: ${err.message}", err)
        }
        mainHandler.post {
            requestLayout()
            invalidate()
        }
    }

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

    fun play() {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        ensureSubtitleTrackVisibility()
        mpv.setPropertyBoolean("pause", false)
        isPlaying = true
    }

    fun pause() {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        mpv.setPropertyBoolean("pause", true)
        isPlaying = false
    }

    fun togglePlay() {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        if (isPlaying) pause() else play()
    }

    fun seekToSeconds(seconds: Double) {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        val clampedSec = seconds.coerceAtLeast(0.0)
        timePos = clampedSec
        ensureSubtitleTrackVisibility()
        mpv.command("seek", clampedSec.toString(), "absolute+exact")
    }

    fun seekRelativeExact(deltaSeconds: Double) {
        if (!isMpvInitialized || !isMediaLoaded || !ProjectSessionManager.isMediaLoaded()) return
        ensureSubtitleTrackVisibility()
        mpv.command("seek", deltaSeconds.toString(), "relative+exact")
    }

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
