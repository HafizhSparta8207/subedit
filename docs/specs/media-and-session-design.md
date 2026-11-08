# Media Engine and Session Synchronization Design

This document specifies the architecture of the media playback pipeline, memory session management, and dialogue resolution for SubEdit.

---

## 1. ProjectSessionManager: Single Source of Truth

In subtitle editing workflows, UI state and playback synchronization must never diverge. SubEdit implements `ProjectSessionManager` as the central coordinator for the active subtitle project.

```
                        +----------------------------+
                        |   ProjectSessionManager    |
                        | (Single In-Memory Source)  |
                        +--------------+-------------+
                                       |
                 +---------------------+---------------------+
                 |                                           |
                 v                                           v
      +----------------------+                   +----------------------+
      | Jetpack Compose UI   |                   |  libmpv Media Engine |
      | - Video Monitor      |                   | - Native SurfaceView |
      | - Audio Waveform     |                   | - libass Subtitles   |
      | - Inspector / Text   |                   | - Playhead Observer  |
      +----------------------+                   +----------------------+
```

### 1.1 In-Memory Model Structure

The project state is maintained as an `AssScript` instance containing:
- `scriptInfo`: Script metadata including resolution `PlayResX` and `PlayResY`, layout resolution, and matrix parameters.
- `styles`: List of `AssStyle` definitions adhering to ASS v4+ formatting rules.
- `events`: Ordered list of `AssDialogueEvent` items representing dialogues and comments.

### 1.2 State Flow and Immutability

`ProjectSessionManager` exposes state through Kotlin `StateFlow<ProjectSessionState>`. Mutations occur through functional transforms:
- `updateScript(transform)`: Applies atomic updates to script metadata, styles, or events while incrementing `contentRevision`.
- `updatePlayhead(timeUs)`: Updates the current timestamp and triggers active dialogue resolution.
- `setActiveEventId(id)`: Synchronizes manual selection across table and timeline components.

---

## 2. ActiveSubtitleResolver: Pure Function Timing Resolution

Matching playback positions to subtitle lines requires strict boundary semantics. SubEdit delegates this to `ActiveSubtitleResolver`.

### 2.1 Boundary Semantics

A dialogue event is defined by its start and end timestamps. In compliance with ASS specifications and Aegisub conventions, dialogue visibility follows half-open intervals:

$$\text{start} \le \text{time} < \text{end}$$

- The start timestamp is inclusive. A playhead positioned exactly at `event.start` activates the event.
- The end timestamp is exclusive. A playhead positioned exactly at `event.end` deactivates the event.
- Comment lines (`isComment = true`) are filtered out and never rendered as active spoken dialogue.

### 2.2 Implementation

```kotlin
object ActiveSubtitleResolver {
    fun findActiveDialogue(events: List<AssEvent>, currentPositionMs: Long): AssEvent? {
        return events.firstOrNull { event ->
            !event.comment &&
            event.start.millis <= currentPositionMs &&
            currentPositionMs < event.end.millis
        }
    }

    fun findActiveDialogueUs(events: List<AssDialogueEvent>, currentPositionUs: Long): AssDialogueEvent? {
        return events.firstOrNull { event ->
            !event.isComment &&
            event.startUs <= currentPositionUs &&
            currentPositionUs < event.endUs
        }
    }
}
```

Because `ActiveSubtitleResolver` is a pure function with no side effects, it executes deterministically and can be validated independently through unit tests.

---

## 3. libmpv Video Pipeline and AVC High 4:2:2 Decoding

Standard Android `MediaCodec` hardware decoders frequently reject advanced H.264 profiles such as AVC High 4:2:2 with chroma subsampling `yuv422p`. This causes visual corruption or frame drops on typical mobile chipsets.

SubEdit bypasses device decoder limitations by embedding `libmpv` backed by FFmpeg `libavcodec`.

### 3.1 Decoder Pipeline

```
  Video Stream (AVC High 4:2:2 yuv422p)
                 |
                 v
       [ libmpv Engine Core ]
                 |
        hwdec=auto-safe check
                 |
        +--------+--------+
        |                 |
  MediaCodec ASIC   Fallback to libavcodec
    (Rejected)      (Software Decoding)
                          |
                          v
                    vo=gpu Surface
                          |
                          v
                 Android SurfaceView
```

### 3.2 Key Configuration Parameters

- `vo=gpu`: Leverages GPU for color space conversion and display scaling.
- `gpu-context=android`: Attaches directly to the Android native window surface.
- `hwdec=auto-safe`: Attempts hardware decoding for standard profiles, automatically falling back to software decoding when complex chroma formats are encountered.
- `hwdec-codecs=h264,hevc,vp9,av1`: Restricts hardware acceleration to standard profiles, leaving 4:2:2 profiles to software decoding.
- `keep-open=always`: Prevents player teardown on file end, allowing smooth frame nudging.

### 3.3 Subtitle Track Rendering via Native libass

Subtitles are processed directly by `libass` compiled inside `libmpv`:
- `sub-ass=yes`: Enables the native ASS renderer.
- `sub-ass-override=no`: Preserves script formatting overrides such as font, margins, and inline positioning tags.
- `sub-fonts-dir`: Points to local application storage containing bundled fonts, avoiding font substitution issues.
- `sub-reload`: Triggered on content edits to refresh rendered subtitles without reloading the underlying video container.
