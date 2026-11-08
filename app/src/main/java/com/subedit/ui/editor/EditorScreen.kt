package com.subedit.ui.editor

import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.ViewGroup
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.subedit.core.domain.VisualTypesetTool
import com.subedit.core.font.FontRepository
import com.subedit.core.mpv.MpvMediaHelper
import com.subedit.core.mpv.MpvPlaybackListener
import com.subedit.core.mpv.SubEditMpvView
import com.subedit.core.session.AssScript
import com.subedit.core.session.ProjectSessionManager
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Snapshot state untuk mendukung fitur Undo (`[↶]`) dan Redo (`[↷]`).
 */
private data class EditorHistorySnapshot(
    val scriptInfo: AssScriptInfo,
    val styles: List<AssStyle>,
    val events: List<AssDialogueEvent>,
    val activeEventId: Int?,
)

/**
 * Layar Studio Editor (`EditorScreen.kt`) sesuai blueprint `editor.html`.
 */
@Composable
fun EditorScreen(
    project: AssProjectItem,
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

    // State Skrip, Styles, Dialogues, dan Keyframes
    var scriptInfo by remember(project) { mutableStateOf(project.scriptInfo) }
    val styles = remember(project) {
        mutableStateListOf<AssStyle>().apply {
            addAll(project.styles.ifEmpty { AssStyle.defaultSingleStyle() })
        }
    }
    val events = remember(project) {
        mutableStateListOf<AssDialogueEvent>().apply { addAll(project.events) }
    }
    val keyframesUs = remember(project) {
        mutableStateListOf<Long>().apply { addAll(project.keyframesUs) }
    }

    // Jika proyek memiliki dialog, pilih baris pertama; jika Zero-State (kosong), activeEventId = null
    var activeEventId by remember(project) {
        mutableStateOf<Int?>(project.events.firstOrNull()?.id)
    }

    // Inisialisasi sesi tunggal ProjectSessionManager di memori
    LaunchedEffect(project.id) {
        ProjectSessionManager.initializeSession(
            project = project,
            videoUri = videoUri,
            audioUri = audioUri,
        )
    }

    // Native libmpv Core View (vo=gpu, gpu-context=android, hwdec=auto-safe, native libass)
    val mpvView = remember(context) {
        SubEditMpvView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    // Fungsi pembantu sinkronisasi skrip .ass langsung ke core native libass
    fun syncSubtitleWithMpv() {
        if (videoUri != null && events.isNotEmpty()) {
            runCatching {
                val liveAss = MpvMediaHelper.writeLiveAssPreview(
                    context = context,
                    scriptInfo = scriptInfo,
                    styles = styles.toList(),
                    events = events.toList(),
                )
                mpvView.loadOrReloadSubtitle(liveAss.absolutePath)
            }
        }
    }

    // Undo / Redo History Stacks
    val undoStack = remember { mutableStateListOf<EditorHistorySnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorHistorySnapshot>() }

    fun pushUndoSnapshot() {
        undoStack.add(
            EditorHistorySnapshot(
                scriptInfo = scriptInfo,
                styles = styles.toList(),
                events = events.toList(),
                activeEventId = activeEventId,
            )
        )
        if (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun performUndo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.add(
            EditorHistorySnapshot(
                scriptInfo = scriptInfo,
                styles = styles.toList(),
                events = events.toList(),
                activeEventId = activeEventId,
            )
        )
        scriptInfo = prev.scriptInfo
        styles.clear()
        styles.addAll(prev.styles)
        events.clear()
        events.addAll(prev.events)
        activeEventId = prev.activeEventId
        ProjectSessionManager.updateScript {
            AssScript(
                scriptInfo = scriptInfo,
                styles = styles.toList(),
                events = events.toList(),
            )
        }
        ProjectSessionManager.setActiveEventId(activeEventId)
        syncSubtitleWithMpv()
    }

    fun performRedo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(
            EditorHistorySnapshot(
                scriptInfo = scriptInfo,
                styles = styles.toList(),
                events = events.toList(),
                activeEventId = activeEventId,
            )
        )
        scriptInfo = next.scriptInfo
        styles.clear()
        styles.addAll(next.styles)
        events.clear()
        events.addAll(next.events)
        activeEventId = next.activeEventId
        ProjectSessionManager.updateScript {
            AssScript(
                scriptInfo = scriptInfo,
                styles = styles.toList(),
                events = events.toList(),
            )
        }
        ProjectSessionManager.setActiveEventId(activeEventId)
        syncSubtitleWithMpv()
    }

    // Kalkulasi efisien dengan derivedStateOf untuk mencegah recomposition loops
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

    // State Playhead & Playback
    var currentPlayheadUs by remember(project) {
        mutableLongStateOf(project.events.firstOrNull()?.startUs ?: 0L)
    }
    var isPlaying by remember { mutableStateOf(false) }

    // Baris dialog yang terlihat di Monitor Video pada posisi currentPlayheadUs (Libass Standard)
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

    // TextFieldValue untuk kolom teks dialog. Saat activeEvent == null (unselected), dikosongkan.
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = activeEvent?.rawText.orEmpty(),
                selection = TextRange(activeEvent?.rawText?.length ?: 0),
            )
        )
    }
    LaunchedEffect(activeEventId, activeEvent?.rawText) {
        if (activeEventId == null || activeEvent == null) {
            if (textFieldValue.text.isNotEmpty()) {
                textFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
            }
        } else {
            val currentRaw = activeEvent?.rawText.orEmpty()
            if (textFieldValue.text != currentRaw) {
                textFieldValue = TextFieldValue(
                    text = currentRaw,
                    selection = TextRange(currentRaw.length),
                )
            }
        }
    }

    // Audio Waveform: null saat belum ada file audio/video (flat timeline), diekstrak saat media dimuat
    var waveformData by remember { mutableStateOf<WaveformData?>(null) }
    LaunchedEffect(audioUri, videoUri) {
        val targetMediaUri = audioUri ?: videoUri
        if (targetMediaUri == null) {
            waveformData = null
        } else {
            ThermalAudioWaveformExtractor.extractFromUri(context, targetMediaUri)
                .onSuccess { extracted -> waveformData = extracted }
        }
    }


    DisposableEffect(mpvView) {
        mpvView.setPlaybackListener(object : MpvPlaybackListener {
            override fun onTimePosChanged(timeUs: Long) {
                currentPlayheadUs = timeUs
                ProjectSessionManager.updatePlayhead(timeUs)
                val lineAtPlayhead = ActiveSubtitleResolver.findActiveDialogueUs(events, timeUs)
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
                // Duration sinkron dari mpv
            }

            override fun onMediaLoaded() {
                syncSubtitleWithMpv()
            }
        })
        onDispose {
            mpvView.setPlaybackListener(null)
            mpvView.destroy()
        }
    }

    LaunchedEffect(videoUri, audioUri) {
        val mediaUri = videoUri ?: audioUri
        if (mediaUri != null) {
            val pathOrUri = MpvMediaHelper.resolveUriToPath(context, mediaUri)
            mpvView.loadMedia(pathOrUri)
        }
    }

    LaunchedEffect(events.size, styles.size, scriptInfo) {
        syncSubtitleWithMpv()
    }

    // On-demand playback loop: 0% CPU usage ketika paused (atau saat zero-state tanpa file media)
    LaunchedEffect(isPlaying, videoUri, audioUri) {
        val hasRealMedia = (videoUri != null || audioUri != null)
        while (isActive && isPlaying && !hasRealMedia) {
            val maxEventEndUs = events.maxOfOrNull { it.endUs } ?: 0L
            val maxDurUs = max(
                waveformData?.durationUs ?: 0L,
                max(maxEventEndUs + 10L * SubTime.US_PER_SEC, 60L * SubTime.US_PER_SEC),
            )
            val nextUs = currentPlayheadUs + 33_000L
            val nextPlayheadUs = if (nextUs >= maxDurUs) {
                isPlaying = false
                0L
            } else {
                nextUs
            }
            currentPlayheadUs = nextPlayheadUs
            val lineAtPlayhead = ActiveSubtitleResolver.findActiveDialogueUs(events, nextPlayheadUs)
            activeEventId = lineAtPlayhead?.id
            ProjectSessionManager.updatePlayhead(nextPlayheadUs)
            ProjectSessionManager.setActiveEventId(lineAtPlayhead?.id)
            delay(33L)
        }
    }

    fun seekPlayheadTo(newTimeUs: Long, syncSelectionWithPlayhead: Boolean = false) {
        val maxEventEndUs = events.maxOfOrNull { it.endUs } ?: 0L
        val maxDur = max(
            max(waveformData?.durationUs ?: 0L, maxEventEndUs + 60L * SubTime.US_PER_SEC),
            3_600L * SubTime.US_PER_SEC,
        )
        val clampedUs = newTimeUs.coerceIn(0L, maxDur)
        currentPlayheadUs = clampedUs
        ProjectSessionManager.updatePlayhead(clampedUs)
        if (videoUri != null || audioUri != null) {
            mpvView.seekToSeconds(clampedUs.toDouble() / SubTime.US_PER_SEC.toDouble())
        }
        if (syncSelectionWithPlayhead) {
            val hit = ActiveSubtitleResolver.findActiveDialogueUs(events, clampedUs)
            activeEventId = hit?.id
            ProjectSessionManager.setActiveEventId(hit?.id)
        }
    }

    fun togglePlayback() {
        val hasRealMedia = (videoUri != null || audioUri != null)
        if (hasRealMedia) {
            mpvView.togglePlay()
        } else {
            isPlaying = !isPlaying
        }
    }

    // State UI Mode & Modals
    var isLineTableMode by remember { mutableStateOf(false) } // Toggle Mode Audio vs Mode Line
    var activeTypesetTool by remember { mutableStateOf(VisualTypesetTool.STANDARD) }
    var showStyleManagerModal by remember { mutableStateOf(false) }
    var showPropertiesModal by remember { mutableStateOf(false) }
    var showHelpModal by remember { mutableStateOf(false) }
    var activeColorSelectorTag by remember { mutableStateOf<String?>(null) }

    // Mutasi Baris Dialog
    fun updateActiveDialogue(
        recordUndo: Boolean = false,
        syncMpv: Boolean = true,
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
                if (sIdx != -1) list[sIdx] = updated
                curr.copy(events = list)
            }
            if (syncMpv) {
                syncSubtitleWithMpv()
            }
        }
    }

    fun addNewLineAtPlayhead(initialText: String? = null) {
        pushUndoSnapshot()
        val nextId = (events.maxOfOrNull { it.id } ?: 0) + 1
        val startUs = currentPlayheadUs
        val endUs = startUs + 2_000_000L
        val defaultStyle = styles.firstOrNull()?.name ?: "Default"
        val newLine = AssDialogueEvent(
            id = nextId,
            startUs = startUs,
            endUs = endUs,
            isComment = false,
            style = defaultStyle,
            actor = "",
            effect = "",
            rawText = initialText ?: "",
        )
        events.add(newLine)
        events.sortBy { it.startUs }
        activeEventId = nextId
        ProjectSessionManager.updateScript { curr ->
            curr.copy(events = events.toList())
        }
        ProjectSessionManager.setActiveEventId(nextId)
        textFieldValue = TextFieldValue(newLine.rawText, TextRange(newLine.rawText.length))
        syncSubtitleWithMpv()
    }

    fun deleteActiveLine() {
        val idx = activeEventIndex
        if (idx !in events.indices) return
        pushUndoSnapshot()
        events.removeAt(idx)
        val activeLine = ActiveSubtitleResolver.findActiveDialogueUs(events, currentPlayheadUs)
        activeEventId = activeLine?.id
        ProjectSessionManager.updateScript { curr ->
            curr.copy(events = events.toList())
        }
        ProjectSessionManager.setActiveEventId(activeLine?.id)
        syncSubtitleWithMpv()
    }

    fun insertTokenAtCursor(token: String) {
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BgDarkest)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // A. TOP TOOLBAR ([← Back], [↶ Undo], [↷ Redo], [➕ Open Media], [💾 Save .ASS], [? Help])
            EditorTopToolbar(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
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
                // =========================================================================
                // TATA LETAK STUDIO LANSKAP (TABLET / HORIZONTAL)
                // =========================================================================
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.58f),
                    ) {
                        // Kiri: Monitor Video 16:9 + Baris 7 Tombol Aksi Cepat
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
                                    visibleDialogue = visibleVideoDialogue,
                                    styles = styles,
                                    scriptInfo = scriptInfo,
                                    activeTypesetTool = activeTypesetTool,
                                    onHeadTagsUpdated = { tagUpdates ->
                                        updateActiveDialogue(recordUndo = true) { ev ->
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
                                        val nextStart = if (currentPlayheadUs < ev.endUs - 100_000L &&
                                            kotlin.math.abs(currentPlayheadUs - ev.startUs) > 50_000L
                                        ) {
                                            currentPlayheadUs
                                        } else {
                                            (ev.startUs - 100_000L).coerceAtLeast(0L)
                                        }
                                        ev.copy(startUs = nextStart)
                                    }
                                },
                                onNudgePlayheadBackward = {
                                    if (activeEvent == null) return@QuickActionBar7Buttons
                                    if (videoUri != null || audioUri != null) {
                                        mpvView.seekRelativeExact(-0.050)
                                    } else {
                                        seekPlayheadTo(currentPlayheadUs - 50_000L)
                                    }
                                },
                                onTogglePlay = ::togglePlayback,
                                onNudgePlayheadForward = {
                                    if (activeEvent == null) return@QuickActionBar7Buttons
                                    if (videoUri != null || audioUri != null) {
                                        mpvView.seekRelativeExact(0.050)
                                    } else {
                                        seekPlayheadTo(currentPlayheadUs + 50_000L)
                                    }
                                },
                                onShiftLeadOut = {
                                    if (activeEvent == null) return@QuickActionBar7Buttons
                                    updateActiveDialogue(recordUndo = true) { ev ->
                                        val nextEnd = if (currentPlayheadUs > ev.startUs + 100_000L &&
                                            kotlin.math.abs(currentPlayheadUs - ev.endUs) > 50_000L
                                        ) {
                                            currentPlayheadUs
                                        } else {
                                            max(ev.startUs + 200_000L, ev.endUs + 100_000L)
                                        }
                                        ev.copy(endUs = nextEnd)
                                    }
                                },
                                onToggleAudioOrLineMode = { isLineTableMode = !isLineTableMode },
                                onLoadKeyframes = onRequestLoadKeyframes,
                                onOpenScriptProperties = { showPropertiesModal = true },
                                onOpenStyleManager = { showStyleManagerModal = true },
                                onDeselectOrAddLine = {
                                    if (activeEventId != null) activeEventId = null else addNewLineAtPlayhead()
                                },
                            )
                        }

                        // Kanan: Inspector Teks & Metadata ASS
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
                                                addNewLineAtPlayhead(finalTfv.text)
                                            }
                                        } else {
                                            updateActiveDialogue { it.copy(rawText = finalTfv.text) }
                                        }
                                    },
                                )
                            },
                            onInsertEnterNewline = { insertTokenAtCursor("\\N") },
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
                            onQuickTagChipClicked = { tag -> insertTokenAtCursor(tag) },
                            onColorTagChipClicked = { colorTagType ->
                                activeColorSelectorTag = colorTagType
                            },
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxHeight(),
                        )
                    }

                    // Bawah: Audio Waveform Timeline / Line Grid
                    TimelineOrLineGridSection(
                        isLineTableMode = isLineTableMode,
                        waveformData = waveformData,
                        events = events,
                        activeEventId = activeEventId,
                        currentPlayheadUs = currentPlayheadUs,
                        keyframesUs = keyframesUs,
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
                                events.find { it.id == id }?.let { seekPlayheadTo(it.startUs) }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.42f),
                    )
                }
            } else {
                // =========================================================================
                // TATA LETAK STUDIO POTRET (SMARTPHONE / PORTRAIT)
                // =========================================================================
                Column(modifier = Modifier.weight(1f)) {
                    // B. Monitor Video 16:9
                    VideoMonitor16by9(
                        mpvView = mpvView,
                        hasVideoUri = videoUri != null,
                        visibleDialogue = visibleVideoDialogue,
                        styles = styles,
                        scriptInfo = scriptInfo,
                        activeTypesetTool = activeTypesetTool,
                        onHeadTagsUpdated = { tagUpdates ->
                            updateActiveDialogue(recordUndo = true) { ev ->
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

                    // C. Baris 7 Tombol Aksi Cepat
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
                                val nextStart = if (currentPlayheadUs < ev.endUs - 100_000L &&
                                    kotlin.math.abs(currentPlayheadUs - ev.startUs) > 50_000L
                                ) {
                                    currentPlayheadUs
                                } else {
                                    (ev.startUs - 100_000L).coerceAtLeast(0L)
                                }
                                ev.copy(startUs = nextStart)
                            }
                        },
                        onNudgePlayheadBackward = {
                            if (activeEvent == null) return@QuickActionBar7Buttons
                            if (videoUri != null || audioUri != null) {
                                mpvView.seekRelativeExact(-0.050)
                            } else {
                                seekPlayheadTo(currentPlayheadUs - 50_000L)
                            }
                        },
                        onTogglePlay = ::togglePlayback,
                        onNudgePlayheadForward = {
                            if (activeEvent == null) return@QuickActionBar7Buttons
                            if (videoUri != null || audioUri != null) {
                                mpvView.seekRelativeExact(0.050)
                            } else {
                                seekPlayheadTo(currentPlayheadUs + 50_000L)
                            }
                        },
                        onShiftLeadOut = {
                            if (activeEvent == null) return@QuickActionBar7Buttons
                            updateActiveDialogue(recordUndo = true) { ev ->
                                val nextEnd = if (currentPlayheadUs > ev.startUs + 100_000L &&
                                    kotlin.math.abs(currentPlayheadUs - ev.endUs) > 50_000L
                                ) {
                                    currentPlayheadUs
                                } else {
                                    max(ev.startUs + 200_000L, ev.endUs + 100_000L)
                                }
                                ev.copy(endUs = nextEnd)
                            }
                        },
                        onToggleAudioOrLineMode = { isLineTableMode = !isLineTableMode },
                        onLoadKeyframes = onRequestLoadKeyframes,
                        onOpenScriptProperties = { showPropertiesModal = true },
                        onOpenStyleManager = { showStyleManagerModal = true },
                        onDeselectOrAddLine = {
                            if (activeEventId != null) activeEventId = null else addNewLineAtPlayhead()
                        },
                    )

                    // D. Inspector Teks & Metadata ASS
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
                                            addNewLineAtPlayhead(finalTfv.text)
                                        }
                                    } else {
                                        updateActiveDialogue { it.copy(rawText = finalTfv.text) }
                                    }
                                },
                            )
                        },
                        onInsertEnterNewline = { insertTokenAtCursor("\\N") },
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
                        onQuickTagChipClicked = { tag -> insertTokenAtCursor(tag) },
                        onColorTagChipClicked = { colorTagType ->
                            activeColorSelectorTag = colorTagType
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // E. Audio Waveform Timeline (~140dp-155dp proporsional mengisi sisa layar)
                    TimelineOrLineGridSection(
                        isLineTableMode = isLineTableMode,
                        waveformData = waveformData,
                        events = events,
                        activeEventId = activeEventId,
                        currentPlayheadUs = currentPlayheadUs,
                        keyframesUs = keyframesUs,
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
                                events.find { it.id == id }?.let { seekPlayheadTo(it.startUs) }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 140.dp),
                    )
                }
            }

            // F. Toolbar Typesetting Visual (Di Bawah Canvas, 6 Tombol Tanpa KF)
            VisualTypesettingBottomBar(
                activeTool = activeTypesetTool,
                onSelectTool = { tool ->
                    activeTypesetTool = tool
                    // Jika memilih tool visual saat playhead berada di luar baris aktif, lompat ke awal baris aktif
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

    // =========================================================================
    // FLOATING CARD MODALS (Color Selector, Style Manager, Properties, Help, Format Error)
    // =========================================================================
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
                insertTokenAtCursor(formattedAssTag)
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

/**
 * Intersepsi tombol Enter pada keyboard virtual Android:
 * Saat pengguna menekan Enter di keyboard virtual (yang menyisipkan karakter `\n`),
 * cegah newline biasa dan otomatis sisipkan tag `\N` pada posisi kursor,
 * lalu majukan kursor 2 langkah ke depan (`cursor + 2`).
 */
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

/**
 * A. Top Toolbar: `[← Back]`, `[↶ Undo]`, `[↷ Redo]`, `[➕ Open Media]`, `[💾 Save .ASS]`, `[? Help]`.
 */
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
        // Kiri: [← Back], [↶ Undo], [↷ Redo]
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

        // Kanan: [➕ Open Media], [💾 Save .ASS], [? Help]
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

/**
 * B. Monitor Video 16:9 (`libmpv` Core) + Rendering Native Libass (100% Aegisub Parity)
 * + Latar Pola Checkerboard Abu-Abu Transparan saat belum ada media video (`!hasVideoUri`)
 * + Visual Typesetting Gizmo.
 */
@Composable
private fun VideoMonitor16by9(
    mpvView: SubEditMpvView,
    hasVideoUri: Boolean,
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

    // Live interactive transform state during drag
    var livePosX by remember(baseTransform) { mutableFloatStateOf(baseTransform.posX) }
    var livePosY by remember(baseTransform) { mutableFloatStateOf(baseTransform.posY) }
    var liveFrz by remember(baseTransform) { mutableFloatStateOf(baseTransform.frz) }
    var liveFrx by remember(baseTransform) { mutableFloatStateOf(baseTransform.frx) }
    var liveFry by remember(baseTransform) { mutableFloatStateOf(baseTransform.fry) }
    var liveFscx by remember(baseTransform) { mutableFloatStateOf(baseTransform.fscx) }
    var liveFscy by remember(baseTransform) { mutableFloatStateOf(baseTransform.fscy) }
    var clipDragBox by remember(baseTransform) { mutableStateOf(baseTransform.clipRect) }

    val latestTool by rememberUpdatedState(activeTypesetTool)
    val latestScriptInfo by rememberUpdatedState(scriptInfo)
    val latestOnHeadTagsUpdated by rememberUpdatedState(onHeadTagsUpdated)

    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(visibleDialogue?.id, activeTypesetTool) {
                if (activeTypesetTool == VisualTypesetTool.STANDARD || visibleDialogue == null) {
                    return@pointerInput
                }
                var startTouchX = 0f
                var startTouchY = 0f
                var initPosX = livePosX
                var initPosY = livePosY
                var initFrz = liveFrz
                var initFrx = liveFrx
                var initFry = liveFry
                var initFscx = liveFscx
                var initFscy = liveFscy
                var hasDragged = false

                detectDragGestures(
                    onDragStart = { startOffset ->
                        startTouchX = startOffset.x
                        startTouchY = startOffset.y
                        initPosX = livePosX
                        initPosY = livePosY
                        initFrz = liveFrz
                        initFrx = liveFrx
                        initFry = liveFry
                        initFscx = liveFscx
                        initFscy = liveFscy
                        hasDragged = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val boxW = size.width.toFloat().coerceAtLeast(1f)
                        val boxH = size.height.toFloat().coerceAtLeast(1f)
                        val dx = change.position.x - startTouchX
                        val dy = change.position.y - startTouchY
                        if (kotlin.math.abs(dx) > 1.5f || kotlin.math.abs(dy) > 1.5f) {
                            hasDragged = true
                        }
                        if (!hasDragged) return@detectDragGestures

                        val resX = latestScriptInfo.playResX.toFloat()
                        val resY = latestScriptInfo.playResY.toFloat()

                        when (latestTool) {
                            VisualTypesetTool.POS -> {
                                val deltaAssX = (dx / boxW) * resX
                                val deltaAssY = (dy / boxH) * resY
                                livePosX = (initPosX + deltaAssX).coerceIn(0f, resX)
                                livePosY = (initPosY + deltaAssY).coerceIn(0f, resY)
                            }
                            VisualTypesetTool.ROTATE_Z -> {
                                val centerPxX = (livePosX / resX) * boxW
                                val centerPxY = (livePosY / resY) * boxH
                                val startAngle = atan2(startTouchY - centerPxY, startTouchX - centerPxX)
                                val curAngle = atan2(change.position.y - centerPxY, change.position.x - centerPxX)
                                val degDelta = -Math.toDegrees((curAngle - startAngle).toDouble()).toFloat()
                                liveFrz = ((initFrz + degDelta + 540f) % 360f) - 180f
                            }
                            VisualTypesetTool.ROTATE_XY -> {
                                liveFry = (initFry + dx * 0.8f).coerceIn(-180f, 180f)
                                liveFrx = (initFrx - dy * 0.8f).coerceIn(-180f, 180f)
                            }
                            VisualTypesetTool.SCALE -> {
                                liveFscx = (initFscx + dx * 0.7f).coerceIn(20f, 350f)
                                liveFscy = (initFscy - dy * 0.7f).coerceIn(20f, 350f)
                            }
                            VisualTypesetTool.CLIP -> {
                                val x1 = ((min(startTouchX, change.position.x) / boxW) * resX)
                                    .roundToInt().coerceIn(0, resX.toInt())
                                val y1 = ((min(startTouchY, change.position.y) / boxH) * resY)
                                    .roundToInt().coerceIn(0, resY.toInt())
                                val x2 = ((max(startTouchX, change.position.x) / boxW) * resX)
                                    .roundToInt().coerceIn(0, resX.toInt())
                                val y2 = ((max(startTouchY, change.position.y) / boxH) * resY)
                                    .roundToInt().coerceIn(0, resY.toInt())
                                clipDragBox = intArrayOf(x1, y1, x2, y2)
                            }
                            VisualTypesetTool.STANDARD -> Unit
                        }
                    },
                    onDragEnd = {
                        if (!hasDragged) return@detectDragGestures
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
                            VisualTypesetTool.CLIP -> {
                                val c = clipDragBox
                                if (c != null && c.size == 4) {
                                    mapOf("clip" to "\\clip(${c[0]},${c[1]},${c[2]},${c[3]})")
                                } else {
                                    emptyMap()
                                }
                            }
                            VisualTypesetTool.STANDARD -> emptyMap()
                        }
                        if (updates.isNotEmpty()) {
                            latestOnHeadTagsUpdated(updates)
                        }
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
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Latar pola checkerboard abu-abu transparan (indikator belum ada media video)
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
                // Indikator resolusi skrip di pojok kanan atas saat belum ada video
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

        // Indikator Mode & Koordinat Live di Pojok Kiri Atas Video Monitor
        if (activeTypesetTool != VisualTypesetTool.STANDARD) {
            val pillText = when (activeTypesetTool) {
                VisualTypesetTool.POS -> "✥ POS: (${livePosX.roundToInt()}, ${livePosY.roundToInt()})"
                VisualTypesetTool.ROTATE_Z -> "🔄 ROT Z: ${liveFrz.roundToInt()}°"
                VisualTypesetTool.ROTATE_XY -> "📐 ROT X:${liveFrx.roundToInt()}° Y:${liveFry.roundToInt()}°"
                VisualTypesetTool.SCALE -> "⤢ SCALE: ${liveFscx.roundToInt()}% × ${liveFscy.roundToInt()}%"
                VisualTypesetTool.CLIP -> {
                    val c = clipDragBox
                    if (c != null && c.size == 4) {
                        "✂️ CLIP: (${c[0]},${c[1]},${c[2]},${c[3]})"
                    } else {
                        "✂️ CLIP: Geser pada subtitle"
                    }
                }
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

        // Rendering Teks Subtitle:
        // Jika ada video (hasVideoUri == true), libass di libmpv merender subtitle secara native pada frame video.
        // Compose hanya merender overlay teks ketika:
        // 1. Belum ada media video (!hasVideoUri) -> mode background checkerboard
        // 2. Mode typesetting interaktif aktif (isTypesetInteractive) -> menampilkan bounding box & drag anchor
        if (visibleDialogue != null && !visibleDialogue.isComment) {
            val isTypesetInteractive = activeTypesetTool != VisualTypesetTool.STANDARD
            if (!hasVideoUri || isTypesetInteractive) {
                val formattedText = remember(visibleDialogue.rawText) {
                    DialogueTokenizer.formatForVideoDisplay(visibleDialogue.rawText)
                }
                if (formattedText.isNotEmpty()) {
                    val rawText = visibleDialogue.rawText
                    val isBold = resolvedStyle.bold || rawText.contains("\\b1")
                    val isItalic = resolvedStyle.italic || rawText.contains("\\i1")
                    val isUnder = resolvedStyle.underline || rawText.contains("\\u1")
                    val isStrike = resolvedStyle.strikeout || rawText.contains("\\s1")

                    val decorations = buildList {
                        if (isUnder) add(TextDecoration.Underline)
                        if (isStrike) add(TextDecoration.LineThrough)
                    }
                    val textDecoration = if (decorations.isEmpty()) {
                        TextDecoration.None
                    } else {
                        TextDecoration.combine(decorations)
                    }

                    val scaleRatio = boxHeightPx / scriptInfo.playResY.toFloat().coerceAtLeast(1f)
                    val density = LocalDensity.current
                    val scaledFontSp = with(density) {
                        ((resolvedStyle.fontsize * scaleRatio * 1.45f).coerceIn(12f, 34f)).toSp()
                    }

                    val targetScreenX = (livePosX / scriptInfo.playResX.toFloat()) * boxWidthPx
                    val targetScreenY = (livePosY / scriptInfo.playResY.toFloat()) * boxHeightPx

                    Box(
                        modifier = Modifier
                            .align(
                                if (baseTransform.hasCustomPos || isTypesetInteractive) {
                                    Alignment.TopStart
                                } else {
                                    when (resolvedStyle.alignment) {
                                        1 -> Alignment.BottomStart
                                        2 -> Alignment.BottomCenter
                                        3 -> Alignment.BottomEnd
                                        4 -> Alignment.CenterStart
                                        5 -> Alignment.Center
                                        6 -> Alignment.CenterEnd
                                        7 -> Alignment.TopStart
                                        8 -> Alignment.TopCenter
                                        else -> Alignment.TopEnd
                                    }
                                }
                            )
                            .let { mod ->
                                if (baseTransform.hasCustomPos || isTypesetInteractive) {
                                    mod.offset {
                                        IntOffset(
                                            x = (targetScreenX - (boxWidthPx * 0.28f)).roundToInt()
                                                .coerceIn(4, (boxWidthPx * 0.7f).roundToInt().coerceAtLeast(8)),
                                            y = (targetScreenY - 22f).roundToInt()
                                                .coerceIn(4, (boxHeightPx - 36f).roundToInt().coerceAtLeast(8)),
                                        )
                                    }
                                } else {
                                    mod.padding(horizontal = 16.dp, vertical = 12.dp)
                                }
                            }
                            .graphicsLayer {
                                rotationZ = -liveFrz
                                rotationX = liveFrx
                                rotationY = liveFry
                                scaleX = (liveFscx / 100f).coerceIn(0.2f, 3.5f)
                                scaleY = (liveFscy / 100f).coerceIn(0.2f, 3.5f)
                            }
                            .let { mod ->
                                if (isTypesetInteractive) {
                                    val gizmoBorderColor = if (activeTypesetTool == VisualTypesetTool.CLIP) {
                                        AccentCoral
                                    } else {
                                        AccentCyan
                                    }
                                    mod
                                        .background(Color(0x380F172A), RoundedCornerShape(3.dp))
                                        .drawBehind {
                                            drawRoundRect(
                                                color = gizmoBorderColor,
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
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                } else {
                                    mod.padding(horizontal = 6.dp, vertical = 2.dp)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Bold
                        val fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                        val textAlign = when (resolvedStyle.alignment) {
                            1, 4, 7 -> TextAlign.Left
                            3, 6, 9 -> TextAlign.Right
                            else -> TextAlign.Center
                        }

                        // Jika !hasVideoUri (mode checkerboard), tampilkan teks solid penuh.
                        // Jika hasVideoUri (video sedang diputar) dan sedang typesetting drag, tampilkan semi-transparan.
                        val textAlpha = if (hasVideoUri) 0.65f else 1f

                        // Layer 1: Outline hitam pekat (Libass Standard)
                        Text(
                            text = formattedText,
                            style = TextStyle(
                                color = resolvedStyle.outlineComposeColor.copy(alpha = textAlpha),
                                fontSize = scaledFontSp,
                                fontFamily = assetFontFamily,
                                fontWeight = fontWeight,
                                fontStyle = fontStyle,
                                textAlign = textAlign,
                                lineHeight = scaledFontSp * 1.25f,
                                drawStyle = Stroke(width = (resolvedStyle.outline * 2.2f).coerceAtLeast(3f)),
                            ),
                        )

                        // Layer 2: Fill Primary + Shadow
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
                                lineHeight = scaledFontSp * 1.25f,
                                shadow = Shadow(
                                    color = resolvedStyle.shadowComposeColor.copy(alpha = textAlpha),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 2f,
                                ),
                            ),
                        )

                        // Titik Anchor Tengah (\an2 / \pos) saat mode typesetting aktif
                        if (isTypesetInteractive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentCoral.copy(alpha = 0.55f))
                                    .border(1.5.dp, AccentCoral, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * C. Baris 7 Tombol Aksi Cepat (Quick Action Bar):
 * 1. Tombol 1 (Dinamis): `🗑️` (Hapus Line) jika ada baris aktif, `➕` (Tambah Line warna biru langit) jika tidak ada.
 * 2. Tombol 2 `[⇤]`: Shift Lead-In (dinonaktifkan sementara saat tidak ada baris dipilih).
 * 3. Tombol 3 `[⇦]`: Nudge Audio/Playhead Mundur 50ms (dinonaktifkan sementara saat tidak ada baris dipilih).
 * 4. Tombol 4 `[▶ / ⏸]`: Play / Pause playback.
 * 5. Tombol 5 `[⇨]`: Nudge Audio/Playhead Maju 50ms (dinonaktifkan sementara saat tidak ada baris dipilih).
 * 6. Tombol 6 `[⇥]`: Shift Lead-Out (dinonaktifkan sementara saat tidak ada baris dipilih).
 * 7. Tombol 7 `[⋮]`: Menu Opsi Lanjutan Subtitler (termasuk `🟣 Load Keyframes`).
 */
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
        // 1. Tombol Dinamis (🗑️ Hapus Line vs ➕ Tambah Line Biru Langit)
        QuickBarBtn(
            label = if (hasActiveLine) "🗑️" else "➕",
            bgColor = if (hasActiveLine) AccentCoral.copy(alpha = 0.2f) else Color(0xFF0284C7),
            borderColor = if (hasActiveLine) AccentCoral else AccentCyan,
            textColor = if (hasActiveLine) AccentCoral else Color.White,
            onClick = onDynamicLineAction,
            modifier = Modifier.weight(1f),
        )

        // 2. [⇤] Shift Lead-In
        QuickBarBtn(
            label = "⇤",
            enabled = hasActiveLine,
            onClick = onShiftLeadIn,
            modifier = Modifier.weight(1f),
        )

        // 3. [⇦] Nudge Audio/Playhead Mundur (-50ms / 1 frame)
        QuickBarBtn(
            label = "⇦",
            enabled = hasActiveLine,
            onClick = onNudgePlayheadBackward,
            modifier = Modifier.weight(1f),
        )

        // 4. [▶ / ⏸] Play / Pause
        QuickBarBtn(
            label = if (isPlaying) "⏸" else "▶",
            bgColor = AccentCyan,
            borderColor = AccentCyan,
            textColor = Color.Black,
            onClick = onTogglePlay,
            modifier = Modifier.weight(1f),
        )

        // 5. [⇨] Nudge Audio/Playhead Maju (+50ms / 1 frame)
        QuickBarBtn(
            label = "⇨",
            enabled = hasActiveLine,
            onClick = onNudgePlayheadForward,
            modifier = Modifier.weight(1f),
        )

        // 6. [⇥] Shift Lead-Out
        QuickBarBtn(
            label = "⇥",
            enabled = hasActiveLine,
            onClick = onShiftLeadOut,
            modifier = Modifier.weight(1f),
        )

        // 7. [⋮] Menu Opsi Lanjutan Subtitler
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

/**
 * D. Inspector Teks & Metadata ASS:
 * - Strip Timecode: `#Line`, `START`, `END`, dan durasi.
 * - Strip Metadata: Checkbox `Comment`, Dropdown `Style` (diikuti separator dan `⚙️ Edit Style...`),
 *   kolom `Actor`, `Effect`, dan counter karakter murni (tanpa kata `"char"`, hijau <= 42, merah >= 43).
 * - Toolbar Chip Tag ASS Cepat (Rata Tengah): `B`, `I`, `U`, `S`, `\c`, `\2c`, `\3c`, `\4c`
 *   (tombol warna membuka Floating Modal `ColorSelectorDialog`).
 * - Kolom Teks Dialog (~110dp) dengan intersepsi tombol Enter -> `\N`.
 */
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
        // 1. Strip Timecode (#Line, START, END, Durasi)
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

        // 2. Strip Metadata (Comment, Style Dropdown + ⚙️ Edit Style..., Actor, Effect, CPL Counter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Checkbox Comment
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

            // Dropdown Style + Separator + ⚙️ Edit Style...
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

            // Actor Input
            CompactMetaInput(
                value = activeEvent?.actor.orEmpty(),
                onValueChange = onActorChanged,
                placeholder = "Actor",
                modifier = Modifier.weight(0.8f),
            )

            // Effect Input
            CompactMetaInput(
                value = activeEvent?.effect.orEmpty(),
                onValueChange = onEffectChanged,
                placeholder = "Effect",
                modifier = Modifier.weight(0.8f),
            )

            // Counter Karakter Real-Time (Tanpa kata "char", Hijau <= maxSafeCpl, Merah >= maxSafeCpl + 1)
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

        // 3. Toolbar Chip Tag ASS Cepat (Rata Tengah): B, I, U, S, \c, \2c, \3c, \4c
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssTagChip("B", TextMain) { onQuickTagChipClicked("{\\b1}") }
            AssTagChip("I", TextMain) { onQuickTagChipClicked("{\\i1}") }
            AssTagChip("U", TextMain) { onQuickTagChipClicked("{\\u1}") }
            AssTagChip("S", TextMain) { onQuickTagChipClicked("{\\s1}") }
            AssTagChip("\\c", ChipPrimaryColor) { onColorTagChipClicked("\\c") }
            AssTagChip("\\2c", ChipSecondaryColor) { onColorTagChipClicked("\\2c") }
            AssTagChip("\\3c", ChipOutlineColor) { onColorTagChipClicked("\\3c") }
            AssTagChip("\\4c", ChipShadowColor) { onColorTagChipClicked("\\4c") }
        }

        // 4. Kolom Teks Dialog (~110dp) dengan Intersepsi Tombol Enter Keyboard -> \N
        BasicTextField(
            value = textFieldValue,
            onValueChange = onTextFieldValueChange,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            cursorBrush = SolidColor(AccentCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(BgDarkest)
                .border(1.dp, BorderHighlight, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
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
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = if (activeEvent == null) {
                                "Tidak ada dialog dipilih. Ketuk [➕] atau ketik untuk membuat baris baru..."
                            } else {
                                "Ketik terjemahan dialog... (Tekan Enter untuk \\N)"
                            },
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                        )
                    }
                    innerTextField()
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

/**
 * E. Panel Audio Waveform Timeline (Continuous Center-Playhead Canvas) atau Mode Line Grid.
 */
@Composable
private fun TimelineOrLineGridSection(
    isLineTableMode: Boolean,
    waveformData: WaveformData?,
    events: List<AssDialogueEvent>,
    activeEventId: Int?,
    currentPlayheadUs: Long,
    keyframesUs: List<Long>,
    onPlayheadScrubbed: (Long) -> Unit,
    onActiveTimingChanged: (Long, Long, Boolean) -> Unit,
    onSelectEvent: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(BgTimeline),
    ) {
        // Timeline Control Strip
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
                // Mode Line (Grid daftar baris dialog ala Aegisub desktop)
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
            // Mode Audio: Continuous Center-Playhead Canvas
            CenterPlayheadWaveformCanvas(
                waveformData = waveformData,
                events = events,
                activeEventId = activeEventId,
                currentPlayheadUs = currentPlayheadUs,
                keyframesUs = keyframesUs,
                onPlayheadScrubbed = onPlayheadScrubbed,
                onActiveTimingChanged = onActiveTimingChanged,
                onSelectEvent = onSelectEvent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * F. Toolbar Typesetting Visual (Di Bawah Canvas):
 * Pointer Mode, Position `\pos`, Rotate Z `\frz`, Rotate X/Y, Scale `\fscx/\fscy`, dan Clip `\clip`
 * (6 tombol; impor Keyframes telah dipindahkan sepenuhnya ke menu `[⋮]`).
 */
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
