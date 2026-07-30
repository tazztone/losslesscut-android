# LosslessCut Domain Model & Glossary (`CONTEXT.md`)

This document establishes the ubiquitous domain language and architectural aggregates for LosslessCut.

---

## 1. Core Editing Domain Aggregates

### EditingSession (Aggregate Root)
The central domain aggregate managing an active NLE editing workspace for one or more media clips.
- **Interface**: Exposes atomic state mutations (`splitSegment`, `updateSegmentBounds`, `toggleDiscard`, `undo`, `redo`, `save`, `restore`) and reactive state flows (`EditingSessionState`).
- **Invariants**: Enforces minimum segment duration (100ms), single-clip discard restrictions, undo stack limits (30 states), and dirty tracking.
- **Scope & Seam**: Placed in `:core:domain` as a pure JVM module. In-memory workspace state only; downstream export (`ExportUseCase`) and snapshot extraction (`ExtractSnapshotUseCase`) consume `EditingSessionState` snapshots.

### MediaClip & TrimSegment
- **MediaClip**: Represents an open video or audio file with metadata (URI, duration, stream tracks, sync keyframes).
- **TrimSegment**: A bounded timestamp interval (`startMs`..`endMs`) within a clip associated with a `SegmentAction` (`KEEP` vs `DISCARD`).

---

## 2. Smart Cut & Analysis Entities

### SilenceCutConfig & VisualDetectionConfig
- **SilenceCutConfig**: Parameterized value object defining silence threshold (dB), minimum silence duration (ms), and padding bounds for automated section removal.
- **VisualDetectionConfig**: Configuration object driving frame analysis: frame sampling step (`sampleIntervalFrames`), pHash difference threshold, SAD luminance delta, and contrast-normalized Laplacian variance threshold.

### FrameAnalysis & WaveformResult
- **FrameAnalysis**: Computed frame quality metrics (perceptual hash, sum of absolute differences, Laplacian blur score).
- **WaveformResult**: Decoded PCM audio amplitude vector optimized for timeline waveform rendering.

---

## 3. Media Processing Engine

### MuxingPipeline (Engine Module)
The internal `:engine` processing pipeline hiding keyframe seeking, sample copying, PTS timestamp shifting, track validation, and `MediaMuxer` lifecycle management behind a clean internal engine seam while satisfying the `ILosslessEngine` domain interface.

---

## 4. Storage & Cache Subsystems

### IAnalysisCache & AnalysisCacheImpl
- **IAnalysisCache**: Pure JVM domain interface defining cache read/write contracts for waveforms and frame metrics.
- **AnalysisCacheImpl**: Android file-backed binary cache implementation in `:core:data` using versioned binary headers, LRU byte cap eviction, and age-based retention expiry in app-private storage.

### IMediaFinalizer & StorageUtils
- **IMediaFinalizer**: Domain interface handling storage destination registration (`IS_PENDING = 0` lifecycle on Android 10+ MediaStore vs SAF DocumentFile SAF tree targets).
- **StorageUtils**: Android helper module executing Storage Access Framework tree operations and MediaStore collection queries.

---

## 5. UI Geometry & Rendering

### TimelineViewport (UI Geometry Module)
Pure Kotlin timeline geometry module in `:app/customviews` (zero Android dependencies). Encapsulates timestamp-to-pixel coordinate translations, zoom factor constraints (1x..20x), scroll panning offsets, hit-testing, and visible tile index calculations. Shared across `CustomVideoSeeker`, `SeekerRenderer`, `SeekerGhostRenderer`, and `SeekerAccessibilityHelper`.

