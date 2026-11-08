# SubEdit Development Roadmap

This document outlines the development trajectory and milestone delivery status for the SubEdit native Android subtitle editor.

For technical differences between SubEdit, Aegisub Desktop, and standard mobile tools, see [Feature and Architecture Comparison](docs/COMPARISON.md).

---

## Milestone 1: Core Engine
**Status**: Completed

- [x] High-precision `SubTime` value class storing time in microseconds (`Long`) to prevent floating-point rounding errors.
- [x] Full `[Script Info]`, `[V4+ Styles]`, and `[Events]` ASS domain data models with immutability guarantees.
- [x] Lossless tag preservation in editor textarea ensuring manual curly bracket overrides are never stripped.
- [x] Bi-directional Aegisub and SubRip parser supporting ASS and SRT import and export.
- [x] Crunchyroll and Aegisub keyframe parser supporting format v1 with variable and fixed frame rates.

---

## Milestone 2: Media Core
**Status**: Completed

- [x] Native `libmpv` Android integration using `mpv-android-lib` with GPU rendering context (`vo=gpu`, `gpu-context=android`).
- [x] Hardware decoding with automatic fallback (`hwdec=auto-safe`) to FFmpeg `libavcodec` software decoding for AVC High 4:2:2 `yuv422p` video profiles.
- [x] Native `libass` subtitle renderer embedded inside `libmpv` with local Crunchyroll font bundling via `sub-fonts-dir`.
- [x] Precise seeking via `seek <sec> absolute+exact` and relative nudging via `seek <delta> relative+exact`.
- [x] Decoupling of subtitle track reloads from playhead motion to prevent disk write thrashing during playback.

---

## Milestone 3: Session Synchronization
**Status**: Completed

- [x] Centralized `ProjectSessionManager` maintaining single in-memory state for script metadata, styles, and dialogues.
- [x] Pure function `ActiveSubtitleResolver` implementing half-open interval matching `start <= time < end`.
- [x] Automatic playhead seeking to line start timestamp when a dialogue event is selected on timeline or table.
- [x] Real-time Compose UI active line updates driven by `time-pos` property observer.
- [x] Comprehensive unit test coverage for boundary conditions, comment filtering, and microsecond timestamps.

---

## Milestone 4: Waveform Timing
**Status**: Completed

- [x] Continuous center-playhead audio canvas with distinct active dialogue, other dialogue, and muted gap colors.
- [x] Power-efficient PCM downsampling to 50 buckets per second using `Dispatchers.IO`.
- [x] Locked time-bucket sampling index preventing waveform distortion and shimmering during horizontal scrub.
- [x] Responsive touch handles for Lead-In and Lead-Out timing boundaries with keyframe snapping.
- [x] Zero-state flat timeline rendering with placeholder prompt when media files are not yet loaded.

---

## Milestone 5: Release Packaging
**Status**: Completed

- [x] Native library packaging across all target ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- [x] Bundled standard Crunchyroll typography fonts extracted to application storage on initialization.
- [x] Verified unit test suite execution with zero failures via `testDebugUnitTest`.
- [x] Verified debug application package assembly via `assembleDebug`.

---

## Future Enhancements
- [ ] Direct cloud storage provider integration through Storage Access Framework.
- [ ] Waveform visual color customizer in user settings.
- [ ] Audio spectrum analyzer mode alongside amplitude timeline.
