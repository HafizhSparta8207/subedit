# SubEdit Development Roadmap

This roadmap tracks the development milestones, active stabilization tasks, and planned feature expansions for SubEdit.

Current Focus: **Milestone 2 - Playback Stabilization & Event Synchronization (In Progress)**

---

## Milestone 1: Core Architecture & In-Memory Domain
**Status**: Completed

- [x] High-precision `SubTime` value class storing microsecond timestamps (`Long`) to prevent rounding errors.
- [x] Immutable ASS v4+ data structures for `[Script Info]`, `[V4+ Styles]`, and `[Events]`.
- [x] Bi-directional ASS and SRT parsing with formatting preservation.
- [x] Aegisub Keyframe Format v1 parser supporting fixed and variable frame rates.
- [x] Basic Jetpack Compose workspace layout with 16:9 monitor and bottom control docks.

---

## Milestone 2: Playback Engine & Event Synchronization
**Status**: In Progress (Active Stabilization)

- [x] Integration of native `libmpv` with `vo=gpu` and `gpu-context=android`.
- [x] Automatic fallback to FFmpeg software decoding (`hwdec=auto-safe`) for AVC High 4:2:2 `yuv422p`.
- [x] Initial embedded `libass` subtitle track loading via `sub-fonts-dir`.
- [ ] **Fix audio track playback stall**: Resolve audio renderer detachment during continuous video playback.
- [ ] **Fix mid-stream subtitle disappearance**: Ensure `ActiveSubtitleResolver` maintains track rendering across long video durations.
- [ ] **Direct dialogue editing binding**: Connect editor text input directly to the active timeline line state.
- [ ] **Timeline scrub lock**: Synchronize waveform drag scrubbing with frame-accurate video seeking without sample distortion.

---

## Milestone 3: Visual Typesetting & Interactive Gizmos
**Status**: Planned

- [ ] Smooth 60 FPS touch dragging for `\pos` coordinate tags using normalized `PlayResX` and `PlayResY` scaling.
- [ ] Interactive dual-handle controls for `\move` animations with nearest-handle touch detection.
- [ ] Intelligent tag injection preventing syntax corruption, parameter splitting, and illegal nested curly brackets `{}`.
- [ ] Visual slider gizmo for Z-axis subtitle rotation (`\frz`).
- [ ] Bounded snapshot undo buffer (`SnapshotUndoStack`) ensuring O(1) memory overhead during continuous dragging.

---

## Milestone 4: Preferences, Diagnostics & Internationalization
**Status**: Planned

- [ ] **Media engine switch**: In-app preference to toggle between `libmpv` (accurate ASS styling & 4:2:2 support) and `ExoPlayer` (battery efficient for standard MP4).
- [ ] **Hardware codec diagnostic screen**: Hardware decoder detection for AVC 4:2:2 profiles with automated fallback suggestions.
- [ ] **Safe rolling autosave**: Maximum 3 rotating recovery files (`.autosave.1.ass`) avoiding storage bloat.
- [ ] **Cache manager**: One-tap cache cleaner for downsampled PCM waveform binaries and temporary sessions.
- [ ] **Per-app language switching**: Locale manager supporting System Default, Bahasa Indonesia, and English.

---

## Milestone 5: Subtitle Grid View & Navigation
**Status**: Planned

- [ ] Full-screen scrollable Subtitle Grid mode (`LazyColumn`) with custom row filtering (Dialogue vs Comment).
- [ ] Instant row selection jumping video playhead directly to line start timestamp.
- [ ] Batch operations: time shifting, line deletion, and bulk style reassignment.

---

## Milestone 6: Distribution & APK Optimization
**Status**: Planned

- [ ] Gradle ABI split configuration (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
- [ ] Package size reduction targeting ~45 MB for standard `arm64-v8a` release builds.
- [ ] Production proguard/R8 optimization for release binaries.
- [ ] First public Alpha release on GitHub Releases.