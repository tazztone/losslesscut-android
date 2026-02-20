# Developer Guide & PRD (`AGENTS.md`)

## 1. Product Vision & Requirements
**Lossless Video Cut** is a lightweight Android app for **lossless video trimming**. 
**Core Value**: Speed and zero generational quality loss by manipulating the video container (`MediaExtractor`/`MediaMuxer`) without re-encoding the actual video/audio streams.

**User Stories & Capabilities**:
- **Lossless Trimming**: Precisely define start/end points and save segments instantly.
- **Multi-Segment Export**: Split video, discard parts (ads/silences), and export remaining segments as individual files in one pass.
- **Precision Seeking**: Zoomable timeline (up to 20x) for frame-accurate cuts based on keyframes.
- **Undo/Redo Workflow**: Non-destructive workflow via an in-memory snapshot stack.

**Non-Goals (Do Not Implement)**:
- ❌ Video re-encoding or compression.
- ❌ Visual filters, text overlays, or color grading.
- ❌ Advanced audio mixing or effects.

## 2. Environment & Stack
- **Stack**: Kotlin 1.9+, Android SDK (Min API 31, Target 34+), Media3 (ExoPlayer).
- **Architecture**: MVVM, single-Activity, Scoped Storage (`MediaStore`).

## 3. Core Architecture

### UI Layer (`VideoEditingActivity.kt`, `CustomVideoSeeker.kt`)
- `VideoEditingActivity`: Exoplayer initialization, binds to ViewModel state (`VideoEditingUiState`), manages the split/delete/undo UI states.
- `CustomVideoSeeker`: High-performance custom timeline View. Features: drag gesture `TouchTarget` logic (HANDLE_LEFT, HANDLE_RIGHT, PLAYHEAD), auto-pan near edges, and haptic snapping to keyframes.

### Presentation (`VideoEditingViewModel.kt`)
- `TrimSegment`: Data model holding `startMs`, `endMs`, and `SegmentAction` (KEEP/DISCARD).
- `Undo Stack`: Deep copies of `List<TrimSegment>` state pushed on destructive actions (capped at 30 items for memory safety).
- Automates the multi-clip export orchestration.

### Domain / Data (`LosslessEngine.kt`, `StorageUtils.kt`)
- `LosslessEngine`: Core extraction/muxing logic. Handles the EOS duration fix (syncs written video vs. audio samples to prevent "frozen last frame" bugs) and export params (rotation override, audio-only).
- `StorageUtils`: Scoped Storage utility for `MediaStore` URI generation and file finalization.

### Data Flow (Trim Action)
1. User confirms trim -> `Activity` calls `ViewModel.trimVideo()`.
2. `ViewModel` emits Loading state -> calls `StorageUtils.createVideoOutputUri()`.
3. `ViewModel` calls `LosslessEngine.executeLosslessCut(input, outputUri, start, end)`.
4. `LosslessEngine` processes samples -> calls `StorageUtils.finalizeVideo()` -> returns `Result.success(uri)`.
5. `ViewModel` emits Success state -> `Activity` reloads ExoPlayer with the new output URI.

## 4. Developer Workflows & CI/CD
- **Testing**: `./gradlew test` (runs JVM/Robolectric `LosslessEngine` tests), `./gradlew connectedAndroidTest` (UI tests).
- **CI/CD (`release.yml`)**: GitHub Action auto-builds/publishes APKs on `v*` tags. Requires repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.

## 5. Roadmap (v3.0+)
1. **Precise Trim (Smart Cut)**: Decode and re-encode *only* the frames between a cut point and the nearest keyframe.
2. **Merging**: Lossless concatenation of segments via PTS alignment.
3. **Background/AI**: `WorkManager` for large background exports; AI-based automatic silence/motion detection.

## 6. Context7 Library IDs
Use the `query-docs` tool with these IDs for up-to-date documentation and code examples:
- Media3 (ExoPlayer): `/androidx/media`
- Coroutines: `/kotlin/kotlinx.coroutines`
- AndroidX: `/androidx/androidx`
- Material: `/material-components/material-components-android`
- Lottie: `/airbnb/lottie-android`
- Robolectric: `/robolectric/robolectric`
