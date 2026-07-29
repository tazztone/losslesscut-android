# LosslessCut Architecture & Technical Reference

This document provides a detailed technical breakdown of the architecture, module design, component blueprint, key workflows, and format compatibility for LosslessCut (MP4).

---

## 1. System Architecture

LosslessCut follows **MVVM + Clean Architecture** principles with strict layer boundaries, reactive state management, and native Android media processing.

```
                  ┌─────────────────────────────────────────┐
                  │                 :app                    │
                  │   (UI, Fragments, Jetpack ViewModels)   │
                  └────────────────────┬────────────────────┘
                                       │
                                       ▼
                  ┌─────────────────────────────────────────┐
                  │              :core:domain               │
                  │ (Pure JVM Library: Use Cases & Models)  │
                  └────────────────────▲────────────────────┘
                                       │
                  ┌────────────────────┴────────────────────┐
                  │                 :engine                 │
                  │   (MediaExtractor & MediaMuxer Engine)  │
                  └─────────────────────────────────────────┘
```

### Technology Stack
- **Languages**: Kotlin 2.2+, Gradle Kotlin DSL (`.gradle.kts`)
- **Media Engine**: Native `MediaExtractor`, `MediaMuxer` (Processing in `:engine`), Media3 (Playback UI in `:app`)
- **Dependency Inversion**: Clean boundaries via `:core:domain` interfaces (JSR-330)
- **Dependency Injection**: Hilt (in Android modules `:app`, `:engine`, `:core:data`)
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 15 / "Baklava")
- **Tooling**: AGP 9.0+ (Built-in Kotlin Support), JDK 17/21 Toolchain

---

## 2. Project Structure & Module Boundaries

The codebase is organized into modular layers:

- **`:app`**: Main Android application module.
  - `ui`: Fragments (`EditorFragment`, `RemuxFragment`, `MetadataFragment`), Custom Views (`CustomVideoSeeker`), and Navigation graph.
  - `ui/compose`: Allowed exclusively for new, isolated UI sheets/dialogs.
  - `viewmodel`: Jetpack ViewModels (`VideoEditingViewModel`) delegating logic to Use Cases.
- **`:core:domain`**: Pure Kotlin/JVM library. Contains Use Cases, Entities (`MediaClip`, `TrimSegment`), and Domain Interfaces. Zero Android/Hilt dependencies.
- **`:engine`**: Core media processing engine (`LosslessEngine`, `TrackInspector`, `VisualSegmentDetectorImpl`). Decoupled from storage and UI via domain interfaces.
- **`:core:data`**: Shared data layer containing persistence (`AppPreferences`), Repository implementations, and MediaStore/SAF finalizers (`IMediaFinalizer`).

> [!IMPORTANT]
> **Module Guardrails**: `:app` uses `runtimeOnly(:engine)` and cannot import engine classes directly. All interactions flow through Hilt bindings and `:core:domain` interfaces.

---

## 3. Component Blueprint

### UI & Navigation
- **Jetpack Navigation**: Manages transitions between NLE editing modes (`EditorFragment`), container conversion (`RemuxFragment`), and rotation metadata overrides (`MetadataFragment`).
- **`CustomVideoSeeker`**: High-performance custom `View` for NLE timeline scrubbing.
  - *Logic*: Supports multi-touch zoom (up to 20x), playhead/segment drag gestures, and edge auto-panning.
  - *Accessibility*: Implements `ExploreByTouchHelper` virtual view hierarchy for screen reader navigation.
  - *Performance*: Uses `LruCache` waveform bitmap tile caching (2048px tiles) to eliminate per-frame canvas line rendering.
- **Layout System**: Orientation-specific layouts (`layout` vs `layout-land`). Includes clip ID-based targeting for sidebar selection and auto-pausing player overlays upon opening dialogs.

### User Interaction & Control Layer
- **Smart Cut Overlay (`SmartCutOverlayController`)**: Tabbed overlay managing:
  - `SilenceDetectionOverlayController`: Parameterized audio silence removal with ghost-state previews.
  - `VisualDetectionOverlayController`: Visual segment detection engine (Scene Changes, Black Frames, Freeze Frames, Blur Quality).
- **Keyboard Shortcuts (`ShortcutHandler`)**:
  - `SPACE`: Play/Pause toggle.
  - `I` / `O`: Set In/Out segment boundaries.
  - `S`: Split segment at current playhead position.
  - `LEFT` / `RIGHT`: Jump to previous/next keyframe.
  - `ALT + LEFT/RIGHT`: Nudge playhead precision position.
- **Playback Speed**: Configurable rates (`0.25x`, `0.5x`, `1.0x`, `1.5x`, `2.0x`, `4.0x`).

### Data & Domain Logic
- **`LosslessEngine`**: Central muxing engine.
  - `executeLosslessCut`: Extracts and remuxes requested timestamps without re-encoding. Automatically routes audio-only clips to `.m4a` containers.
  - `executeLosslessMerge`: Concatenates compatible media clips while shifting sample Presentation Timestamps (PTS).
- **`VideoEditingViewModel`**: State machine orchestrator. Context-free design emitting one-time UI events via `Channel<VideoEditingEvent>` and preserving undo/redo history stacks.
- **Detection Heuristics**:
  - *Silence Detection*: Uses absolute raw amplitudes from waveform data to prevent false amplification of quiet passages.
  - *Visual Detection*: Pure Kotlin frame analysis using pHash, Sum of Absolute Differences (SAD), and Laplacian variance on sequential `MediaExtractor` frames.

---

## 4. Key Media Workflows

### Lossless Export Process
1. `MediaExtractor` seeks to the keyframe (sync sample) immediately preceding `startMs`.
2. Encoded samples are extracted and written to `MediaMuxer`.
3. Samples prior to `startMs` are filtered out based on Presentation Timestamps (PTS).
4. Extracted samples write continuously until `endMs` is reached, ensuring lossless quality with zero transcoding.

### Multi-Clip Merging Workflow
1. Validates stream compatibility across input clips (matching video/audio codecs and container parameters).
2. Calculates cumulative duration offsets across input segments.
3. Adjusts sample PTS values sequentially during muxing to create a seamless output file.

---

## 5. Format Compatibility Matrix

LosslessCut operates strictly at the **container level** using Android's native `MediaMuxer` to ensure lightning-fast processing and zero quality degradation.

| Category | Primary Support (Lossless) | Legacy/Technical Support |
| :--- | :--- | :--- |
| **Output Container** | `.mp4`, `.m4a` | — |
| **Video Codecs** | **H.264 (AVC)**, **H.265 (HEVC)** | H.263, MPEG-4 Visual |
| **Audio Codecs** | **AAC (LC, HE)** | AMR-NB, AMR-WB |
| **Input Containers** | `.mp4`, `.m4a`, `.mov`, `.mkv`* | `.3gp`, `.webm`* |

\* *MKV, MOV, and WebM input files can be remuxed to MP4 without re-encoding ONLY if internal audio/video streams match supported codecs.*

> [!NOTE]
> **Why are these the supported formats?**
> Standard Android `MediaMuxer` enforces strict MP4 container compliance. Formats like **MP3**, **FLAC**, **VP9**, or **Vorbis** require full transcoding to place into an MP4 container, which would defeat the zero-loss purpose of LosslessCut.

---

## 6. Context7 Documentation IDs

For external AI documentation lookups:
- `/androidx/media` (Media3/ExoPlayer)
- `/kotlin/kotlinx.coroutines` (Coroutines & Flows)
- `/androidx/datastore` (Preferences DataStore)
- `/material-components/material-components-android` (Material 3 UI)
