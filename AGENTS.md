# Developer Reference (`AGENTS.md`)

## 1. System Architecture
LosslessCut follows **MVVM** architecture with a focus on reactive UI and native media processing.

### Tech Stack
- **Languages**: Kotlin 1.9+
- **Media Engine**: Media3 (ExoPlayer), `MediaExtractor`, `MediaMuxer`
- **Dependency Injection**: Hilt
- **Persistence**: Jetpack DataStore (AppPreferences)
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)

## 2. Component Blueprint

### UI & Custom Views
- **`VideoEditingActivity`**: Central hub. Manages ExoPlayer lifecycle, binds ViewModel state, and coordinates UI transitions between Video and Audio modes.
- **`CustomVideoSeeker`**: A high-performance custom `View` for the NLE timeline.
    - **Logic**: Handles multi-touch (zoom), drag gestures for playhead and segments, and edge-auto-panning.
    - **Accessibility**: Implements `ExploreByTouchHelper` to expose virtual nodes for playhead and segment handles. Supports standard accessibility actions (Scroll Forward/Backward).
    - **Visuals**: Draws segment colors, keyframe ticks, and zoom levels directly on the canvas for performance.

### Data & Domain Logic
- **`LosslessEngine`**: Core muxing orchestration.
    - `executeLosslessCut`: Trims a single file.
    - `executeLosslessMerge`: Concatenates multiple `MediaClip` objects or segments. Handles PTS (Presentation Time Stamp) shifting to ensure seamless playback in the output container.
- **`VideoEditingViewModel`**: State machine for the editor.
    - **State**: `VideoEditingUiState` (Initial, Loading, Success, Error).
    - **Undo Stack**: In-memory list of `List<MediaClip>` snapshots.
    - **Export**: Orchestrates single-clip multi-segment export OR multi-clip merging based on user selection.

### Utilities
- **`StorageUtils`**: Handles Scoped Storage. Saves to `Movies/LosslessCut` (video) or `Music/LosslessCut` (audio). Manages URI generation and MediaStore finalization.
- **`TimeUtils`**: Formatting and precision conversion between MS and microseconds.

## 3. Key Workflows

### Lossless Export Process
1. `MediaExtractor` seeks to the nearest keyframe *before* the requested `startMs`.
2. Encoded samples are read and passed to `MediaMuxer`.
3. Samples *before* `startMs` are discarded by the muxer logic based on timestamp.
4. Samples are written until `endMs`.
5. `MediaMuxer` finalizes the file, updating the duration in the header.

### Multi-Clip Merging
- Validates track compatibility (codecs must match for lossless concatenation).
- Shifts sample PTS values by the cumulative duration of previous segments to ensure continuity.

## 4. Development & CI
- **Testing**: 
    - JVM: `./gradlew test` (Robolectric for Engine/ViewModel).
    - Android: `./gradlew connectedAndroidTest` (Espresso for UI/Timeline).
- **Scripts**: `dev-scripts/` contains helpers for cleaning, pushing debug builds, and viewing logs.
- **CI**: GitHub Actions (`release.yml`) builds and signs production APKs on tag push.

## 5. Roadmap
1. **Smart Rendering (v3.0)**: Use `MediaCodec` to decode/re-encode only the first/last GOP of a cut for true frame-accuracy while keeping the rest lossless.
2. **AI Tools**: Integration of on-device ML for automatic scene change detection.
3. **Task Orchestration**: Migrate export to `WorkManager` to support background processing for extremely large files.

## 6. Context7 Library IDs
Use these IDs for documentation queries:
- `/androidx/media` (Media3/ExoPlayer)
- `/kotlin/kotlinx.coroutines` (Concurrency)
- `/androidx/datastore` (Preferences)
- `/material-components/material-components-android` (UI)
