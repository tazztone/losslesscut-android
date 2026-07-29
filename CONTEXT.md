# LosslessCut Domain Model & Glossary (`CONTEXT.md`)

This document establishes the ubiquitous domain language for LosslessCut.

---

## 1. Domain Terms

### EditingSession (Aggregate)
The central domain aggregate managing an active NLE editing workspace for one or more media clips.
- **Interface**: Exposes atomic state mutations (`splitSegment`, `updateSegmentBounds`, `toggleDiscard`, `undo`, `redo`, `save`, `restore`) and reactive state flows.
- **Invariants**: Enforces minimum segment duration (100ms), single-clip discard restrictions, undo stack limits (30 states), and dirty tracking.
- **Scope & Seam**: Placed in `:core:domain` as a pure JVM module. In-memory workspace state only; downstream export (`ExportUseCase`) and snapshot extraction (`ExtractSnapshotUseCase`) consume `EditingSessionState` snapshots.

### MediaClip & TrimSegment
- **MediaClip**: Represents an open video/audio file with metadata (URI, duration, tracks, keyframes).
- **TrimSegment**: A bounded timestamp interval (`startMs`..`endMs`) within a clip with an associated `SegmentAction` (`KEEP` vs `DISCARD`).

### MuxingPipeline (Engine Module)
The internal `:engine` processing pipeline hiding keyframe seeking, sample copying, PTS timestamp shifting, track validation, and `MediaMuxer` lifecycle management behind a clean internal engine seam while satisfying the `ILosslessEngine` domain interface.

### TimelineViewport (UI Geometry Module)
Pure Kotlin timeline geometry module placed in `:app/customviews` (zero Android dependencies). Encapsulates timestamp-to-pixel coordinate translations, zoom factor constraints (1x..20x), scroll panning offsets, hit-testing, and visible tile index calculations. Shared across `CustomVideoSeeker`, `SeekerRenderer`, `SeekerGhostRenderer`, and `SeekerAccessibilityHelper`.
