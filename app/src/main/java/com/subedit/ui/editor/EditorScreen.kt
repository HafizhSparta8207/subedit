package com.subedit.ui.editor

import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.subedit.core.audio.ThermalAudioWaveformExtractor
import com.subedit.core.audio.WaveformData
import com.subedit.core.domain.ActiveSubtitleResolver
import com.subedit.core.domain.AssDialogueEvent
import com.subedit.core.domain.AssProjectItem
import com.subedit.core.domain.AssScriptInfo
import com.subedit.core.domain.AssStyle
import com.subedit.core.domain.DEFAULT_MAX_SAFE_CPL
import com.subedit.core.domain.DialogueTokenizer
import com.subedit.core.domain.SubTime
import com.subedit.core.domain.SubtitleVisualTransform
import com.subedit.core.domain.TimingConstraints
import com.subedit.core.domain.VisualTypesetTool
import com.subedit.core.domain.start
import com.subedit.core.font.FontRepository
import com.subedit.core.mpv.MpvMediaHelper
import com.subedit.core.mpv.MpvPlaybackListener
import com.subedit.core.mpv.SubEditMpvView
import com.subedit.core.session.AssScript
import com.subedit.core.session.ProjectSessionManager
import com.subedit.core.video.ChromaProxyConverter
import com.subedit.ui.editor.dialogs.ColorSelectorDialog
import com.subedit.ui.editor.dialogs.HelpDialog
import com.subedit.ui.editor.dialogs.PropertiesDialog
import com.subedit.ui.editor.dialogs.StyleManagerDialog
import com.subedit.ui.editor.dialogs.SubtitleFormatErrorDialog
import com.subedit.ui.theme.AccentAmber
import com.subedit.ui.theme.AccentCoral
import com.subedit.ui.theme.AccentCyan
import com.subedit.ui.theme.AccentGreen
import com.subedit.ui.theme.AccentPurple
import com.subedit.ui.theme.BgCard
import com.subedit.ui.theme.BgDarkest
import com.subedit.ui.theme.BgMain
import com.subedit.ui.theme.BgSurface
import com.subedit.ui.theme.BgTimeline
import com.subedit.ui.theme.BorderColor
import com.subedit.ui.theme.BorderHighlight
import com.subedit.ui.theme.ChipOutlineColor
import com.subedit.ui.theme.ChipPrimaryColor
import com.subedit.ui.theme.ChipSecondaryColor
import com.subedit.ui.theme.ChipShadowColor
import com.subedit.ui.theme.TextMain
import com.subedit.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class EditorHistorySnapshot(
    val scriptInfo: AssScriptInfo,
    val styles: List<AssStyle>,
    val events: List<AssDialogueEvent>,
    val activeEventId: Int?,
)

@Composable
fun EditorScreen(
    project: AssProjectItem,
    subtitleImportVersion: Int = 0,
    videoUri: Uri?,
    audioUri: Uri?,
    externalErrorMessage: String?,
    onClearExternalError: () -> Unit,
    onBackToHome: () -> Unit,
    onRequestOpenSubtitle: () -> Unit,
    onRequestOpenVideo: () -> Unit,
    onRequestOpenAudio: () -> Unit,
    onRequestLoadKeyframes: () -> Unit,
    onSaveAssFile: (AssScriptInfo, List<AssStyle>, List<AssDialogueEvent>) -> Unit,
    maxSafeCpl: Int = DEFAULT_MAX_SAFE_CPL,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var scriptInfo by remember(project.id) { mutableStateOf(project.scriptInfo) }
    val styles = remember(project.id) {
        mutableStateListOf<AssStyle>().apply {
            addAll(project.styles.ifEmpty { AssStyle.defaultSingleStyle() })
        }
    }
    val events = remember(project.id) {
        mutableStateListOf<AssDialogueEvent>().apply { addAll(project.events) }
    }
    val keyframesUs = remember(project.id, project.keyframesUs) {
        mutableStateListOf<Long>().apply { addAll(project.keyframesUs) }
    }

    var activeEventId by remember(project.id) {
        mutableStateOf<Int?>(project.events.firstOrNull()?.id)
    }

    val sessionState by ProjectSessionManager.sessionState.collectAsState()

    val mpvView = remember(context) {
        SubEditMpvView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    fun syncSubtitleWithMpv(forceFreshReAdd: Boolean = false) {
        if (videoUri == null || !mpvView.isMediaLoaded || !sessionState.isMediaLoaded) return
        runCatching {
            val liveAss = MpvMediaHelper.writeLiveAssPreview(
                context = context,
                scriptInfo = scriptInfo,
                styles = styles.toList(),
                events = events.toList(),
            )
            if (forceFreshReAdd) {
                mpvView.reloadFreshSubtitleFile(liveAss.absolutePath)
            } else {
                mpvView.loadOrReloadSubtitle(liveAss.absolutePath)
            }
        }
    }

    LaunchedEffect(project.id) {
        ProjectSessionManager.initializeSession(
            project = project,
            videoUri = videoUri,
            audioUri = audioUri,
        )
    }

    fun pushUndoSnapshot() {
        ProjectSessionManager.pushUndoSnapshot()
    }

    fun performUndo() {
        val prev = ProjectSessionManager.undo() ?: return
        scriptInfo = prev.script.scriptInfo
        styles.clear()
        styles.addAll(prev.script.styles)
        events.clear()
        events.addAll(prev.script.events)
        activeEventId = prev.activeEventId
        syncSubtitleWithMpv()
    }

    fun performRedo() {
        val next = ProjectSessionManager.redo() ?: return
        scriptInfo = next.script.scriptInfo
        styles.clear()
        styles.addAll(next.script.styles)
        events.clear()
        events.addAll(next.script.events)
        activeEventId = next.activeEventId
        syncSubtitleWithMpv()
    }

    val activeEventIndex by remember {
        derivedStateOf {
            val id = activeEventId ?: return@derivedStateOf -1
            events.indexOfFirst { it.id == id }
        }
    }
    val activeEvent by remember {
        derivedStateOf {
            val idx = activeEventIndex
            if (idx in events.indices) events[idx] else null
        }
    }

    var currentPlayheadUs by remember(project.id) {
        mutableLongStateOf(project.events.firstOrNull()?.startUs ?: 0L)
    }
    var isPlaying by remember { mutableStateOf(false) }

    val visibleVideoDialogue by remember {
        derivedStateOf {
            val currentActive = activeEvent
            if (currentActive != null && ActiveSubtitleResolver.isEventActiveUs(currentActive, currentPlayheadUs)) {
                currentActive
            } else {
                ActiveSubtitleResolver.findActiveDialogueUs(events, currentPlayheadUs)
            }
        }
    }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = activeEvent?.rawText.orEmpty(),
                selection = TextRange(activeEvent?.rawText?.length ?: 0),
            )
        )
    }
    LaunchedEffect(activeEventId, activeEvent?.rawText) {
        if (activeEventId == null) {
            if (textFieldValue.text.isNotEmpty()) {
                textFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
            }
        } else {
            val currentRaw = activeEvent?.rawText ?: return@LaunchedEffect
            if (textFieldValue.text != currentRaw) {
                textFieldValue = TextFieldValue(
                    text = currentRaw,
                    selection = TextRange(currentRaw.length),
                )
            }
        }
    }

    LaunchedEffect(subtitleImportVersion) {
        if (subtitleImportVersion > 0) {
            isPlaying = false
            activeEventId = null
            textFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
            ProjectSessionManager.setActiveEventId(null)
            ProjectSessionManager.resetUndoHistory()
            scriptInfo = project.scriptInfo
            styles.clear()
            styles.addAll(project.styles.ifEmpty { AssStyle.defaultSingleStyle() })
            events.clear()
            events.addAll(project.events)
            if (!sessionState.isMediaLoaded) {
                val firstDialogStartUs = project.events.firstOrNull { !it.isComment }?.startUs
                    ?: project.events.firstOrNull()?.startUs
                    ?: 0L
                currentPlayheadUs = firstDialogStartUs
                ProjectSessionManager.updatePlayhead(firstDialogStartUs)
                ProjectSessionManager.setActiveEventId(null)
            }
            syncSubtitleWithMpv(forceFreshReAdd = true)
        }
    }

    var waveformData by remember { mutableStateOf<WaveformData?>(null) }
    LaunchedEffect(audioUri, videoUri) {
        val targetMediaUri = audioUri ?: videoUri
        if (targetMediaUri == null) {
            waveformData = null
        } else {
            ThermalAudioWaveformExtractor.extractFromUri(
                context = context,
                mediaUri = targetMediaUri,
                onPartialWaveform = { partial ->
                    waveformData = partial
                },
            ).onSuccess { extracted ->
                waveformData = extracted
                if (sessionState.mediaDurationUs <= 0L) {
                    ProjectSessionManager.setMediaDuration(extracted.durationUs)
                }
            }
        }
    }

    DisposableEffect(mpvView) {
        mpvView.setPlaybackListener(object : MpvPlaybackListener {
            override fun onTimePosChanged(timeUs: Long) {
                currentPlayheadUs = timeUs
                val lineAtPlayhead = ProjectSessionManager.updatePlayhead(timeUs)
                if (lineAtPlayhead != null && activeEventId != lineAtPlayhead.id) {
                    activeEventId = lineAtPlayhead.id
                    ProjectSessionManager.setActiveEventId(lineAtPlayhead.id)
                }
            }

            override fun onPlaybackStateChanged(playing: Boolean) {
                isPlaying = playing
                ProjectSessionManager.updatePlaybackState(playing)
            }

            override fun onDurationChanged(durationUs: Long) {
                ProjectSessionManager.setMediaDuration(durationUs)
            }

            override fun onMediaLoadingStarted() {
                ProjectSessionManager.setMediaLoading(
                    isLoading = true,
                    progress = 85,
                    stageMessage = "Menginisialisasi demuxer & merender frame perdana...",
                )
            }

            override fun onMediaLoaded() {
                ProjectSessionManager.setMediaLoaded(true)
                ProjectSessionManager.setMediaLoading(isLoading = false, progress = 100, stageMessage = "")
                syncSubtitleWithMpv(forceFreshReAdd = true)
            }
        })
        onDispose {
            mpvView.setPlaybackListener(null)
            mpvView.destroy()
            MpvMediaHelper.closeActiveFileDescriptor()
        }
    }

    LaunchedEffect(videoUri, audioUri) {
        val vUri = videoUri
        val aUri = audioUri
        if (vUri != null) {
            ProjectSessionManager.setMediaLoaded(false)
            ProjectSessionManager.setMediaLoading(
                isLoading = true,
                progress = 5,
                stageMessage = "Memeriksa format piksel stream video...",
            )
            val resolvedPath = ChromaProxyConverter.resolveOrConvertVideoIfNeeded(
                context = context,
                videoUri = vUri,
                onConversionProgress = { pct, msg ->
                    ProjectSessionManager.updateLoadingProgress(progress = pct, stageMessage = msg)
                },
            )
            mpvView.loadMedia(resolvedPath)
        } else if (aUri != null) {
            ProjectSessionManager.setMediaLoaded(false)
            ProjectSessionManager.setMediaLoading(
                isLoading = true,
                progress = 20,
                stageMessage = "Membuka trek audio...",
            )
            val pathOrUri = MpvMediaHelper.resolveUriToPath(context, aUri)
            mpvView.loadMedia(pathOrUri, isAudioOnly = true)
        } else {
            mpvView.clearLoadedMediaState()
            MpvMediaHelper.closeActiveFileDescriptor()
            ProjectSessionManager.setMediaLoaded(false)
            ProjectSessionManager.setMediaLoading(isLoading = false, progress = 0, stageMessage = "")
        }
    }

    LaunchedEffect(sessionState.contentRevision, videoUri, sessionState.isMediaLoaded) {
        if (videoUri != null && sessionState.isMediaLoaded && mpvView.isMediaLoaded && sessionState.contentRevision > 0L) {
            delay(150L)
            syncSubtitleWithMpv()
        }
    }

    LaunchedEffect(isPlaying, sessionState.isMediaLoaded, videoUri, audioUri) {
        val hasLoadedNativeMedia = sessionState.isMediaLoaded && mpvView.isMediaLoaded && (videoUri != null || audioUri != null)
        if (hasLoadedNativeMedia) {
            while (isActive && isPlaying) {
                val posSec = mpvView.pollTimePosSeconds()
                if (posSec >= 0.0) {
                    val posUs = (posSec * SubTime.US_PER_SEC).toLong().coerceAtLeast(0L)
                    currentPlayheadUs = posUs
                    val lineAtPlayhead = ProjectSessionManager.updatePlayhead(posUs)
                    if (lineAtPlayhead != null && activeEventId != lineAtPlayhead.id) {
                        activeEventId = lineAtPlayhead.id
                        ProjectSessionManager.setActiveEventId(lineAtPlayhead.id)
                    }
                }
                delay(16L)
            }
        } else {
            while (isActive && isPlaying) {
                val maxEventEndUs = events.maxOfOrNull { it.endUs } ?: 0L
                val virtualDurationUs = max(
                    waveformData?.durationUs ?: 0L,
                    if (maxEventEndUs > 0L) maxEventEndUs else 60L * SubTime.US_PER_SEC,
                )
                val nextUs = currentPlayheadUs + 16_000L
                val nextPlayheadUs = if (nextUs >= virtualDurationUs) {
                    isPlaying = false
                    ProjectSessionManager.updatePlaybackState(false)
                    0L
                } else {
                    nextUs
                }
                currentPlayheadUs = nextPlayheadUs
                val lineAtPlayhead = ActiveSubtitleResolver.findActiveDialogueUs(events, nextPlayheadUs)
                activeEventId = lineAtPlayhead?.id
                ProjectSessionManager.updatePlayhead(nextPlayheadUs)
                ProjectSessionManager.setActiveEventId(lineAtPlayhead?.id)
                delay(16L)
            }
        }
    }

    fun seekPlayheadTo(newTimeUs: Long, syncSelectionWithPlayhead: Boolean = false) {
        val maxEventEndUs = events.maxOfOrNull { it.endUs } ?: 0L
        val maxDur = max(
            max(sessionState.mediaDurationUs, waveformData?.durationUs ?: 0L),
            max(maxEventEndUs + 60L * SubTime.US_PER_SEC, 3_600L * SubTime.US_PER_SEC),
        )
        val clampedUs = newTimeUs.coerceIn(0L, maxDur)
        currentPlayheadUs = clampedUs
        ProjectSessionManager.updatePlayhead(clampedUs)
        if (sessionState.isMediaLoaded && mpvView.isMediaLoaded && (videoUri != null || audioUri != null)) {
            mpvView.seekToSeconds(clampedUs.toDouble() / SubTime.US_PER_SEC.toDouble())
        }
        if (syncSelectionWithPlayhead) {
            val hit = ActiveSubtitleResolver.findActiveDialogueUs(events, clampedUs)
            activeEventId = hit?.id
            ProjectSessionManager.setActiveEventId(hit?.id)
        }
    }

    fun togglePlayback() {
        if (sessionState.isMediaLoaded && mpvView.isMediaLoaded && (videoUri != null || audioUri != null)) {
            mpvView.togglePlay()
        } else {
            val nextPlaying = !isPlaying
            isPlaying = nextPlaying
            ProjectSessionManager.updatePlaybackState(nextPlaying)
        }
    }

    var isLineTableMode by remember { mutableStateOf(false) }
    var activeTypesetTool by remember { mutableStateOf(VisualTypesetTool.STANDARD) }
    var showStyleManagerModal by remember { mutableStateOf(false) }
    var showPropertiesModal by remember { mutableStateOf(false) }
    var showHelpModal by remember { mutableStateOf(false) }
    var activeColorSelectorTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeEventId) {
        if (activeEventId == null && activeTypesetTool != VisualTypesetTool.STANDARD) {
            activeTypesetTool = VisualTypesetTool.STANDARD
        }
    }

    fun updateActiveDialogue(
        recordUndo: Boolean = false,
        syncMpv: Boolean = false,
        transform: (AssDialogueEvent) -> AssDialogueEvent,
    ) {
        val idx = activeEventIndex
        if (idx in events.indices) {
            if (recordUndo) pushUndoSnapshot()
            val updated = transform(events[idx])
            events[idx] = updated
            ProjectSessionManager.updateScript { curr ->
                val list = curr.events.toMutableList()
                val sIdx = list.indexOfFirst { it.id == updated.id }
                if (sIdx != -1) list[sIdx] = updated else list.add(updated)
                curr.copy(events = list)
            }
            if (syncMpv) {
                syncSubtitleWithMpv()
            }
        }
    }

    fun addNewLineAtPlayhead(
        initialText: String? = null,
        initialSelection: TextRange? = null,
    ): AssDialogueEvent {
        val defaultStyle = styles.firstOrNull()?.name ?: "Default"
        val newLine = ProjectSessionManager.addEvent(
            initialText = initialText ?: "",
            startUs = currentPlayheadUs,
            endUs = currentPlayheadUs + 2_000_000L,
            styleName = defaultStyle,
        )
        events.clear()
        events.addAll(ProjectSessionManager.getCurrentScript().events)
        activeEventId = newLine.id
        textFieldValue = TextFieldValue(
            text = newLine.rawText,
            selection = initialSelection ?: TextRange(newLine.rawText.length),
        )
        syncSubtitleWithMpv()
        return newLine
    }

    fun deleteActiveLine() {
        val targetId = activeEventId ?: return
        val nextActiveId = ProjectSessionManager.deleteEvent(targetId)
        events.clear()
        events.addAll(ProjectSessionManager.getCurrentScript().events)
        activeEventId = nextActiveId
        if (nextActiveId == null) {
            activeTypesetTool = VisualTypesetTool.STANDARD
            textFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
        }
        syncSubtitleWithMpv()
    }

    fun insertNewlineTokenAtCursor() {
        val token = "\\N"
        if (activeEventId == null) {
            addNewLineAtPlayhead(token)
            return
        }
        val currentVal = textFieldValue
        val start = currentVal.selection.min.coerceIn(0, currentVal.text.length)
        val end = currentVal.selection.max.coerceIn(0, currentVal.text.length)
        val updatedText = currentVal.text.substring(0, start) + token + currentVal.text.substring(end)
        val newCursor = start + token.length
        textFieldValue = TextFieldValue(
            text = updatedText,
            selection = TextRange(newCursor),
        )
        updateActiveDialogue(recordUndo = true) { it.copy(rawText = updatedText) }
    }

    fun toggleFormatTagOnActiveDialogue(tagKey: String) {
        val cleanKey = tagKey.trim().removePrefix("{").removeSuffix("}").removePrefix("\\").take(1).lowercase()
        if (activeEventId == null) {
            addNewLineAtPlayhead("{\\${cleanKey}1}")
            return
        }
        val currentStyle = styles.find { it.name == activeEvent?.style } ?: styles.firstOrNull() ?: AssStyle()
        val styleDefault = when (cleanKey) {
            "b" -> currentStyle.bold
            "i" -> currentStyle.italic
            "u" -> currentStyle.underline
            "s" -> currentStyle.strikeout
            else -> false
        }
        updateActiveDialogue(recordUndo = true, syncMpv = true) { ev ->
            val updatedText = DialogueTokenizer.toggleFormatTag(
                rawText = ev.rawText,
                tagKey = cleanKey,
                styleDefaultEnabled = styleDefault,
            )
            textFieldValue = TextFieldValue(
                text = updatedText,
                selection = TextRange(updatedText.length),
            )
            ev.copy(rawText = updatedText)
        }
    }

    fun applyColorTagToActiveDialogue(formattedAssTag: String) {
        if (activeEventId == null) {
            val initialText = DialogueTokenizer.applyColorOverrideTag("", formattedAssTag)
            addNewLineAtPlayhead(initialText)
            return
        }
        updateActiveDialogue(recordUndo = true, syncMpv = true) { ev ->
            val updatedText = DialogueTokenizer.applyColorOverrideTag(
                rawText = ev.rawText,
                formattedColorTagBlock = formattedAssTag,
            )
            textFieldValue = TextFieldValue(
                text = updatedText,
                selection = TextRange(updatedText.length),
            )
            ev.copy(rawText = updatedText)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BgDarkest)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            EditorTopToolbar(
                canUndo = sessionState.canUndo,
                canRedo = sessionState.canRedo,
                onBackClick = onBackToHome,
                onUndoClick = ::performUndo,
                onRedoClick = ::performRedo,
                onOpenSubtitle = onRequestOpenSubtitle,
                onOpenVideo = onRequestOpenVideo,
                onOpenAudio = onRequestOpenAudio,
                onSaveAss = { onSaveAssFile(scriptInfo, styles.toList(), events.toList()) },
                onHelpClick = { showHelpModal = true },
            )

            if (isLandscape) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.58f),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxHeight()
                                .background(Color.Black),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                VideoMonitor16by9(
                                    mpvView = mpvView,
                                    hasVideoUri = videoUri != null,
                                    isMediaLoading = sessionState.isMediaLoading,
                                    loadingProgress = sessionState.loadingProgress,
                                    loadingStageMessage = sessionState.loadingStageMessage,
                                    visibleDialogue = visibleVideoDialogue,
                                    styles = styles,
                                    scriptInfo = scriptInfo,
                                    activeTypesetTool = activeTypesetTool,
                                    onHeadTagsUpdated = { tagUpdates ->
                                        updateActiveDialogue(recordUndo = true, syncMpv = true) { ev ->
                                            val updated = DialogueTokenizer.injectOrUpdateHeadTags(
                                                ev.rawText,
                                                tagUpdates,
                                            )
                                            textFieldValue = TextFieldValue(updated, TextRange(updated.length))
                                            ev.copy(rawText = updated)
                                        }
                                    },
                                )
                            }

                            QuickActionBar7Buttons(
                                hasActiveLine = activeEvent != null,
                                isPlaying = isPlaying,
                                isLineTableMode = isLineTableMode,
                                onDynamicLineAction = {
                                    if (activeEvent != null) deleteActiveLine() else addNewLineAtPlayhead()
                                },
                                onShiftLeadIn = {
                                    if (activeEvent == null) return@QuickActionBar7Buttons
                                    updateActiveDialogue(recordUndo = true) { ev ->
                                        val proposedStart = if (currentPlayheadUs <= ev.endUs - TimingConstraints.MIN_DURATION_US &&
                                            kotlin.math.abs(currentPlayheadUs - ev.startUs) > 50_000L
                                        ) {
                                            currentPlayheadUs
                                        } else {
                                            (ev.startUs - 100_000L).coerceAtLeast(0L)
                                        }
                                        val nextStart = TimingConstraints.clampStartUs(proposedStart, ev.endUs)
                                        ev.copy(startUs = nextStart)
                                    }
                                },
                                onNudgePlayheadBackward = {
                                    if (activeEvent == null) return@QuickActionBar7Buttons
                                    if (sessionState.isMediaLoaded && mpvView.isMediaLoaded && (videoUri != null || audioUri != null)) {
                                        mpvView.seekRelativeExact(-0.050)
                                    } else {
                                        seekPlayheadTo(currentPlayheadUs - 50_000L)
                                    }
                                },
                                onTogglePlay = ::togglePlayback,
                                onNudgePlayheadForward = {
                                    if (activeEvent == null) return@QuickActionBar7Buttons
                                    if (sessionState.isMediaLoaded && mpvView.isMediaLoaded && (videoUri != null || audioUri != null)) {
                                        mpvView.seekRelativeExact(0.050)
                                    } else {
                                        seekPlayheadTo(currentPlayheadUs + 50_000L)
                                    }
                                },
                                onShiftLeadOut = {
                                    if (activeEvent == null) return@QuickActionBar7Buttons
                                    val effectiveDurUs = max(sessionState.mediaDurationUs, waveformData?.durationUs ?: 0L)
                                    updateActiveDialogue(recordUndo = true) { ev ->
                                        val proposedEnd = if (currentPlayheadUs >= ev.startUs + TimingConstraints.MIN_DURATION_US &&
                                            kotlin.math.abs(currentPlayheadUs - ev.endUs) > 50_000L
                                        ) {
                                            currentPlayheadUs
                                        } else {
                                            ev.endUs + 100_000L
                                        }
                                        val nextEnd = TimingConstraints.clampEndUs(ev.startUs, proposedEnd, effectiveDurUs)
                                        ev.copy(endUs = nextEnd)
                                    }
                                },
                                onToggleAudioOrLineMode = { isLineTableMode = !isLineTableMode },
                                onLoadKeyframes = onRequestLoadKeyframes,
                                onOpenScriptProperties = { showPropertiesModal = true },
                                onOpenStyleManager = { showStyleManagerModal = true },
                                onDeselectOrAddLine = {
                                    if (activeEventId != null) {
                                        activeEventId = null
                                        ProjectSessionManager.setActiveEventId(null)
                                    } else {
                                        addNewLineAtPlayhead()
                                    }
                                },
                            )
                        }

                        AssInspectorPanel(
                            activeEvent = activeEvent,
                            styles = styles,
                            textFieldValue = textFieldValue,
                            maxSafeCpl = maxSafeCpl,
                            onTextFieldValueChange = { newValue ->
                                handleTextFieldChangeWithEnterIntercept(
                                    oldValue = textFieldValue,
                                    newValue = newValue,
                                    onUpdateState = { finalTfv ->
                                        textFieldValue = finalTfv
                                        if (activeEventId == null) {
                                            if (finalTfv.text.isNotEmpty()) {
                                                addNewLineAtPlayhead(
                                                    initialText = finalTfv.text,
                                                    initialSelection = finalTfv.selection,
                                                )
                                            }
                                        } else {
                                            updateActiveDialogue { it.copy(rawText = finalTfv.text) }
                                        }
                                    },
                                )
                            },
                            onInsertEnterNewline = ::insertNewlineTokenAtCursor,
                            onCommentChanged = { checked ->
                                updateActiveDialogue(recordUndo = true) { it.copy(isComment = checked) }
                            },
                            onStyleSelected = { styleName ->
                                updateActiveDialogue(recordUndo = true) { it.copy(style = styleName) }
                            },
                            onEditStylesRequested = { showStyleManagerModal = true },
                            onActorChanged = { actor ->
                                updateActiveDialogue { it.copy(actor = actor) }
                            },
                            onEffectChanged = { effect ->
                                updateActiveDialogue { it.copy(effect = effect) }
                            },
                            onQuickTagChipClicked = { tagKey -> toggleFormatTagOnActiveDialogue(tagKey) },
                            onColorTagChipClicked = { colorTagType ->
                                activeColorSelectorTag = colorTagType
                            },
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxHeight(),
                        )
                    }

                    TimelineOrLineGridSection(
                        isLineTableMode = isLineTableMode,
                        waveformData = waveformData,
                        events = events,
                        activeEventId = activeEventId,
                        currentPlayheadUs = currentPlayheadUs,
                        keyframesUs = keyframesUs,
                        mediaDurationUs = max(sessionState.mediaDurationUs, waveformData?.durationUs ?: 0L),
                        onPlayheadScrubbed = { scrubbedUs ->
                            seekPlayheadTo(scrubbedUs, syncSelectionWithPlayhead = true)
                        },
                        onActiveTimingChanged = { newStartUs, newEndUs, finished ->
                            updateActiveDialogue(recordUndo = finished, syncMpv = finished) {
                                it.copy(startUs = newStartUs, endUs = newEndUs)
                            }
                        },
                        onSelectEvent = { id ->
                            activeEventId = id
                            ProjectSessionManager.setActiveEventId(id)
                            if (id != null) {
                                events.find { it.id == id }?.let { seekPlayheadTo(it.start.microseconds) }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.42f),
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    VideoMonitor16by9(
                        mpvView = mpvView,
                        hasVideoUri = videoUri != null,
                        isMediaLoading = sessionState.isMediaLoading,
                        loadingProgress = sessionState.loadingProgress,
                        loadingStageMessage = sessionState.loadingStageMessage,
                        visibleDialogue = visibleVideoDialogue,
                        styles = styles,
                        scriptInfo = scriptInfo,
                        activeTypesetTool = activeTypesetTool,
                        onHeadTagsUpdated = { tagUpdates ->
                            updateActiveDialogue(recordUndo = true, syncMpv = true) { ev ->
                                val updated = DialogueTokenizer.injectOrUpdateHeadTags(
                                    ev.rawText,
                                    tagUpdates,
                                )
                                textFieldValue = TextFieldValue(updated, TextRange(updated.length))
                                ev.copy(rawText = updated)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )

                    QuickActionBar7Buttons(
                        hasActiveLine = activeEvent != null,
                        isPlaying = isPlaying,
                        isLineTableMode = isLineTableMode,
                        onDynamicLineAction = {
                            if (activeEvent != null) deleteActiveLine() else addNewLineAtPlayhead()
                        },
                        onShiftLeadIn = {
                            if (activeEvent == null) return@QuickActionBar7Buttons
                            updateActiveDialogue(recordUndo = true) { ev ->
                                val proposedStart = if (currentPlayheadUs <= ev.endUs - TimingConstraints.MIN_DURATION_US &&
                                    kotlin.math.abs(currentPlayheadUs - ev.startUs) > 50_000L
                                ) {
                                    currentPlayheadUs
                                } else {
                                    (ev.startUs - 100_000L).coerceAtLeast(0L)
                                }
                                val nextStart = TimingConstraints.clampStartUs(proposedStart, ev.endUs)
                                ev.copy(startUs = nextStart)
                            }
                        },
                        onNudgePlayheadBackward = {
                            if (activeEvent == null) return@QuickActionBar7Buttons
                            if (sessionState.isMediaLoaded && mpvView.isMediaLoaded && (videoUri != null || audioUri != null)) {
                                mpvView.seekRelativeExact(-0.050)
                            } else {
                                seekPlayheadTo(currentPlayheadUs - 50_000L)
                            }
                        },
                        onTogglePlay = ::togglePlayback,
                        onNudgePlayheadForward = {
                            if (activeEvent == null) return@QuickActionBar7Buttons
                            if (sessionState.isMediaLoaded && mpvView.isMediaLoaded && (videoUri != null || audioUri != null)) {
                                mpvView.seekRelativeExact(0.050)
                            } else {
                                seekPlayheadTo(currentPlayheadUs + 50_000L)
                            }
                        },
                        onShiftLeadOut = {
                            if (activeEvent == null) return@QuickActionBar7Buttons
                            val effectiveDurUs = max(sessionState.mediaDurationUs, waveformData?.durationUs ?: 0L)
                            updateActiveDialogue(recordUndo = true) { ev ->
                                val proposedEnd = if (currentPlayheadUs >= ev.startUs + TimingConstraints.MIN_DURATION_US &&
                                    kotlin.math.abs(currentPlayheadUs - ev.endUs) > 50_000L
                                ) {
                                    currentPlayheadUs
                                } else {
                                    ev.endUs + 100_000L
                                }
                                val nextEnd = TimingConstraints.clampEndUs(ev.startUs, proposedEnd, effectiveDurUs)
                                ev.copy(endUs = nextEnd)
                            }
                        },
                        onToggleAudioOrLineMode = { isLineTableMode = !isLineTableMode },
                        onLoadKeyframes = onRequestLoadKeyframes,
                        onOpenScriptProperties = { showPropertiesModal = true },
                        onOpenStyleManager = { showStyleManagerModal = true },
                        onDeselectOrAddLine = {
                            if (activeEventId != null) {
                                activeEventId = null
                                ProjectSessionManager.setActiveEventId(null)
                            } else {
                                addNewLineAtPlayhead()
                            }
                        },
                    )

                    AssInspectorPanel(
                        activeEvent = activeEvent,
                        styles = styles,
                        textFieldValue = textFieldValue,
                        maxSafeCpl = maxSafeCpl,
                        onTextFieldValueChange = { newValue ->
                            handleTextFieldChangeWithEnterIntercept(
                                oldValue = textFieldValue,
                                newValue = newValue,
                                onUpdateState = { finalTfv ->
                                    textFieldValue = finalTfv
                                    if (activeEventId == null) {
                                        if (finalTfv.text.isNotEmpty()) {
                                            addNewLineAtPlayhead(
                                                initialText = finalTfv.text,
                                                initialSelection = finalTfv.selection,
                                            )
                                        }
                                    } else {
                                        updateActiveDialogue { it.copy(rawText = finalTfv.text) }
                                    }
                                },
                            )
                        },
                        onInsertEnterNewline = ::insertNewlineTokenAtCursor,
                        onCommentChanged = { checked ->
                            updateActiveDialogue(recordUndo = true) { it.copy(isComment = checked) }
                        },
                        onStyleSelected = { styleName ->
                            updateActiveDialogue(recordUndo = true) { it.copy(style = styleName) }
                        },
                        onEditStylesRequested = { showStyleManagerModal = true },
                        onActorChanged = { actor ->
                            updateActiveDialogue { it.copy(actor = actor) }
                        },
                        onEffectChanged = { effect ->
                            updateActiveDialogue { it.copy(effect = effect) }
                        },
                        onQuickTagChipClicked = { tagKey -> toggleFormatTagOnActiveDialogue(tagKey) },
                        onColorTagChipClicked = { colorTagType ->
                            activeColorSelectorTag = colorTagType
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TimelineOrLineGridSection(
                        isLineTableMode = isLineTableMode,
                        waveformData = waveformData,
                        events = events,
                        activeEventId = activeEventId,
                        currentPlayheadUs = currentPlayheadUs,
                        keyframesUs = keyframesUs,
                        mediaDurationUs = max(sessionState.mediaDurationUs, waveformData?.durationUs ?: 0L),
                        onPlayheadScrubbed = { scrubbedUs ->
                            seekPlayheadTo(scrubbedUs, syncSelectionWithPlayhead = true)
                        },
                        onActiveTimingChanged = { newStartUs, newEndUs, finished ->
                            updateActiveDialogue(recordUndo = finished, syncMpv = finished) {
                                it.copy(startUs = newStartUs, endUs = newEndUs)
                            }
                        },
                        onSelectEvent = { id ->
                            activeEventId = id
                            ProjectSessionManager.setActiveEventId(id)
                            if (id != null) {
                                events.find { it.id == id }?.let { seekPlayheadTo(it.start.microseconds) }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 140.dp),
                    )
                }
            }

            VisualTypesettingBottomBar(
                activeTool = activeTypesetTool,
                onSelectTool = { tool ->
                    activeTypesetTool = tool
                    val ev = activeEvent
                    if (tool != VisualTypesetTool.STANDARD && ev != null) {
                        if (currentPlayheadUs !in ev.startUs..ev.endUs) {
                            seekPlayheadTo(ev.startUs)
                        }
                    }
                },
            )
        }
    }

    val colorSelectorTag = activeColorSelectorTag
    if (colorSelectorTag != null) {
        val currentStyle = styles.find { it.name == activeEvent?.style } ?: styles.firstOrNull() ?: AssStyle()
        val initialHexForSlot = when (colorSelectorTag) {
            "\\2c" -> currentStyle.secondaryColorHex
            "\\3c" -> currentStyle.outlineColorHex
            "\\4c" -> currentStyle.shadowColorHex
            else -> currentStyle.primaryColorHex
        }
        ColorSelectorDialog(
            tagType = colorSelectorTag,
            initialHexColor = initialHexForSlot,
            onDismiss = { activeColorSelectorTag = null },
            onApplyColorTag = { formattedAssTag ->
                activeColorSelectorTag = null
                applyColorTagToActiveDialogue(formattedAssTag)
            },
        )
    }

    if (showStyleManagerModal) {
        StyleManagerDialog(
            initialStyles = styles,
            activeStyleName = activeEvent?.style ?: (styles.firstOrNull()?.name ?: "Default"),
            onDismiss = { showStyleManagerModal = false },
            onSaveStyles = { updatedStyles, renamedMap ->
                pushUndoSnapshot()
                styles.clear()
                styles.addAll(updatedStyles)

                val fallbackStyleName = updatedStyles.firstOrNull()?.name ?: "Default"
                for (i in events.indices) {
                    val oldStyle = events[i].style
                    val mapped = renamedMap[oldStyle] ?: oldStyle
                    val validStyle = if (updatedStyles.any { it.name == mapped }) {
                        mapped
                    } else {
                        fallbackStyleName
                    }
                    if (validStyle != oldStyle) {
                        events[i] = events[i].copy(style = validStyle)
                    }
                }
                showStyleManagerModal = false
                ProjectSessionManager.updateScript { curr ->
                    curr.copy(styles = styles.toList(), events = events.toList())
                }
                syncSubtitleWithMpv()
            },
        )
    }

    if (showPropertiesModal) {
        PropertiesDialog(
            initialScriptInfo = scriptInfo,
            onDismiss = { showPropertiesModal = false },
            onSaveProperties = { updatedInfo ->
                pushUndoSnapshot()
                scriptInfo = updatedInfo
                showPropertiesModal = false
                ProjectSessionManager.updateScript { curr ->
                    curr.copy(scriptInfo = updatedInfo)
                }
                syncSubtitleWithMpv()
            },
        )
    }

    if (showHelpModal) {
        HelpDialog(onDismiss = { showHelpModal = false })
    }

    if (externalErrorMessage != null) {
        SubtitleFormatErrorDialog(
            message = externalErrorMessage,
            onDismiss = onClearExternalError,
        )
    }
}

private fun handleTextFieldChangeWithEnterIntercept(
    oldValue: TextFieldValue,
    newValue: TextFieldValue,
    onUpdateState: (TextFieldValue) -> Unit,
) {
    if ('\n' in newValue.text) {
        val newlineIdx = newValue.text.indexOf('\n')
        val replaced = newValue.text.replace("\r\n", "\\N").replace("\n", "\\N")
        val targetCursor = (newlineIdx + 2).coerceIn(0, replaced.length)
        onUpdateState(
            TextFieldValue(
                text = replaced,
                selection = TextRange(targetCursor),
            )
        )
    } else {
        onUpdateState(newValue)
    }
}

@Composable
private fun EditorTopToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBackClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenAudio: () -> Unit,
    onSaveAss: () -> Unit,
    onHelpClick: () -> Unit,
) {
    var openMediaExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(BgMain)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopToolbarIconButton(
                symbol = "←",
                onClick = onBackClick,
            )
            TopToolbarIconButton(
                symbol = "↶",
                enabled = canUndo,
                onClick = onUndoClick,
            )
            TopToolbarIconButton(
                symbol = "↷",
                enabled = canRedo,
                onClick = onRedoClick,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TopToolbarIconButton(
                    symbol = "➕",
                    textColor = AccentCyan,
                    borderColor = Color(0xFF0284C7),
                    onClick = { openMediaExpanded = true },
                )

                DropdownMenu(
                    expanded = openMediaExpanded,
                    onDismissRequest = { openMediaExpanded = false },
                    modifier = Modifier
                        .width(220.dp)
                        .background(BgCard)
                        .border(1.dp, BorderHighlight, RoundedCornerShape(8.dp)),
                ) {
                    MediaDropdownRow(
                        title = "📄 Open Subtitle",
                        extBadge = ".ass / .srt",
                        onClick = {
                            openMediaExpanded = false
                            onOpenSubtitle()
                        },
                    )
                    MediaDropdownRow(
                        title = "🎬 Open Video",
                        extBadge = ".mp4 / .webm / .mkv",
                        onClick = {
                            openMediaExpanded = false
                            onOpenVideo()
                        },
                    )
                    MediaDropdownRow(
                        title = "🎵 Open Audio",
                        extBadge = ".mp3 / .wav / .flac",
                        onClick = {
                            openMediaExpanded = false
                            onOpenAudio()
                        },
                    )
                }
            }

            TopToolbarIconButton(
                symbol = "💾",
                textColor = AccentGreen,
                borderColor = AccentGreen,
                onClick = onSaveAss,
            )

            TopToolbarIconButton(
                symbol = "?",
                textColor = AccentAmber,
                borderColor = AccentAmber,
                onClick = onHelpClick,
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = BorderColor)
}

@Composable
private fun TopToolbarIconButton(
    symbol: String,
    enabled: Boolean = true,
    textColor: Color = TextMain,
    borderColor: Color = BorderColor,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BgSurface.copy(alpha = if (enabled) 1f else 0.45f))
            .border(1.dp, borderColor.copy(alpha = if (enabled) 1f else 0.45f), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = textColor.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MediaDropdownRow(
    title: String,
    extBadge: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(extBadge, color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun VideoMonitor16by9(
    mpvView: SubEditMpvView,
    hasVideoUri: Boolean,
    isMediaLoading: Boolean = false,
    loadingProgress: Int = 0,
    loadingStageMessage: String = "",
    visibleDialogue: AssDialogueEvent?,
    styles: List<AssStyle>,
    scriptInfo: AssScriptInfo,
    activeTypesetTool: VisualTypesetTool,
    onHeadTagsUpdated: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedStyle = remember(visibleDialogue?.style, styles) {
        val styleName = visibleDialogue?.style
        styles.find { it.name == styleName } ?: styles.firstOrNull() ?: AssStyle()
    }
    val assetFontFamily = FontRepository.rememberAssFontFamily(resolvedStyle.fontname)

    val baseTransform = remember(visibleDialogue?.rawText, resolvedStyle, scriptInfo) {
        if (visibleDialogue == null) {
            SubtitleVisualTransform()
        } else {
            DialogueTokenizer.parseVisualTransform(
                rawText = visibleDialogue.rawText,
                style = resolvedStyle,
                playResX = scriptInfo.playResX,
                playResY = scriptInfo.playResY,
            )
        }
    }

    var isGizmoDragging by remember { mutableStateOf(false) }
    var livePosX by remember { mutableFloatStateOf(baseTransform.posX) }
    var livePosY by remember { mutableFloatStateOf(baseTransform.posY) }
    var liveFrz by remember { mutableFloatStateOf(baseTransform.frz) }
    var liveFrx by remember { mutableFloatStateOf(baseTransform.frx) }
    var liveFry by remember { mutableFloatStateOf(baseTransform.fry) }
    var liveFscx by remember { mutableFloatStateOf(baseTransform.fscx) }
    var liveFscy by remember { mutableFloatStateOf(baseTransform.fscy) }

    LaunchedEffect(baseTransform, isGizmoDragging) {
        if (!isGizmoDragging) {
            livePosX = baseTransform.posX
            livePosY = baseTransform.posY
            liveFrz = baseTransform.frz
            liveFrx = baseTransform.frx
            liveFry = baseTransform.fry
            liveFscx = baseTransform.fscx
            liveFscy = baseTransform.fscy
        }
    }

    var previewBoxWidthPx by remember { mutableIntStateOf(0) }
    var previewBoxHeightPx by remember { mutableIntStateOf(0) }

    DisposableEffect(mpvView) {
        onDispose {
            mpvView.setSubtitleVisibility(true)
        }
    }

    LaunchedEffect(hasVideoUri, isMediaLoading) {
        if (!hasVideoUri || !isMediaLoading) return@LaunchedEffect
        var fileLoadedTimestampMs = 0L
        while (isActive && ProjectSessionManager.sessionState.value.isMediaLoading) {
            if (mpvView.isMediaLoaded && fileLoadedTimestampMs == 0L) {
                fileLoadedTimestampMs = System.currentTimeMillis()
            }
            val metricReady = mpvView.isFirstFrameMetricReady()
            val timeoutReached = fileLoadedTimestampMs > 0L &&
                (System.currentTimeMillis() - fileLoadedTimestampMs) >= 2_500L
            if (metricReady || timeoutReached) {
                mpvView.requestLayout()
                mpvView.invalidate()
                ProjectSessionManager.setMediaLoaded(true)
                ProjectSessionManager.setMediaLoading(
                    isLoading = false,
                    progress = 100,
                    stageMessage = "",
                )
                break
            }
            delay(100L)
        }
    }

    val latestTool by rememberUpdatedState(activeTypesetTool)
    val latestScriptInfo by rememberUpdatedState(scriptInfo)
    val latestVisibleDialogue by rememberUpdatedState(visibleDialogue)
    val latestResolvedStyle by rememberUpdatedState(resolvedStyle)
    val latestOnHeadTagsUpdated by rememberUpdatedState(onHeadTagsUpdated)

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(visibleDialogue?.id, activeTypesetTool) {
                if (activeTypesetTool == VisualTypesetTool.STANDARD || visibleDialogue == null) {
                    return@pointerInput
                }
                val touchSlop = viewConfiguration.touchSlop
                var startTouchX = 0f
                var startTouchY = 0f
                var startX = livePosX
                var startY = livePosY
                var startFrz = liveFrz
                var startFrx = liveFrx
                var startFry = liveFry
                var startScaleX = liveFscx
                var startScaleY = liveFscy
                var hasDragged = false

                detectDragGestures(
                    onDragStart = { startOffset ->
                        val curDialogue = latestVisibleDialogue ?: return@detectDragGestures
                        val curInfo = latestScriptInfo
                        val parsedCurrent = DialogueTokenizer.parseVisualTransform(
                            rawText = curDialogue.rawText,
                            style = latestResolvedStyle,
                            playResX = curInfo.playResX,
                            playResY = curInfo.playResY,
                        )
                        startTouchX = startOffset.x
                        startTouchY = startOffset.y
                        startX = parsedCurrent.posX
                        startY = parsedCurrent.posY
                        startFrz = parsedCurrent.frz
                        startFrx = parsedCurrent.frx
                        startFry = parsedCurrent.fry
                        startScaleX = parsedCurrent.fscx
                        startScaleY = parsedCurrent.fscy

                        livePosX = startX
                        livePosY = startY
                        liveFrz = startFrz
                        liveFrx = startFrx
                        liveFry = startFry
                        liveFscx = startScaleX
                        liveFscy = startScaleY
                        hasDragged = false

                        isGizmoDragging = true
                        mpvView.setSubtitleVisibility(false)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val monitorWidth = size.width.toFloat().coerceAtLeast(1f)
                        val monitorHeight = size.height.toFloat().coerceAtLeast(1f)
                        val dragDeltaX = change.position.x - startTouchX
                        val dragDeltaY = change.position.y - startTouchY
                        if (!hasDragged && hypot(dragDeltaX, dragDeltaY) > touchSlop) {
                            hasDragged = true
                        }
                        if (!hasDragged) return@detectDragGestures

                        val playResX = latestScriptInfo.playResX.toFloat().coerceAtLeast(1f)
                        val playResY = latestScriptInfo.playResY.toFloat().coerceAtLeast(1f)

                        when (latestTool) {
                            VisualTypesetTool.POS -> {
                                livePosX = (startX + dragDeltaX * (playResX / monitorWidth)).coerceIn(0f, playResX)
                                livePosY = (startY + dragDeltaY * (playResY / monitorHeight)).coerceIn(0f, playResY)
                            }
                            VisualTypesetTool.ROTATE_Z -> {
                                val centerPxX = (livePosX / playResX) * monitorWidth
                                val centerPxY = (livePosY / playResY) * monitorHeight
                                val startAngle = atan2(startTouchY - centerPxY, startTouchX - centerPxX)
                                val curAngle = atan2(change.position.y - centerPxY, change.position.x - centerPxX)
                                val degDelta = -Math.toDegrees((curAngle - startAngle).toDouble()).toFloat()
                                liveFrz = ((startFrz + degDelta + 540f) % 360f) - 180f
                            }
                            VisualTypesetTool.ROTATE_XY -> {
                                liveFrx = (startFrx + (-dragDeltaY / monitorHeight) * 180f).coerceIn(-85f, 85f)
                                liveFry = (startFry + (dragDeltaX / monitorWidth) * 180f).coerceIn(-85f, 85f)
                            }
                            VisualTypesetTool.SCALE -> {
                                liveFscx = (startScaleX + (dragDeltaX / monitorWidth) * 150f).coerceIn(10f, 500f)
                                liveFscy = (startScaleY + (-dragDeltaY / monitorHeight) * 150f).coerceIn(10f, 500f)
                            }
                            VisualTypesetTool.STANDARD -> Unit
                        }
                    },
                    onDragEnd = {
                        if (hasDragged) {
                            val updates = when (latestTool) {
                                VisualTypesetTool.POS -> mapOf(
                                    "pos" to "\\pos(${livePosX.roundToInt()},${livePosY.roundToInt()})"
                                )
                                VisualTypesetTool.ROTATE_Z -> mapOf(
                                    "frz" to "\\frz${liveFrz.roundToInt()}"
                                )
                                VisualTypesetTool.ROTATE_XY -> mapOf(
                                    "frx" to "\\frx${liveFrx.roundToInt()}",
                                    "fry" to "\\fry${liveFry.roundToInt()}",
                                )
                                VisualTypesetTool.SCALE -> mapOf(
                                    "fscx" to "\\fscx${liveFscx.roundToInt()}",
                                    "fscy" to "\\fscy${liveFscy.roundToInt()}",
                                )
                                VisualTypesetTool.STANDARD -> emptyMap()
                            }
                            if (updates.isNotEmpty()) {
                                latestOnHeadTagsUpdated(updates)
                            }
                        }
                        isGizmoDragging = false
                        mpvView.setSubtitleVisibility(true)
                    },
                    onDragCancel = {
                        isGizmoDragging = false
                        mpvView.setSubtitleVisibility(true)
                    },
                )
            }
    ) {
        val boxWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        if (hasVideoUri) {
            AndroidView<SubEditMpvView>(
                factory = { _ ->
                    mpvView.apply {
                        (parent as? ViewGroup)?.removeView(this)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    view.requestLayout()
                    view.invalidate()
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (isMediaLoading) {
                val clampedPct = loadingProgress.coerceIn(0, 100)
                val statusLabel = loadingStageMessage.ifBlank {
                    when {
                        clampedPct < 20 -> "Membuka file descriptor (fd://) & menginisialisasi dekoder..."
                        clampedPct < 85 -> "Mengekstrak puncak gelombang audio PCM ($clampedPct%)..."
                        else -> "Mendekode & mengunggah frame perdana ke permukaan GPU..."
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xC8090D16)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgCard.copy(alpha = 0.94f))
                            .border(1.dp, BorderHighlight, RoundedCornerShape(10.dp))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = AccentCyan,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(38.dp),
                            )
                            Text(
                                text = "$clampedPct%",
                                color = AccentCyan,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Text(
                            text = statusLabel,
                            color = TextMain,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0E131B))
                    .drawBehind {
                        val tilePx = 14.dp.toPx()
                        val cols = (size.width / tilePx).toInt() + 1
                        val rows = (size.height / tilePx).toInt() + 1
                        val lightChecker = Color(0xFF17202D)
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                if ((r + c) % 2 == 0) {
                                    drawRect(
                                        color = lightChecker,
                                        topLeft = Offset(c * tilePx, r * tilePx),
                                        size = Size(tilePx, tilePx),
                                    )
                                }
                            }
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x99090D16))
                        .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${scriptInfo.playResX}×${scriptInfo.playResY}",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        if (activeTypesetTool != VisualTypesetTool.STANDARD && visibleDialogue != null) {
            val pillText = when (activeTypesetTool) {
                VisualTypesetTool.POS -> "✥ POS: (${livePosX.roundToInt()}, ${livePosY.roundToInt()})"
                VisualTypesetTool.ROTATE_Z -> "🔄 ROT 2D: ${liveFrz.roundToInt()}°"
                VisualTypesetTool.ROTATE_XY -> "📐 3D: X:${liveFrx.roundToInt()}° Y:${liveFry.roundToInt()}°"
                VisualTypesetTool.SCALE -> "⤢ SCALE: ${liveFscx.roundToInt()}% × ${liveFscy.roundToInt()}%"
                VisualTypesetTool.STANDARD -> ""
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgMain.copy(alpha = 0.85f))
                    .border(1.dp, BorderHighlight, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = pillText,
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (visibleDialogue != null && !visibleDialogue.isComment && isGizmoDragging) {
            val formattedText = remember(visibleDialogue.rawText) {
                DialogueTokenizer.formatForVideoDisplay(visibleDialogue.rawText)
            }
            if (formattedText.isNotEmpty()) {
                val headTagMap = remember(visibleDialogue.rawText) {
                    DialogueTokenizer.parseHeadTagMap(visibleDialogue.rawText)
                }
                val isBold = when (headTagMap["b"]) {
                    "\\b1" -> true
                    "\\b0" -> false
                    else -> resolvedStyle.bold
                }
                val isItalic = when (headTagMap["i"]) {
                    "\\i1" -> true
                    "\\i0" -> false
                    else -> resolvedStyle.italic
                }
                val isUnder = when (headTagMap["u"]) {
                    "\\u1" -> true
                    "\\u0" -> false
                    else -> resolvedStyle.underline
                }
                val isStrike = when (headTagMap["s"]) {
                    "\\s1" -> true
                    "\\s0" -> false
                    else -> resolvedStyle.strikeout
                }

                val decorations = buildList {
                    if (isUnder) add(TextDecoration.Underline)
                    if (isStrike) add(TextDecoration.LineThrough)
                }
                val textDecoration = if (decorations.isEmpty()) {
                    TextDecoration.None
                } else {
                    TextDecoration.combine(decorations)
                }

                val scaleFactor = boxWidthPx / scriptInfo.playResX.toFloat().coerceAtLeast(1f)
                val density = LocalDensity.current
                val scaledFontSp = with(density) {
                    (resolvedStyle.fontsize * scaleFactor).coerceIn(9f, 52f).toSp()
                }

                val alignmentAn = baseTransform.alignment.coerceIn(1, 9)
                val anchorFracX = when (alignmentAn) {
                    1, 4, 7 -> 0f
                    2, 5, 8 -> 0.5f
                    else -> 1f
                }
                val anchorFracY = when (alignmentAn) {
                    7, 8, 9 -> 0f
                    4, 5, 6 -> 0.5f
                    else -> 1f
                }

                val targetScreenX = (livePosX / scriptInfo.playResX.toFloat().coerceAtLeast(1f)) * boxWidthPx
                val targetScreenY = (livePosY / scriptInfo.playResY.toFloat().coerceAtLeast(1f)) * boxHeightPx

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onSizeChanged {
                            previewBoxWidthPx = it.width
                            previewBoxHeightPx = it.height
                        }
                        .offset {
                            IntOffset(
                                x = (targetScreenX - previewBoxWidthPx * anchorFracX).roundToInt(),
                                y = (targetScreenY - previewBoxHeightPx * anchorFracY).roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(anchorFracX, anchorFracY)
                            rotationZ = -liveFrz
                            rotationX = liveFrx
                            rotationY = liveFry
                            scaleX = (liveFscx / 100f).coerceIn(0.1f, 5.0f)
                            scaleY = (liveFscy / 100f).coerceIn(0.1f, 5.0f)
                        }
                        .background(Color(0x380F172A), RoundedCornerShape(3.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = AccentCyan,
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                                ),
                            )
                            if (activeTypesetTool == VisualTypesetTool.ROTATE_Z ||
                                activeTypesetTool == VisualTypesetTool.ROTATE_XY
                            ) {
                                drawCircle(
                                    color = AccentPurple,
                                    radius = 28.dp.toPx(),
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                                    ),
                                )
                            }
                        }
                        .padding(horizontal = (6f * scaleFactor).dp, vertical = (3f * scaleFactor).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Bold
                    val fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                    val textAlign = when (alignmentAn) {
                        1, 4, 7 -> TextAlign.Left
                        3, 6, 9 -> TextAlign.Right
                        else -> TextAlign.Center
                    }

                    val textAlpha = 0.9f
                    val scaledOutlineWidth = (resolvedStyle.outline * scaleFactor * 2f).coerceAtLeast(2f)

                    Text(
                        text = formattedText,
                        style = TextStyle(
                            color = resolvedStyle.outlineComposeColor.copy(alpha = textAlpha),
                            fontSize = scaledFontSp,
                            fontFamily = assetFontFamily,
                            fontWeight = fontWeight,
                            fontStyle = fontStyle,
                            textAlign = textAlign,
                            lineHeight = scaledFontSp * 1.2f,
                            drawStyle = Stroke(width = scaledOutlineWidth),
                        ),
                    )

                    Text(
                        text = formattedText,
                        style = TextStyle(
                            color = resolvedStyle.primaryComposeColor.copy(alpha = textAlpha),
                            fontSize = scaledFontSp,
                            fontFamily = assetFontFamily,
                            fontWeight = fontWeight,
                            fontStyle = fontStyle,
                            textDecoration = textDecoration,
                            textAlign = textAlign,
                            lineHeight = scaledFontSp * 1.2f,
                            shadow = Shadow(
                                color = resolvedStyle.shadowComposeColor.copy(alpha = textAlpha),
                                offset = Offset(2f * scaleFactor, 2f * scaleFactor),
                                blurRadius = 2f * scaleFactor,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionBar7Buttons(
    hasActiveLine: Boolean,
    isPlaying: Boolean,
    isLineTableMode: Boolean,
    onDynamicLineAction: () -> Unit,
    onShiftLeadIn: () -> Unit,
    onNudgePlayheadBackward: () -> Unit,
    onTogglePlay: () -> Unit,
    onNudgePlayheadForward: () -> Unit,
    onShiftLeadOut: () -> Unit,
    onToggleAudioOrLineMode: () -> Unit,
    onLoadKeyframes: () -> Unit,
    onOpenScriptProperties: () -> Unit,
    onOpenStyleManager: () -> Unit,
    onDeselectOrAddLine: () -> Unit,
) {
    var advancedMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(BgMain)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickBarBtn(
            label = if (hasActiveLine) "🗑️" else "➕",
            bgColor = if (hasActiveLine) AccentCoral.copy(alpha = 0.2f) else Color(0xFF0284C7),
            borderColor = if (hasActiveLine) AccentCoral else AccentCyan,
            textColor = if (hasActiveLine) AccentCoral else Color.White,
            onClick = onDynamicLineAction,
            modifier = Modifier.weight(1f),
        )

        QuickBarBtn(
            label = "⇤",
            enabled = hasActiveLine,
            onClick = onShiftLeadIn,
            modifier = Modifier.weight(1f),
        )

        QuickBarBtn(
            label = "⇦",
            enabled = hasActiveLine,
            onClick = onNudgePlayheadBackward,
            modifier = Modifier.weight(1f),
        )

        QuickBarBtn(
            label = if (isPlaying) "⏸" else "▶",
            bgColor = AccentCyan,
            borderColor = AccentCyan,
            textColor = Color.Black,
            onClick = onTogglePlay,
            modifier = Modifier.weight(1f),
        )

        QuickBarBtn(
            label = "⇨",
            enabled = hasActiveLine,
            onClick = onNudgePlayheadForward,
            modifier = Modifier.weight(1f),
        )

        QuickBarBtn(
            label = "⇥",
            enabled = hasActiveLine,
            onClick = onShiftLeadOut,
            modifier = Modifier.weight(1f),
        )

        Box(modifier = Modifier.weight(1f)) {
            QuickBarBtn(
                label = "⋮",
                onClick = { advancedMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownMenu(
                expanded = advancedMenuExpanded,
                onDismissRequest = { advancedMenuExpanded = false },
                modifier = Modifier
                    .background(BgCard)
                    .border(1.dp, BorderHighlight, RoundedCornerShape(8.dp)),
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isLineTableMode) {
                                "🔊 Beralih ke Mode Audio (Waveform)"
                            } else {
                                "📋 Beralih ke Mode Line (Tabel Dialog)"
                            },
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    onClick = {
                        advancedMenuExpanded = false
                        onToggleAudioOrLineMode()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "🟣 Load Keyframes (.txt / .log)",
                            color = AccentPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    onClick = {
                        advancedMenuExpanded = false
                        onLoadKeyframes()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "📐 Script Properties...",
                            color = TextMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    onClick = {
                        advancedMenuExpanded = false
                        onOpenScriptProperties()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "🎨 Style Manager...",
                            color = TextMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    onClick = {
                        advancedMenuExpanded = false
                        onOpenStyleManager()
                    },
                )
                HorizontalDivider(color = BorderColor)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (hasActiveLine) {
                                "⬜ Unselect Line (Mode ➕)"
                            } else {
                                "➕ Tambah Line Baru di Playhead"
                            },
                            color = TextMuted,
                            fontSize = 11.sp,
                        )
                    },
                    onClick = {
                        advancedMenuExpanded = false
                        onDeselectOrAddLine()
                    },
                )
            }
        }
    }
    HorizontalDivider(thickness = 1.dp, color = BorderColor)
}

@Composable
private fun QuickBarBtn(
    label: String,
    enabled: Boolean = true,
    bgColor: Color = BgSurface,
    borderColor: Color = BorderColor,
    textColor: Color = TextMain,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(bgColor.copy(alpha = if (enabled) 1f else 0.35f))
            .border(1.dp, borderColor.copy(alpha = if (enabled) 1f else 0.35f), RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor.copy(alpha = if (enabled) 1f else 0.35f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AssInspectorPanel(
    activeEvent: AssDialogueEvent?,
    styles: List<AssStyle>,
    textFieldValue: TextFieldValue,
    maxSafeCpl: Int,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onInsertEnterNewline: () -> Unit,
    onCommentChanged: (Boolean) -> Unit,
    onStyleSelected: (String) -> Unit,
    onEditStylesRequested: () -> Unit,
    onActorChanged: (String) -> Unit,
    onEffectChanged: (String) -> Unit,
    onQuickTagChipClicked: (String) -> Unit,
    onColorTagChipClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var styleDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(BgMain)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(BgCard)
                .border(1.dp, BorderColor, RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LINE ${if (activeEvent != null) "#${activeEvent.id}" else "--"}",
                color = TextMain,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Row {
                Text("START: ", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(
                    text = activeEvent?.startAss ?: "0:00:00.00",
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row {
                Text("END: ", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(
                    text = activeEvent?.endAss ?: "0:00:00.00",
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = activeEvent?.durationSecondsLabel ?: "0.00s",
                color = AccentAmber,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                    .clickable(enabled = activeEvent != null) {
                        onCommentChanged(!(activeEvent?.isComment ?: false))
                    }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Checkbox(
                    checked = activeEvent?.isComment ?: false,
                    onCheckedChange = null,
                    modifier = Modifier.size(14.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentCyan,
                        uncheckedColor = TextMuted,
                    ),
                )
                Text(
                    text = "Comment",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }

            Box(modifier = Modifier.weight(1.25f)) {
                val currentStyleName = activeEvent?.style ?: (styles.firstOrNull()?.name ?: "Default")
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgCard)
                        .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                        .clickable { styleDropdownExpanded = true }
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentStyleName,
                        color = TextMain,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾", color = TextMuted, fontSize = 10.sp)
                }

                DropdownMenu(
                    expanded = styleDropdownExpanded,
                    onDismissRequest = { styleDropdownExpanded = false },
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .background(BgCard)
                        .border(1.dp, BorderHighlight, RoundedCornerShape(8.dp)),
                ) {
                    styles.forEach { styleItem ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = styleItem.name,
                                    color = if (styleItem.name == currentStyleName) AccentCyan else TextMain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            onClick = {
                                styleDropdownExpanded = false
                                onStyleSelected(styleItem.name)
                            },
                        )
                    }
                    HorizontalDivider(color = BorderHighlight)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "⚙️ Edit Style...",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        onClick = {
                            styleDropdownExpanded = false
                            onEditStylesRequested()
                        },
                    )
                }
            }

            CompactMetaInput(
                value = activeEvent?.actor.orEmpty(),
                onValueChange = onActorChanged,
                placeholder = "Actor",
                modifier = Modifier.weight(0.8f),
            )

            CompactMetaInput(
                value = activeEvent?.effect.orEmpty(),
                onValueChange = onEffectChanged,
                placeholder = "Effect",
                modifier = Modifier.weight(0.8f),
            )

            val charCount = remember(textFieldValue.text) {
                DialogueTokenizer.countCleanCharacters(textFieldValue.text)
            }
            val isOverSafeCpl = charCount > maxSafeCpl
            val cplColor = if (isOverSafeCpl) Color(0xFFEF4444) else Color(0xFF22C55E)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isOverSafeCpl) Color(0xFFEF4444).copy(alpha = 0.14f) else BgSurface
                    )
                    .border(
                        width = 1.dp,
                        color = if (isOverSafeCpl) Color(0xFFEF4444) else Color(0xFF22C55E).copy(alpha = 0.55f),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = charCount.toString(),
                    color = cplColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        val headTagMap = remember(textFieldValue.text) {
            DialogueTokenizer.parseHeadTagMap(textFieldValue.text)
        }
        val activeStyleObj = remember(activeEvent?.style, styles) {
            styles.find { it.name == activeEvent?.style } ?: styles.firstOrNull() ?: AssStyle()
        }
        val isBoldActive = when (headTagMap["b"]) {
            "\\b1" -> true
            "\\b0" -> false
            else -> activeStyleObj.bold
        }
        val isItalicActive = when (headTagMap["i"]) {
            "\\i1" -> true
            "\\i0" -> false
            else -> activeStyleObj.italic
        }
        val isUnderlineActive = when (headTagMap["u"]) {
            "\\u1" -> true
            "\\u0" -> false
            else -> activeStyleObj.underline
        }
        val isStrikeoutActive = when (headTagMap["s"]) {
            "\\s1" -> true
            "\\s0" -> false
            else -> activeStyleObj.strikeout
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssTagChip("B", if (isBoldActive) AccentCyan else TextMain) { onQuickTagChipClicked("b") }
            AssTagChip("I", if (isItalicActive) AccentCyan else TextMain) { onQuickTagChipClicked("i") }
            AssTagChip("U", if (isUnderlineActive) AccentCyan else TextMain) { onQuickTagChipClicked("u") }
            AssTagChip("S", if (isStrikeoutActive) AccentCyan else TextMain) { onQuickTagChipClicked("s") }
            AssTagChip("\\c", ChipPrimaryColor) { onColorTagChipClicked("\\c") }
            AssTagChip("\\2c", ChipSecondaryColor) { onColorTagChipClicked("\\2c") }
            AssTagChip("\\3c", ChipOutlineColor) { onColorTagChipClicked("\\3c") }
            AssTagChip("\\4c", ChipShadowColor) { onColorTagChipClicked("\\4c") }
        }

        OutlinedTextField(
            value = textFieldValue,
            onValueChange = onTextFieldValueChange,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            placeholder = {
                Text(
                    text = if (activeEvent == null) {
                        "Tidak ada dialog dipilih. Ketuk [➕] atau ketik untuk membuat baris baru..."
                    } else {
                        "Ketik terjemahan dialog... (Tekan Enter untuk \\N)"
                    },
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AccentCyan,
                focusedContainerColor = BgDarkest,
                unfocusedContainerColor = BgDarkest,
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = BorderHighlight,
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                    ) {
                        onInsertEnterNewline()
                        true
                    } else {
                        false
                    }
                },
        )
    }
    HorizontalDivider(thickness = 1.dp, color = BorderColor)
}

@Composable
private fun CompactMetaInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TextMain, fontSize = 10.sp),
        cursorBrush = SolidColor(AccentCyan),
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(BgCard)
            .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, color = Color(0xFF64748B), fontSize = 10.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun AssTagChip(
    label: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(BgSurface)
            .border(1.dp, BorderHighlight, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TimelineOrLineGridSection(
    isLineTableMode: Boolean,
    waveformData: WaveformData?,
    events: List<AssDialogueEvent>,
    activeEventId: Int?,
    currentPlayheadUs: Long,
    keyframesUs: List<Long>,
    mediaDurationUs: Long = 0L,
    onPlayheadScrubbed: (Long) -> Unit,
    onActiveTimingChanged: (Long, Long, Boolean) -> Unit,
    onSelectEvent: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(BgTimeline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(Color(0xFF161B24))
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = SubTime.formatAss(currentPlayheadUs),
                color = AccentCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "AUDIO: ${waveformData?.sourceLabel ?: "NONE"}",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "KF: ${keyframesUs.size}",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isLineTableMode) "MODE: LINE GRID" else "ZOOM: 70px/s",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF222A38))

        if (isLineTableMode) {
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Belum ada baris dialog. Ketuk [➕] untuk menambahkan baris baru.",
                        color = TextMuted,
                        fontSize = 11.sp,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = events,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "dialogue_row" },
                    ) { _, item ->
                        val isSelected = item.id == activeEventId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .background(
                                    when {
                                        isSelected -> AccentCyan.copy(alpha = 0.16f)
                                        item.isComment -> Color(0xFF121824)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSelectEvent(item.id) }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "#${item.id}",
                                color = if (isSelected) AccentCyan else TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(36.dp),
                            )
                            Text(
                                text = item.startAss,
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(74.dp),
                            )
                            Text(
                                text = item.endAss,
                                color = AccentCoral,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(74.dp),
                            )
                            Text(
                                text = item.rawText,
                                color = if (item.isComment) TextMuted else TextMain,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = BorderColor)
                    }
                }
            }
        } else {
            CenterPlayheadWaveformCanvas(
                waveformData = waveformData,
                events = events,
                activeEventId = activeEventId,
                currentPlayheadUs = currentPlayheadUs,
                keyframesUs = keyframesUs,
                mediaDurationUs = mediaDurationUs,
                onPlayheadScrubbed = onPlayheadScrubbed,
                onActiveTimingChanged = onActiveTimingChanged,
                onSelectEvent = onSelectEvent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VisualTypesettingBottomBar(
    activeTool: VisualTypesetTool,
    onSelectTool: (VisualTypesetTool) -> Unit,
) {
    HorizontalDivider(thickness = 1.dp, color = BorderColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(BgMain)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VisualTypesetTool.entries.forEach { tool ->
            val isSelected = activeTool == tool
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (isSelected) AccentCyan.copy(alpha = 0.18f) else BgSurface
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) AccentCyan else BorderColor,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .clickable { onSelectTool(tool) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tool.symbol,
                    color = if (isSelected) AccentCyan else TextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
