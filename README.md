# SubEdit

SubEdit is a native Android subtitle editor designed for advanced SubStation Alpha (ASS v4+) authoring, timing, translation, and styling with Aegisub desktop parity.

---

## 1. Project Overview

SubEdit combines a modern Jetpack Compose interface with the native media rendering capabilities of `libmpv` and `libass`. It is built to run efficiently on Android tablets and smartphones while supporting complex typesetting tags, multi-track ASS documents, and high-spec video profiles.

For an architectural breakdown and benchmark against other tools, refer to [Feature and Architecture Comparison](docs/COMPARISON.md).

---

## 2. Technical Milestones and Status

The development of SubEdit is organized across five functional milestones:

| Milestone | Status | Description |
| :--- | :--- | :--- |
| **Core Engine** | Completed | In-memory ASS v4+ domain models, microsecond `SubTime` arithmetic, lossless tokenizer, SRT parser and exporter. |
| **Media Core** | Completed | Native `libmpv` integration, FFmpeg software decoding fallback for AVC High 4:2:2 `yuv422p`, and embedded `libass` rendering. |
| **Session Synchronization** | Completed | Centralized `ProjectSessionManager`, pure function `ActiveSubtitleResolver`, and frame-accurate playhead tracking. |
| **Waveform Timing** | Completed | Continuous center-playhead audio canvas, 50 buckets per second PCM peak extraction, and locked time-bucket scrubbing. |
| **Release Packaging** | Completed | Multi-ABI shared libraries (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`), asset font bundling, and debug APK validation. |

---

## 3. Architecture Highlights

- **Single Session In Memory**: All editor controls, dialog inspectors, and player tracks observe a unified `ProjectSessionManager` instance, eliminating drift between the visual canvas and playback state.
- **Pure Function Resolution**: Playhead matching uses `ActiveSubtitleResolver.findActiveDialogue`, enforcing `start <= time < end` boundary rules and ignoring comment events.
- **Software Decoding Fallback**: Using `hwdec=auto-safe`, `libmpv` renders anime files and production formats that standard Android `MediaCodec` ASIC hardware cannot decode.
- **Thermal-Efficient Audio Extraction**: Background audio extraction downsamples to 50 amplitude buckets per second using `Dispatchers.IO`, caching waveforms in memory to preserve battery life and eliminate scrubbing lag.

---

## 4. Documentation Index

- [Media Engine and Session Design](docs/specs/media-and-session-design.md): Specifications for `ProjectSessionManager`, `ActiveSubtitleResolver`, and `libmpv`.
- [Playback Stabilization Plan](docs/plans/playback-stabilization-plan.md): Bug fix verification checklist and unit testing strategy.
- [Feature and Architecture Comparison](docs/COMPARISON.md): Technical comparison between SubEdit, Aegisub Desktop, and standard Android subtitle tools.
- [Project Roadmap](ROADMAP.md): Detailed progress tracking across development phases.

---

## 5. Building and Testing

Requirements:
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35 (Minimum API 26)

Execute unit tests:
```bash
./gradlew testDebugUnitTest
```

Build debug APK:
```bash
./gradlew assembleDebug
```
