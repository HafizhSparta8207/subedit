# Playback Stabilization and Synchronization Plan

This plan tracks technical stabilization tasks for video playback, audio waveform scrubbing, dialogue selection, and timing test coverage.

---

## 1. Bug Fix and Stabilization Checklist

### 1.1 Video Player and Media Pipeline
- [x] Configure `libmpv` with `vo=gpu` and `gpu-context=android` for native surface rendering.
- [x] Set `hwdec=auto-safe` with automatic fallback to FFmpeg `libavcodec` software decoding for AVC High 4:2:2 `yuv422p`.
- [x] Connect `time-pos` property observer to dispatch playhead updates to main thread.
- [ ] Resolve audio track detachment and muted playback during continuous video execution.
- [ ] Fix subtitle track disappearing during mid-stream playback over long video durations.

### 1.2 Dialogue Selection and Synchronization
- [x] Implement `ActiveSubtitleResolver` as a pure function adhering to half-open interval semantics `start <= time < end`.
- [x] Ensure `ActiveSubtitleResolver` excludes comment lines from active dialogue resolution.
- [ ] Connect dialogue text input directly to the selected active line state.
- [ ] Automatically seek video playhead to `event.start` whenever a dialogue line is selected in timeline or grid.

### 1.3 Audio Timeline and Waveform Scrubbing
- [x] Lock amplitude sampling to absolute time bucket indices derived from sample rate.
- [x] Streamline amplitude extraction using `Dispatchers.IO` and direct PCM downsampling to 50 buckets per second.
- [ ] Synchronize continuous waveform scrubbing drag directly with frame-accurate video seeking.
- [ ] Eliminate touch stutter and maintain 60 FPS response during timeline scrub.
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
