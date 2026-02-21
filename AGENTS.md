# Developer Guide & PRD (`AGENTS.md`)

## 1. Product Vision & Requirements
**LosslessCut** is a lightweight Android app for **lossless media trimming (video and audio)**.
**Core Value**: Speed and zero generational quality loss by manipulating the media container (`MediaExtractor`/`MediaMuxer`) without re-encoding the actual video/audio streams.

**User Stories & Capabilities**:
- **Lossless Video/Audio Trimming**: Precisely define start/end points and save segments instantly without transcoding.
- **Audio-Only Mode**: Automatically adapts the UI for audio files, providing a specialized music note placeholder and hiding video-only tools (e.g., rotation, snapshots).
- **Multi-Segment Export**: Split media, discard parts (ads/silences), and export remaining segments as individual files in one pass.
- **Precision Seeking**: Zoomable timeline (up to 20x) for frame-accurate cuts based on keyframes (for video) or time.
- **Accessibility**: First-class support for accessibility services via `ExploreByTouchHelper`, allowing vision-impaired users to edit media via standard talkback gestures and custom accessibility actions.
- **Undo/Redo Workflow**: Non-destructive workflow via an in-memory snapshot stack.

**Non-Goals (Do Not Implement)**:
- ❌ Lossy video re-encoding or compression.
- ❌ Visual filters, text overlays, or color grading.
- ❌ Advanced audio mixing or effects beyond lossless cuts.

## 2. Environment & Stack
- **Stack**: Kotlin 1.9+, Android SDK (Min API 31, Target 35), Media3 (ExoPlayer).
- **DI & Persistence**: Hilt for dependency injection, Jetpack DataStore for preferences.
- **Architecture**: MVVM, single-Activity, Scoped Storage (`MediaStore`).

## 3. Core Architecture

### UI Layer (`VideoEditingActivity.kt`, `CustomVideoSeeker.kt`, `SettingsBottomSheetDialogFragment.kt`)
- `VideoEditingActivity`: Exoplayer initialization, binds to ViewModel state (`VideoEditingUiState`), manages the split/delete/undo UI states, and handles tooltips/rotation previews. Adapts the UI dynamically based on `isAudioOnly` flag.
- `SettingsBottomSheetDialogFragment`: Presents app preferences such as the "Snap" (Lossless) mode toggle.
- `CustomVideoSeeker`: High-performance custom timeline View. Features:
    - **Gestures**: Drag gesture `TouchTarget` logic (HANDLE_LEFT, HANDLE_RIGHT, PLAYHEAD) with edge-auto-panning.
    - **Visuals**: Haptic snapping to keyframes, cycling colors for `KEEP` segments, and animated pinch-to-zoom indicators.
    - **Accessibility**: Implements `ExploreByTouchHelper` to expose playhead and segment handles as virtual views with support for `ACTION_SCROLL_FORWARD/BACKWARD`.

### Presentation (`VideoEditingViewModel.kt`)
- `TrimSegment`: Data model holding `startMs`, `endMs`, and `SegmentAction` (KEEP/DISCARD).
- `Undo Stack`: Deep copies of `List<TrimSegment>` state pushed on destructive actions (capped at 30 items for memory safety).
- **Hilt**: ViewModels are injected with `LosslessEngine`, `StorageUtils`, and `AppPreferences` (DataStore).
- Automates the multi-clip export orchestration and snapshot frame extraction. Detects media type (video vs. audio) during initialization.

### Domain / Data (`LosslessEngine.kt`, `StorageUtils.kt`, `AppPreferences.kt`)
- `LosslessEngine`: Core extraction/muxing logic. Handles the EOS duration fix and export params (rotation override, audio-only tracks). Automatically detects track types and finalizes to appropriate MediaStore collections.
- `StorageUtils`: Scoped Storage utility for `MediaStore` URI generation, handling video, audio, and PNG frame snapshots. Saves video to `Movies/LosslessCut` and audio to `Music/LosslessCut`.
- `AppPreferences`: Reactive wrapper around DataStore for managing settings like snap mode, undo limits, and snapshot formats.


## 4. Developer Workflows & CI/CD
- **Testing**: `./gradlew test` (runs JVM/Robolectric `LosslessEngine` tests), `./gradlew connectedAndroidTest` (UI tests).
- **CI/CD (`release.yml`)**: GitHub Action auto-builds/publishes APKs on `v*` tags. Requires repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.

## 5. Roadmap (v3.0+)
1. **Precise Trim (Smart Cut)**: Decode and re-encode *only* the frames between a cut point and the nearest keyframe.
2. **Merging**: Lossless concatenation of segments via PTS alignment.
3. **Background/AI**: `WorkManager` for large background exports; AI-based automatic silence/motion detection.

## 6. Context7 Library IDs
Use the Context7 MCP server's `query-docs` tool with these IDs for up-to-date documentation and code examples:
- Media3 (ExoPlayer): `/androidx/media`
- Coroutines: `/kotlin/kotlinx.coroutines`
- AndroidX: `/androidx/androidx`
- Material: `/material-components/material-components-android`
- Lottie: `/airbnb/lottie-android`
- Robolectric: `/robolectric/robolectric`

**Example Usage**:
`mcp_context7_query-docs(libraryId="/androidx/media", query="How to initialize ExoPlayer with Media3?")`
