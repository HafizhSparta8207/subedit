# SubEdit

SubEdit is a native Android subtitle editor designed for advanced SubStation Alpha (ASS v4+) authoring, timing, translation, and styling with Aegisub desktop parity.

---

## 1. Project Overview

SubEdit combines a modern Jetpack Compose interface with the native media rendering capabilities of `libmpv` and `libass`. It is built to run efficiently on Android tablets and smartphones while supporting complex typesetting tags, multi-track ASS documents, and high-spec video profiles.

For an architectural breakdown and benchmark against other tools, refer to [Feature and Architecture Comparison](docs/COMPARISON.md).

---

## 2. Technical Milestones and Status

The development of SubEdit is organized across functional milestones:

| Milestone | Status | Description |
| :--- | :--- | :--- |
| **Core Engine** | Completed | In-memory ASS v4+ domain models, microsecond `SubTime` arithmetic, lossless Aegisub round-trip parser (`[V4+ Styles]`, metadata sections, and `Comment:` lines), SRT parser and exporter. |
| **Media Core & Sync** | Completed | Native `libmpv` integration, auto chroma-only proxy (`yuv422p` to `yuv420p`) with FFmpeg software fallback, `32MiB`/`16MiB` demuxer cache cap, and idle-state guard (`isMediaLoaded`). |
| **Waveform Timing** | Completed | Center-playhead audio canvas, 50 buckets/sec PCM peak extraction, viewport culling, and locked time-bucket scrubbing. |
| **Visual Typesetting** | Completed | 5-tool visual typesetting bar (`Select`, `\pos`, `\frz`, `\frx/\fry`, `\fscx/\fscy`) with single-block head-tag upsert parser. |
| **Packaging & Optimization**| Planned | Multi-ABI split optimization to trim release APK footprint down to ~45 MB. |

---

## 3. Architecture Highlights

- **Single Session In Memory**: All editor controls, dialog inspectors, and player tracks observe a unified `ProjectSessionManager` instance, eliminating drift between the visual canvas and playback state.
- **Pure Function Resolution**: Playhead matching uses `ActiveSubtitleResolver.findActiveDialogue`, enforcing `start <= time < end` boundary rules and ignoring comment events.
- **Auto Chroma-Only Proxy & Software Decoding Fallback**: `ChromaProxyConverter` detects AVC High 4:2:2 (`yuv422p`) streams and transcodes chroma to `yuv420p` via hardware `MediaCodec` (preserving 100% original resolution, 1:1 PTS/frame count, and raw audio passthrough), paired with `hwdec=auto-safe` software decoding fallback and an `isMediaLoaded` idle-state guard.
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
