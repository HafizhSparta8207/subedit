# Feature and Architecture Comparison

This document outlines the architectural differences between SubEdit, Aegisub Desktop, and standard Android subtitle editors.

---

## 1. Architecture Matrix

| Feature Area | SubEdit (Android) | Aegisub (Desktop) | Standard Mobile Subtitle Apps |
| :--- | :--- | :--- | :--- |
| **Platform Target** | Android 8.0+ (ARM64, x86_64) | Windows, macOS, Linux | Android |
| **UI Framework** | Jetpack Compose (Material3) | wxWidgets C++ | Android Views or Flutter |
| **Media Engine** | libmpv with FFmpeg core | libavcodec, DirectShow, ffms2 | AndroidX Media3 / ExoPlayer |
| **Subtitle Engine** | Native libass inside libmpv | Native libass | Regex parser + Compose Text / TextView |
| **4:2:2 Video Decoding** | Supported via software fallback | Supported via FFmpeg / ffms2 | Often fails with MediaCodec decoding error |
| **Time Precision** | Microseconds (Long integer) | Centiseconds or Milliseconds | Milliseconds |
| **In-Memory State** | ProjectSessionManager (Single source) | Aegisub AssFile linked list | Loose Activity or ViewModel state |
| **Audio Waveform** | 50 buckets/sec PCM cache | Full PCM or peak cache | Uncached or basic amplitude visualizer |
| **Styling Fidelity** | Full ASS v4+ tag parsing | Full ASS v4+ tag parsing | Basic SRT styling, bold/italic only |

---

## 2. Key Differentiation Analysis

### 2.1 Subtitle Rendering Accuracy
- **Standard Mobile Apps**: Most Android subtitle editors parse subtitles by stripping tags with regular expressions and rendering plain strings into standard text components. Advanced tags such as `\pos`, `\frz`, `\clip`, `\fad`, and multi-border outlines cannot be displayed faithfully.
- **SubEdit**: SubEdit embeds `libass` through `libmpv`. The player surface renders the subtitle stream with exact desktop parity, supporting font metrics, vector drawings, blur, clipping, and rotation tags.

### 2.2 Video Codec Compatibility
- **Standard Mobile Apps**: Relying solely on `MediaCodec` restricts playback to hardware profiles certified by the device manufacturer. AVC High 4:2:2 video streams typically output black screens, green lines, or decoder failure exceptions.
- **SubEdit**: Using `hwdec=auto-safe` with `libmpv` allows seamless fallback to FFmpeg software decoding, ensuring reliable rendering of production anime files and mastering formats.

### 2.3 Audio Timeline Scrubbing Stability
- **SubEdit**: Implements locked time-bucket indexing. Waveform bars are calculated from fixed absolute timestamps rather than floating canvas coordinates, eliminating visual jitter and sample deformation during rapid scrolling.
