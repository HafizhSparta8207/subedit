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
- `scriptInfo`: Script metadata including resolution `PlayResX` and `PlayResY`, layout resolution, matrix parameters, and `preservedSections` (`[Aegisub Project Garbage]`, `[Aegisub Extradata]`, `[Fonts]`, `[Graphics]`) for lossless Aegisub Desktop round-trip export.
- `styles`: List of `AssStyle` definitions adhering to all 23 ASS v4+ `[V4+ Styles]` columns and preserving original studio style names (e.g., `DefaultCR`, `DefaultCR Italics`, `DefaultCR Tops`).
- `events`: Ordered list of `AssDialogueEvent` items representing spoken dialogues and preserved `Comment:` lines (`isComment = true`).

### 1.2 State Flow and Immutability

`ProjectSessionManager` exposes state through Kotlin `StateFlow<ProjectSessionState>`. Mutations occur through functional transforms:
- `updateScript(transform)`: Applies atomic updates to script metadata, styles, or events while incrementing `contentRevision`.
- `updatePlayhead(timeUs)`: Updates the current timestamp and triggers active dialogue resolution.
- `setActiveEventId(id)`: Synchronizes manual selection across table and timeline components.
- `setMediaLoaded(loaded)`: Tracks whether native media is loaded (`isMediaLoaded`). When `isMediaLoaded == false` (checkerboard mode / `AUDIO: NONE`), native `libmpv` commands (`seek`, `play`, `pause`, `sub-add`, `sub-reload`, `sub-remove`) are guarded and playback/scrubbing runs in Kotlin virtual playhead memory using `virtualDurationUs`.

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

SubEdit resolves device decoder limitations by combining an automatic chroma-only hardware `MediaCodec` proxy (`ChromaProxyConverter`) with `libmpv` backed by FFmpeg `libavcodec`:
- When an AVC High 4:2:2 (`yuv422p`) video is selected, `ChromaProxyConverter` checks `cacheDir/chroma_proxy/` (keyed by original file size and last-modified timestamp). If not yet cached, it converts chroma from `yuv422p` to `yuv420p` via hardware `MediaCodec` while preserving **100% original resolution (`width` x `height`)**, **exact 1:1 frame count and PTS**, a **1-second keyframe interval (`KEY_I_FRAME_INTERVAL = 1`)**, and **raw audio stream copy passthrough**.
- `libmpv` also retains `hwdec=auto-safe` software decoding fallback for any stream opened directly.

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

- `vo=gpu`: Leverages GPU for color space conversion and display scaling (activated only when a valid `Surface` is attached, without `force-window=immediate`).
- `gpu-context=android`: Attaches directly to the Android native window surface.
- `hwdec=auto-safe`: Attempts hardware decoding for standard profiles, automatically falling back to software decoding when complex chroma formats are encountered.
- `hwdec-codecs=h264,hevc,vp9,av1`: Restricts hardware acceleration to standard profiles, leaving 4:2:2 profiles to software decoding.
- `demuxer-max-bytes=32MiB` & `demuxer-max-back-bytes=16MiB`: Caps native demuxer packet buffering in RAM to prevent OOM on long videos and heavy production `.ass` scripts.
- `keep-open=always`: Prevents player teardown on file end, allowing smooth frame nudging.

### 3.3 Subtitle Track Rendering via Native libass

Subtitles are processed directly by `libass` compiled inside `libmpv` and synchronized with the 5-tool visual typesetting bar (`Select`, `\pos`, `\frz`, `\frx/\fry`, `\fscx/\fscy`) backed by a single-block head-tag upsert parser (`DialogueTokenizer`):
- `sub-ass=yes`: Enables the native ASS renderer.
- `sub-ass-override=no`: Preserves script formatting overrides such as font, margins, and inline positioning tags.
- `sub-fonts-dir`: Points to local application storage containing bundled fonts, avoiding font substitution issues.
- `sub-reload`: Triggered on content edits and visual gizmo drag completion (`onDragEnd`) when `isMediaLoaded == true` to refresh rendered subtitles without reloading the underlying video container.
