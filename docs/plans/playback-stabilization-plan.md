# Playback Stabilization and Synchronization Plan

This plan tracks technical stabilization tasks for video playback, audio waveform scrubbing, dialogue selection, and timing test coverage.

---

## 1. Bug Fix and Stabilization Checklist

### 1.1 Video Player and Media Pipeline
- [x] Configure `libmpv` with `vo=gpu`, `gpu-context=android`, and `keep-open=always` for native surface rendering (without `force-window=immediate` to prevent premature EGL initialization when opening an empty project without video).
- [x] Detect AVC High 4:2:2 (`yuv422p`) streams via `ChromaProxyConverter` and auto-convert chroma to `yuv420p` using hardware `MediaCodec` (cached in `cacheDir/chroma_proxy/` by file size and modification timestamp, preserving 100% original resolution, exact 1:1 PTS/frame count, 1-second keyframe interval, and raw audio stream copy), paired with `hwdec=auto-safe` FFmpeg `libavcodec` software decoding fallback.
- [x] Set explicit `libmpv` demuxer cache limits (`demuxer-max-bytes=32MiB`, `demuxer-max-back-bytes=16MiB`) and dismiss the `isMediaLoading` overlay immediately upon first rendered video frame (`vo-configured` / `timePos > 0` / `estimated-vf-fps > 0` / `playback-restart` or 2.5s watchdog after `file-loaded` plus initial `seek 0 absolute+exact`) without blocking on background PCM waveform extraction.
- [x] Guard idle `libmpv` state (`isMediaLoaded == false`) so native `seek`, `play`, `pause`, `sub-remove`, `sub-add`, and `sub-reload` commands are never invoked when no media is loaded (virtual playhead mode on checkerboard monitor).
- [x] Connect `time-pos` property observer and 16ms playback polling (`pollTimePosSeconds`) to dispatch 60 FPS playhead updates to main thread.
- [x] Resolve audio track detachment and muted playback during continuous video execution.
- [x] Fix subtitle track disappearing during mid-stream playback over long video durations.

### 1.2 Dialogue Selection, Import, and Synchronization
- [x] Normalize imported `.ass` files safely on `Dispatchers.IO` (strip `\uFEFF` BOM and `\r` control characters while preserving 100% of original `[V4+ Styles]` names such as `DefaultCR` across all 23 columns, `[Script Info]`, `[Aegisub Project Garbage]`, `[Aegisub Extradata]`, `[Fonts]`, `[Graphics]`, and `Comment:` lines for lossless Aegisub Desktop round-trip export) and reset `selectedEventId = null` plus undo history on import.
- [x] Auto-select new dialogue lines on creation (`+`) and auto-create a line on the first keystroke when typing with `selectedEventId == null`.
- [x] Synchronize line deletion (including deleting the last line) by flushing the empty `[Events]` table to disk and calling `sub-reload` on `libmpv`, while resetting visual gizmos to `STANDARD` pointer mode.
- [x] Implement `ActiveSubtitleResolver` as a pure function adhering to half-open interval semantics `start <= time < end`.
- [x] Ensure `ActiveSubtitleResolver` excludes comment lines from active dialogue resolution.
- [x] Connect dialogue text input directly to the selected active line state.
- [x] Automatically seek video playhead to `event.start` whenever a dialogue line is selected in timeline or grid.

### 1.3 Audio Timeline and Waveform Scrubbing
- [x] Lock playhead at center (`canvasWidth / 2`) and calculate each amplitude bar position from its absolute bucket index relative to `playheadSec`.
- [x] Apply viewport culling (`eventEndMs >= visibleStartMs && eventStartMs <= visibleEndMs`) and `clipRect` text width clamping on `CenterPlayheadWaveformCanvas` so off-screen or full-episode dialogue blocks never exceed Compose `TextMeasurer` constraints.
- [x] Streamline amplitude extraction using `Dispatchers.IO` and direct PCM downsampling to 50 buckets per second with live percentage progress reporting.
- [x] Unify tap (`< touchSlop` for instant dialogue selection & seek to `event.start`) and drag (`>= touchSlop` for Lead-In/Lead-Out handle resizing with `TimingConstraints` or `absolute+exact` scrubbing) in a single pointer gesture on `CenterPlayheadWaveformCanvas`.
- [x] Eliminate touch stutter and maintain 60 FPS response during timeline playback and scrub.

### 1.4 Single-Block Tag Upsert & 5 Visual Typesetting Tools
- [x] Implement centralized head-tag upsert parser (`DialogueTokenizer.parseHeadTagMap` & `injectOrUpdateHeadTags`) with strict tag prefix separation (`\b` vs `\bord`/`\blur`, `\fs` vs `\fscx`/`\fscy`, `\frz`/`\frx`/`\fry` vs `\fn`, `\p` vs `\pos`) merging consecutive leading `{...}` blocks into one `LinkedHashMap<String, String>` while preserving manual tags (`\q2`, `\an1`–`\an9`, `\bord`, `\shad`, `\blur`, `\fad`) and mid-sentence inline tags.
- [x] Eliminate cursor-based tag insertion for `B`, `I`, `U`, `S` toggles and `\c`–`\4c` color overrides to prevent splitting existing tags.
- [x] Enforce `Modifier.clipToBounds()` on `VideoMonitor16by9` and scale preview typography and gizmo bounds via `monitorWidth / playResX` and `\an` 1–9 alignment anchors.
- [x] Provide 5 dedicated visual typesetting tools in the bottom bar: **Select / Pointer**, **Position (`\pos`)**, **Rotate 2D (`\frz`)**, **Perspective 3D (`\frx` & `\fry`)**, and **Scale (`\fscx` & `\fscy`)**.
- [x] Implement repeatable three-stage 60 FPS gizmo drag lifecycle (`onDragStart` reading existing tags from `selectedEvent.rawText` and hiding `libass` via `sub-visibility=no`, `onDrag` mutating local UI state in memory after exceeding `touchSlop`, `onDragEnd` committing once to `ProjectSessionManager`, flushing the temporary `.ass` file, calling `sub-reload`, and restoring `sub-visibility=yes`).
---

## 2. Unit Test Plan

### 2.1 ActiveSubtitleResolver Verification
- **Exact Start Boundary**:
  - Test condition: Playhead timestamp equals `event.start.millis`.
  - Expected result: Event is identified as active.
- **Exact End Boundary**:
  - Test condition: Playhead timestamp equals `event.end.millis`.
  - Expected result: Event is not identified as active because end boundary is exclusive.
- **Mid-Interval Position**:
  - Test condition: Playhead timestamp is strictly between `start` and `end`.
  - Expected result: Event is identified as active.
- **Outside Interval**:
  - Test condition: Playhead timestamp is before start or after end.
  - Expected result: Returns null or next active line.
- **Comment Filtering**:
  - Test condition: Event has `isComment = true` overlapping a non-comment event.
  - Expected result: Resolver ignores the comment event and selects the valid dialogue line.
- **Microsecond Precision**:
  - Test condition: Microsecond timestamps evaluated via `findActiveDialogueUs` and `isEventActiveUs`.
  - Expected result: Precise resolution without integer truncation errors.

### 2.2 Event Duration and Boundary Safeguards
- **Normal Duration**:
  - Verification of `durationUs = endUs - startUs` and duration formatting.
- **Inverted Timestamps**:
  - Test condition: Event configured with `endUs < startUs`.
  - Expected result: `durationUs` is clamped to zero to prevent negative calculation errors in audio timeline rendering.

### 2.3 ProjectSessionManager State Synchronization
- **Session Initialization**:
  - Verification that script metadata, styles, and events initialize correctly from project models.
- **Playhead and Selection Sync**:
  - Verification that `updatePlayhead` updates position and resolves active dialogue ID.
- **Content Mutation**:
  - Verification that `updateScript` applies transformations and increments the content revision counter.
