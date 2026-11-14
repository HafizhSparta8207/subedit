# Feature and Architecture Comparison

This document outlines the architectural differences between SubEdit, Aegisub Desktop, and standard Android subtitle editors.

---

## 1. Architecture Matrix

| Feature Area | SubEdit (Android) | Aegisub (Desktop) | Standard Mobile Subtitle Apps |
| :--- | :--- | :--- | :--- |
| **Platform Target** | Android 8.0+ (ARM64, x86_64) | Windows, macOS, Linux | Android |
| **UI Framework** | Jetpack Compose (Material3) | wxWidgets C++ | Android Views or Flutter |
| **Media Engine** | libmpv with FFmpeg core (`32MiB`/`16MiB` demuxer cache cap & `isMediaLoaded` idle guard) | libavcodec, DirectShow, ffms2 | AndroidX Media3 / ExoPlayer |
| **Subtitle Engine** | Native libass inside libmpv | Native libass | Regex parser + Compose Text / TextView |
| **4:2:2 Video Decoding** | Auto Chroma-Only Proxy (`yuv422p` -> `yuv420p` via hardware `MediaCodec`, 100% resolution, 1:1 PTS, audio passthrough) + software fallback | Supported via FFmpeg / ffms2 | Often fails with MediaCodec decoding error |
| **Time Precision** | Microseconds (Long integer) | Centiseconds or Milliseconds | Milliseconds |
| **In-Memory State** | ProjectSessionManager (Single source) | Aegisub AssFile linked list | Loose Activity or ViewModel state |
| **Audio Waveform** | 50 buckets/sec PCM cache with viewport culling | Full PCM or peak cache | Uncached or basic amplitude visualizer |
| **Styling Fidelity** | Lossless ASS v4+ round-trip (all 23 `[V4+ Styles]` columns incl. `DefaultCR`, Aegisub metadata, `Comment:` lines) & 5 visual tools (`Select`, `\pos`, `\frz`, `\frx/\fry`, `\fscx/\fscy`) | Full ASS v4+ tag parsing & visual tools | Basic SRT styling, bold/italic only |

---

## 2. Key Differentiation Analysis

### 2.1 Subtitle Rendering Accuracy
- **Standard Mobile Apps**: Most Android subtitle editors parse subtitles by stripping tags with regular expressions and rendering plain strings into standard text components. Advanced tags such as `\pos`, `\frz`, `\clip`, `\fad`, and multi-border outlines cannot be displayed faithfully.
- **SubEdit**: SubEdit embeds `libass` through `libmpv` and preserves 100% of original `[V4+ Styles]` definitions (including studio variants like `DefaultCR`), `[Aegisub Project Garbage]`, `[Aegisub Extradata]`, `[Fonts]`, `[Graphics]`, and `Comment:` lines for lossless round-trip export. Its 5-tool visual typesetting bar (`Select`, `\pos`, `\frz`, `\frx/\fry`, `\fscx/\fscy`) updates a single leading override block without corrupting inline tags.

### 2.2 Video Codec Compatibility
- **Standard Mobile Apps**: Relying solely on `MediaCodec` restricts playback to hardware profiles certified by the device manufacturer. AVC High 4:2:2 video streams typically output black screens, green lines, or decoder failure exceptions.
- **SubEdit**: Combines an automatic chroma-only hardware `MediaCodec` proxy (`yuv422p` to `yuv420p` cached in `cacheDir/chroma_proxy/` with 100% original resolution, 1:1 frame PTS, 1s keyframe interval, and raw audio stream copy) with `hwdec=auto-safe` FFmpeg software decoding fallback, bounded demuxer cache (`32MiB` / `16MiB`), and an `isMediaLoaded` idle-state guard.

### 2.3 Audio Timeline Scrubbing Stability
- **SubEdit**: Implements locked time-bucket indexing. Waveform bars are calculated from fixed absolute timestamps rather than floating canvas coordinates, eliminating visual jitter and sample deformation during rapid scrolling.
