package com.subedit.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subedit.core.audio.WaveformData
import com.subedit.core.domain.ActiveSubtitleResolver
import com.subedit.core.domain.AssDialogueEvent
import com.subedit.core.domain.DialogueTokenizer
import com.subedit.core.domain.KeyframeEngine
import com.subedit.core.domain.SubTime
import com.subedit.core.domain.TimingConstraints
import com.subedit.core.domain.end
import com.subedit.core.domain.start
import com.subedit.ui.theme.AccentCoral
import com.subedit.ui.theme.AccentCyan
import com.subedit.ui.theme.AccentGreen
import com.subedit.ui.theme.AccentLeadInCyan
import com.subedit.ui.theme.AccentPlayheadCyan
import com.subedit.ui.theme.AccentPurple
import com.subedit.ui.theme.BgTimelineCanvas
import com.subedit.ui.theme.TextMuted
import com.subedit.ui.theme.TextSubtle
import com.subedit.ui.theme.WaveActiveRed
import com.subedit.ui.theme.WaveCenterLine
import com.subedit.ui.theme.WaveGridLine
import com.subedit.ui.theme.WaveMutedGreen
import com.subedit.ui.theme.WaveOtherDialogueGreen
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private enum class TimelineDragMode {
    NONE,
    DRAG_START,
    DRAG_END,
    SCRUB,
}

/**
 * Audio Waveform Timeline (`Continuous Center-Playhead Canvas`) sesuai `editor.html`.
 *
 * Spesifikasi:
 * - **Center Playhead**: Garis tegak cyan (`#06B6D4`) tepat di tengah layar (50% lebar canvas).
 *   Waveform dan blok dialog bergerak horizontal melintasinya saat play/scrubbing.
 * - **Zero-State / Tanpa Audio (`waveformData == null`)**:
 *   * Tidak menggambar gelombang sintetis jika `waveformData == null`.
 *   * Menggambar garis datar horizontal hening (flat timeline) beserta teks panduan redup:
 *     *"Belum ada audio. Tekan [+] untuk memuat file audio/video."*
 *   * Tetap bisa di-scrubbing secara bebas melewati penggaris waktu detik (time ruler).
 * - **Waveform Multi-Warna (Saat `waveformData != null`)**:
 *   * Merah (`#EF4444`): Gelombang suara pada rentang baris aktif yang sedang diedit.
 *   * Hijau Terang (`#84CC16`): Gelombang suara pada rentang baris dialog lain yang terdaftar.
 *   * Hijau Redup / Muted (`#44682A`): Gelombang suara pada jeda kosong non-dialog.
 *   * Garis Ungu (`#C084FC`): Penanda titik keyframe video.
 * - **Sentuhan & Drag Presisi**:
 *   * Garis batas Lead-In (Cyan) dan Lead-Out (Merah) memiliki target sentuh jari responsif.
 *   * Men-drag area di luar handle melakukan scrubbing timeline.
 *   * Mengetuk blok dialog memilih baris tersebut; mengetuk ruang kosong melepas pilihan (`deselect`).
 */
@Composable
fun CenterPlayheadWaveformCanvas(
    waveformData: WaveformData?,
    events: List<AssDialogueEvent>,
    activeEventId: Int?,
    currentPlayheadUs: Long,
    keyframesUs: List<Long>,
    mediaDurationUs: Long = 0L,
    pixelsPerSecond: Float = 70f,
    snapToKeyframes: Boolean = true,
    onPlayheadScrubbed: (newPlayheadUs: Long) -> Unit,
    onActiveTimingChanged: (newStartUs: Long, newEndUs: Long, dragFinished: Boolean) -> Unit,
    onSelectEvent: (eventId: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hitRadiusPx = remember(density) { with(density) { 20.dp.toPx() } }
    val pxPerSec = remember(density, pixelsPerSecond) {
        with(density) { pixelsPerSecond.dp.toPx().coerceAtLeast(30f) }
    }
    val textMeasurer = rememberTextMeasurer()

    val activeEvent = remember(events, activeEventId) {
        if (activeEventId == null) null else events.find { it.id == activeEventId }
    }

    val latestWaveform by rememberUpdatedState(waveformData)
    val latestEvents by rememberUpdatedState(events)
    val latestActiveEvent by rememberUpdatedState(activeEvent)
    val latestPlayheadUs by rememberUpdatedState(currentPlayheadUs)
    val latestKeyframesUs by rememberUpdatedState(keyframesUs)
    val latestMediaDurationUs by rememberUpdatedState(mediaDurationUs)
    val latestSnap by rememberUpdatedState(snapToKeyframes)
    val latestOnScrub by rememberUpdatedState(onPlayheadScrubbed)
    val latestOnTimingChanged by rememberUpdatedState(onActiveTimingChanged)
    val latestOnSelectEvent by rememberUpdatedState(onSelectEvent)

    var dragMode by remember { mutableStateOf(TimelineDragMode.NONE) }
    var dragInitialPlayheadUs by remember { mutableLongStateOf(0L) }
    var dragInitialStartUs by remember { mutableLongStateOf(0L) }
    var dragInitialEndUs by remember { mutableLongStateOf(0L) }
    var accumulatedDragPx by remember { mutableStateOf(0f) }

    val tickTextStyle = remember {
        TextStyle(
            color = Color(0xFF475569),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val emptyAudioGuideTextStyle = remember {
        TextStyle(
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
    val activePreviewTextStyle = remember {
        TextStyle(
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
    val inactivePreviewTextStyle = remember {
        TextStyle(
            color = TextMuted,
            fontSize = 11.sp,
        )
    }
    val activeMetaTextStyle = remember {
        TextStyle(
            color = AccentCyan,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val inactiveMetaTextStyle = remember {
        TextStyle(
            color = TextSubtle,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgTimelineCanvas)
            .pointerInput(pxPerSec) {
                // Gabungan deteksi ketuk (tap < touchSlop) dan geser (drag >= touchSlop) dalam satu pointer gesture
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startOffset = down.position
                    val centerX = size.width * 0.5f
                    val touchSlop = viewConfiguration.touchSlop

                    accumulatedDragPx = 0f
                    var totalDx = 0f
                    var totalDy = 0f
                    var isDragging = false

                    dragInitialPlayheadUs = latestPlayheadUs
                    val currentActive = latestActiveEvent
                    if (currentActive != null) {
                        val startX = centerX +
                            ((currentActive.startUs - latestPlayheadUs).toFloat() / SubTime.US_PER_SEC) * pxPerSec
                        val endX = centerX +
                            ((currentActive.endUs - latestPlayheadUs).toFloat() / SubTime.US_PER_SEC) * pxPerSec

                        val distStart = abs(startOffset.x - startX)
                        val distEnd = abs(startOffset.x - endX)

                        when {
                            distStart <= hitRadiusPx && distStart <= distEnd -> {
                                dragMode = TimelineDragMode.DRAG_START
                                dragInitialStartUs = currentActive.startUs
                                dragInitialEndUs = currentActive.endUs
                            }
                            distEnd <= hitRadiusPx -> {
                                dragMode = TimelineDragMode.DRAG_END
                                dragInitialStartUs = currentActive.startUs
                                dragInitialEndUs = currentActive.endUs
                            }
                            else -> {
                                dragMode = TimelineDragMode.SCRUB
                            }
                        }
                    } else {
                        dragMode = TimelineDragMode.SCRUB
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            break
                        }

                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (!isDragging && hypot(totalDx, totalDy) > touchSlop) {
                            isDragging = true
                        }

                        if (isDragging) {
                            change.consume()
                            accumulatedDragPx += delta.x
                            val deltaUs = ((accumulatedDragPx / pxPerSec) * SubTime.US_PER_SEC).toLong()
                            val effectiveDurationUs = max(latestMediaDurationUs, latestWaveform?.durationUs ?: 0L)

                            when (dragMode) {
                                TimelineDragMode.DRAG_START -> {
                                    val rawStart = (dragInitialStartUs + deltaUs).coerceAtLeast(0L)
                                    val snappedStart = if (latestSnap) {
                                        KeyframeEngine.snapToKeyframe(rawStart, latestKeyframesUs, 80_000L)
                                    } else {
                                        rawStart
                                    }
                                    val clampedStart = TimingConstraints.clampStartUs(
                                        proposedStartUs = snappedStart,
                                        currentEndUs = dragInitialEndUs,
                                    )
                                    latestOnTimingChanged(clampedStart, dragInitialEndUs, false)
                                }
                                TimelineDragMode.DRAG_END -> {
                                    val rawEnd = (dragInitialEndUs + deltaUs).coerceAtLeast(TimingConstraints.MIN_DURATION_US)
                                    val snappedEnd = if (latestSnap) {
                                        KeyframeEngine.snapToKeyframe(rawEnd, latestKeyframesUs, 80_000L)
                                    } else {
                                        rawEnd
                                    }
                                    val clampedEnd = TimingConstraints.clampEndUs(
                                        currentStartUs = dragInitialStartUs,
                                        proposedEndUs = snappedEnd,
                                        durationUs = effectiveDurationUs,
                                    )
                                    latestOnTimingChanged(dragInitialStartUs, clampedEnd, false)
                                }
                                TimelineDragMode.SCRUB -> {
                                    val maxEventEndUs = latestEvents.maxOfOrNull { it.endUs } ?: 0L
                                    val maxDur = if (effectiveDurationUs > 0L) {
                                        effectiveDurationUs
                                    } else {
                                        max(maxEventEndUs + 60L * SubTime.US_PER_SEC, 3_600L * SubTime.US_PER_SEC)
                                    }
                                    val scrubbedUs = (dragInitialPlayheadUs - deltaUs).coerceIn(0L, maxDur)
                                    latestOnScrub(scrubbedUs)
                                }
                                TimelineDragMode.NONE -> Unit
                            }
                        }
                    }

                    if (!isDragging) {
                        // Ketukan di bawah touchSlop: cari segmen dialog pada waktu tersebut via ActiveSubtitleResolver
                        val deltaSec = (startOffset.x - centerX) / pxPerSec
                        val tappedTimeUs = (latestPlayheadUs + (deltaSec * SubTime.US_PER_SEC).toLong())
                            .coerceAtLeast(0L)
                        val tappedTimeMs = tappedTimeUs / 1_000L

                        val hitEvent = ActiveSubtitleResolver.findActiveDialogue(latestEvents, tappedTimeMs)
                            ?: ActiveSubtitleResolver.findActiveDialogueUs(latestEvents, tappedTimeUs)
                        if (hitEvent != null) {
                            latestOnSelectEvent(hitEvent.id)
                        } else {
                            latestOnSelectEvent(null)
                        }
                    } else if (dragMode == TimelineDragMode.DRAG_START || dragMode == TimelineDragMode.DRAG_END) {
                        latestActiveEvent?.let { ev ->
                            latestOnTimingChanged(ev.startUs, ev.endUs, true)
                        }
                    }
                    dragMode = TimelineDragMode.NONE
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width * 0.5f
            val midY = size.height * 0.5f
            val arrowPath = Path().apply {
                moveTo(centerX - 6.dp.toPx(), 0f)
                lineTo(centerX + 6.dp.toPx(), 0f)
                lineTo(centerX, 8.dp.toPx())
                close()
            }
            val barStepPx = 2.8.dp.toPx().coerceAtLeast(2.5f)
            val barWidthPx = 2.dp.toPx().coerceAtLeast(1.8f)
            val maxWaveHalfHeight = max(24f, (size.height - 36.dp.toPx()) * 0.48f)

            drawAegisubContinuousTimeline(
                waveformData = waveformData,
                events = events,
                activeEvent = activeEvent,
                playheadUs = currentPlayheadUs,
                keyframesUs = keyframesUs,
                centerX = centerX,
                midY = midY,
                pxPerSec = pxPerSec,
                barStepPx = barStepPx,
                barWidthPx = barWidthPx,
                maxWaveHalfHeight = maxWaveHalfHeight,
                arrowPath = arrowPath,
                textMeasurer = textMeasurer,
                tickTextStyle = tickTextStyle,
                emptyAudioGuideTextStyle = emptyAudioGuideTextStyle,
                activePreviewTextStyle = activePreviewTextStyle,
                inactivePreviewTextStyle = inactivePreviewTextStyle,
                activeMetaTextStyle = activeMetaTextStyle,
                inactiveMetaTextStyle = inactiveMetaTextStyle,
            )
        }
    }
}

private fun DrawScope.drawAegisubContinuousTimeline(
    waveformData: WaveformData?,
    events: List<AssDialogueEvent>,
    activeEvent: AssDialogueEvent?,
    playheadUs: Long,
    keyframesUs: List<Long>,
    centerX: Float,
    midY: Float,
    pxPerSec: Float,
    barStepPx: Float,
    barWidthPx: Float,
    maxWaveHalfHeight: Float,
    arrowPath: Path,
    textMeasurer: TextMeasurer,
    tickTextStyle: TextStyle,
    emptyAudioGuideTextStyle: TextStyle,
    activePreviewTextStyle: TextStyle,
    inactivePreviewTextStyle: TextStyle,
    activeMetaTextStyle: TextStyle,
    inactiveMetaTextStyle: TextStyle,
) {
    val w = size.width
    val h = size.height
    if (w <= 1f || h <= 1f) return

    val playheadSec = playheadUs.toFloat() / SubTime.US_PER_SEC.toFloat()
    val halfSpanSec = centerX / pxPerSec
    val startVisibleSec = max(0f, playheadSec - halfSpanSec)
    val endVisibleSec = playheadSec + halfSpanSec

    // Viewport Culling: Filter hanya segmen subtitle yang masuk dalam rentang waktu layar saat ini
    val visibleStartUs = (max(0f, startVisibleSec - 0.5f) * SubTime.US_PER_SEC).toLong()
    val visibleEndUs = ((endVisibleSec + 0.5f) * SubTime.US_PER_SEC).toLong()
    val visibleStartMs = visibleStartUs / 1_000L
    val visibleEndMs = visibleEndUs / 1_000L
    val visibleEvents = events.filter { event ->
        val eventStartMs = event.startUs / 1_000L
        val eventEndMs = event.endUs / 1_000L
        eventEndMs >= visibleStartMs && eventStartMs <= visibleEndMs
    }

    // 1. Grid Vertikal per Detik (Time Ruler) & Garis Tengah Horizontal
    val firstTickSec = floor(startVisibleSec).toInt()
    val lastTickSec = (endVisibleSec + 1f).toInt()
    for (sec in firstTickSec..lastTickSec) {
        val x = centerX + (sec - playheadSec) * pxPerSec
        if (x in 0f..w) {
            drawLine(
                color = WaveGridLine,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f,
            )
            val m = sec / 60
            val s = sec % 60
            val mPad = if (m < 10) "0$m" else "$m"
            val sPad = if (s < 10) "0$s" else "$s"
            val label = "$mPad:$sPad"
            if (x + 36f < w) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = tickTextStyle,
                    topLeft = Offset(x + 3f, h - 14.dp.toPx()),
                )
            }
        }
    }

    // Garis tengah horizontal (flat timeline)
    drawLine(
        color = if (waveformData == null) Color(0xFF334155) else WaveCenterLine,
        start = Offset(0f, midY),
        end = Offset(w, midY),
        strokeWidth = if (waveformData == null) 1.5.dp.toPx() else 1f,
    )

    // 2. Waveform Multi-Warna Kontinu (HANYA jika waveformData != null)
    // Kunci posisi sampling amplitudo audio pada indeks bucket waktu absolut (timestampSec * 50)
    // agar bentuk gelombang tetap statis dan tidak bergeser saat kanvas digulir.
    if (waveformData != null && waveformData.peaks.isNotEmpty()) {
        val sampleRate = 50 // 50 bucket/detik (timestampSec * 50)
        val bucketStepPx = pxPerSec / sampleRate.toFloat()
        val stride = max(1, (1.8f / bucketStepPx).toInt())
        val effectiveBarWidth = max(1.5f, (bucketStepPx * stride * 0.78f).coerceAtLeast(barWidthPx))

        val minVisibleSec = max(0f, playheadSec - (centerX + 16f) / pxPerSec)
        val maxVisibleSec = playheadSec + (w - centerX + 16f) / pxPerSec

        val startBucket = ((minVisibleSec * 50f).toInt() - 1).coerceAtLeast(0)
        val endBucket = ((maxVisibleSec * 50f).toInt() + 1).coerceAtMost(waveformData.peaks.size - 1)

        var bucketIdx = startBucket
        while (bucketIdx <= endBucket) {
            val timestampSec = bucketIdx.toFloat() / 50f
            val lockedBucketIndex = (timestampSec * 50f).toInt().coerceIn(0, waveformData.peaks.size - 1)
            val bucketTimeUs = (lockedBucketIndex.toLong() * SubTime.US_PER_SEC) / sampleRate
            val x = centerX + (timestampSec - playheadSec) * pxPerSec

            if (x in -effectiveBarWidth..(w + effectiveBarWidth)) {
                val amp = waveformData.peaks[lockedBucketIndex]
                val barHalfH = max(2f, amp * maxWaveHalfHeight)
                val barColor = when {
                    activeEvent != null && bucketTimeUs in activeEvent.startUs..activeEvent.endUs -> WaveActiveRed
                    visibleEvents.any { it.id != activeEvent?.id && bucketTimeUs in it.startUs..it.endUs } -> WaveOtherDialogueGreen
                    else -> WaveMutedGreen
                }

                drawRect(
                    color = barColor,
                    topLeft = Offset(x, midY - barHalfH),
                    size = Size(effectiveBarWidth, barHalfH * 2f),
                )
            }
            bucketIdx += stride
        }
    } else {
        // Tanpa audio: Gambar overlay tipis blok dialog di sekitar garis datar & teks panduan redup (hanya visibleEvents)
        for (ev in visibleEvents) {
            val startSec = ev.startUs.toFloat() / SubTime.US_PER_SEC.toFloat()
            val endSec = ev.endUs.toFloat() / SubTime.US_PER_SEC.toFloat()
            val startX = (centerX + (startSec - playheadSec) * pxPerSec).coerceIn(0f, w)
            val endX = (centerX + (endSec - playheadSec) * pxPerSec).coerceIn(0f, w)
            if (endX > startX) {
                val isActive = ev.id == activeEvent?.id
                drawRect(
                    color = if (isActive) {
                        WaveActiveRed.copy(alpha = 0.14f)
                    } else {
                        WaveOtherDialogueGreen.copy(alpha = 0.10f)
                    },
                    topLeft = Offset(startX, 0f),
                    size = Size(endX - startX, h),
                )
            }
        }

        val guideMessage = "Belum ada audio. Tekan [+] untuk memuat file audio/video."
        val measured = textMeasurer.measure(
            text = guideMessage,
            style = emptyAudioGuideTextStyle,
        )
        val textX = ((w - measured.size.width) * 0.5f).coerceAtLeast(8f)
        val textY = (midY + 8.dp.toPx()).coerceAtMost(h - 32.dp.toPx())
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(textX, textY),
        )
    }

    // 3. Garis Vertikal Keyframe Ungu (#C084FC)
    if (keyframesUs.isNotEmpty()) {
        for (kfUs in keyframesUs) {
            if (kfUs < visibleStartUs) continue
            if (kfUs > visibleEndUs) break
            val kfSec = kfUs.toFloat() / SubTime.US_PER_SEC.toFloat()
            val kfX = centerX + (kfSec - playheadSec) * pxPerSec
            drawLine(
                color = AccentPurple,
                start = Offset(kfX, 0f),
                end = Offset(kfX, h),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawRect(
                color = AccentPurple,
                topLeft = Offset(kfX - 2.5.dp.toPx(), 0f),
                size = Size(5.dp.toPx(), 5.dp.toPx()),
            )
        }
    }

    // 4. Overlay Blok Dialog (Viewport Culled), Teks Preview dengan clipRect, Lead-In (Cyan) & Lead-Out (Merah)
    val maxCanvasWidthConstraint = w.toInt().coerceIn(1, 8192)
    for (ev in visibleEvents) {
        val startSec = ev.startUs.toFloat() / SubTime.US_PER_SEC.toFloat()
        val endSec = ev.endUs.toFloat() / SubTime.US_PER_SEC.toFloat()
        val startX = centerX + (startSec - playheadSec) * pxPerSec
        val endX = centerX + (endSec - playheadSec) * pxPerSec
        val isActive = ev.id == activeEvent?.id

        if (endX > 0f && startX < w) {
            val clipLeft = startX.coerceAtLeast(0f)
            val clipRight = endX.coerceAtMost(w)
            val textLeftX = max(clipLeft + 4f, startX + 6.dp.toPx())
            val availableWidthPx = (clipRight - textLeftX - 4.dp.toPx()).coerceAtMost(w)
            val displayLabel = DialogueTokenizer.formatForWaveformLabel(ev.rawText)
            val prefix = if (ev.isComment) "[C] " else ""
            val fullLabel = prefix + displayLabel

            if (clipRight > clipLeft) {
                clipRect(
                    left = clipLeft,
                    top = 0f,
                    right = clipRight,
                    bottom = h,
                ) {
                    if (availableWidthPx > 10f && fullLabel.isNotEmpty()) {
                        val safeConstraintWidth = availableWidthPx.toInt().coerceIn(1, maxCanvasWidthConstraint)
                        val measuredLabel = textMeasurer.measure(
                            text = fullLabel,
                            style = if (isActive) activePreviewTextStyle else inactivePreviewTextStyle,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            maxLines = 1,
                            constraints = Constraints(maxWidth = safeConstraintWidth),
                        )
                        drawText(
                            textLayoutResult = measuredLabel,
                            topLeft = Offset(textLeftX, 4.dp.toPx()),
                        )
                    }

                    // Label Nomor Line & Durasi (#31 (2.00s)) ikut terbungkus clipRect segmen
                    if (clipRight - textLeftX > 44f) {
                        val safeMetaWidth = (clipRight - textLeftX).toInt().coerceIn(1, maxCanvasWidthConstraint)
                        val measuredMeta = textMeasurer.measure(
                            text = "#${ev.id} (${ev.durationSecondsLabel})",
                            style = if (isActive) activeMetaTextStyle else inactiveMetaTextStyle,
                            overflow = TextOverflow.Clip,
                            softWrap = false,
                            maxLines = 1,
                            constraints = Constraints(maxWidth = safeMetaWidth),
                        )
                        drawText(
                            textLayoutResult = measuredMeta,
                            topLeft = Offset(textLeftX, h - 26.dp.toPx()),
                        )
                    }
                }
            }
        }

        // Garis Pembatas Lead-In (Cyan #22D3EE)
        if (startX in -16f..(w + 16f)) {
            drawLine(
                color = AccentLeadInCyan,
                start = Offset(startX, 0f),
                end = Offset(startX, h),
                strokeWidth = if (isActive) 2.5.dp.toPx() else 1.5.dp.toPx(),
            )
            if (isActive) {
                val handleW = 8.dp.toPx()
                val handleH = 13.dp.toPx()
                drawRect(
                    color = AccentLeadInCyan,
                    topLeft = Offset(startX - handleW * 0.5f, 0f),
                    size = Size(handleW, handleH),
                )
                drawRect(
                    color = AccentLeadInCyan,
                    topLeft = Offset(startX - handleW * 0.5f, h - handleH - 6.dp.toPx()),
                    size = Size(handleW, handleH),
                )
            }
        }

        // Garis Pembatas Lead-Out (Merah #F43F5E untuk aktif, Hijau #22C55E untuk non-aktif)
        if (endX in -16f..(w + 16f)) {
            val endColor = if (isActive) AccentCoral else AccentGreen
            drawLine(
                color = endColor,
                start = Offset(endX, 0f),
                end = Offset(endX, h),
                strokeWidth = if (isActive) 2.5.dp.toPx() else 1.5.dp.toPx(),
            )
            if (isActive) {
                val handleW = 8.dp.toPx()
                val handleH = 13.dp.toPx()
                drawRect(
                    color = AccentCoral,
                    topLeft = Offset(endX - handleW * 0.5f, 0f),
                    size = Size(handleW, handleH),
                )
                drawRect(
                    color = AccentCoral,
                    topLeft = Offset(endX - handleW * 0.5f, h - handleH - 6.dp.toPx()),
                    size = Size(handleW, handleH),
                )
            }
        }
    }

    // 5. Center Playhead Line & Arrow (Tegak Cyan di 50% Lebar Canvas)
    drawLine(
        color = AccentPlayheadCyan,
        start = Offset(centerX, 0f),
        end = Offset(centerX, h),
        strokeWidth = 2.dp.toPx(),
    )
    drawPath(
        path = arrowPath,
        color = AccentPlayheadCyan,
    )
}
