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

## 2. Project Structure
The project is organized into a layer/feature-based structure within the `com.tazztone.losslesscut` package:
- **`ui`**: Activities, Fragments, and Adapters (`MainActivity`, `VideoEditingActivity`, `MediaClipAdapter`).
- **`viewmodel`**: Jetpack ViewModels (`VideoEditingViewModel`).
- **`engine`**: Core media processing logic (`LosslessEngine`, `AudioWaveformExtractor`).
- **`data`**: Data persistence and preferences (`AppPreferences`).
- **`utils`**: General helper classes (`StorageUtils`, `TimeUtils`, `DetectionUtils`).
- **`di`**: Hilt dependency injection modules (`AppModule`).
- **`customviews`**: Complex custom UI components (`CustomVideoSeeker`).

## 3. Component Blueprint

### UI & Custom Views
- **VideoEditingActivity**: Central hub. Manages ExoPlayer lifecycle, binds ViewModel state, and coordinates UI transitions between Video and Audio modes.
- **CustomVideoSeeker**: A high-performance custom `View` for the NLE timeline.
    - **Logic**: Handles multi-touch (zoom), drag gestures for playhead and segments, and edge-auto-panning.
    - **Accessibility**: Implements `ExploreByTouchHelper` to expose virtual nodes for playhead and segment handles. Supports standard accessibility actions (Scroll Forward/Backward).
    - **Visuals**: Draws segment colors, keyframe ticks, and zoom levels directly on the canvas for performance.
- **Layout System**: Uses orientation-specific layouts (`layout` vs `layout-land`) to maintain ergonomics. 
    - **Sidebars**: Landscape mode uses vertical sidebars for primary toolbar actions.
    - **Playlist Sidebar**: Synced with ExoPlayer's `currentMediaItemIndex`. Uses a `RecyclerView` with an inline "Add Media" placeholder. Visibility is tied to `clips.size > 1`.
    - **Overlays**: Semi-transparent overlays for player controls ensure unified UX across both orientations.

### Data & Domain Logic
- **`LosslessEngine`**: Core muxing orchestration.
    - `executeLosslessCut`: Trims a single file. Bypasses video-specific orientation hints if no video track is present.
    - `executeLosslessMerge`: Concatenates multiple `MediaClip` objects or segments. Handles PTS shifting and validates track availability.
- **`VideoEditingViewModel`**: State machine for the editor.
    - **State**: `VideoEditingUiState` (Initial, Loading, Success, Error). `Success` state includes `hasAudioTrack` flag for UI constraints.
    - **Undo Stack**: In-memory list of `List<MediaClip>` snapshots.
    - **Persistence**: Implements `saveSession()` (JSON serialization to cache) and `restoreSession()` to allow resuming work on the current URI.
    - **Silence Detection**: Orchestrates `DetectionUtils.findSilence` to preview or apply automated cuts based on waveform analysis.
    - **Export**: Orchestrates single-clip multi-segment export OR multi-clip merging. Automatically selects `.m4a` extension and `Music` storage if video is unchecked.

### Utilities
- **StorageUtils**: Handles Scoped Storage. Centralizes URI creation via `createMediaOutputUri`, which dynamically selects `Movies/LosslessCut` or `Music/LosslessCut` based on the requested media type and sets appropriate MIME types. Supports custom tree URIs for user-selected folders.
- **TimeUtils**: Formatting and precision conversion between MS and microseconds.
- **DetectionUtils**: Parameterized silence detection algorithm (`findSilence`) that scans audio waveforms for regions below a decibel-relative threshold.
- **Permission Management**: The app relies primarily on the **Storage Access Framework (SAF)** and `ActivityResultContracts.OpenMultipleDocuments`. Broad runtime permissions (like `READ_MEDIA_VIDEO` or `POST_NOTIFICATIONS`) are avoided for enhanced privacy and UX.

## 4. Key Workflows

### Lossless Export Process
1. `MediaExtractor` seeks to the nearest keyframe *before* the requested `startMs`.
2. Encoded samples are read and passed to `MediaMuxer`.
3. Samples *before* `startMs` are discarded by the muxer logic based on timestamp.
4. Samples are written until `endMs`.
5. `MediaMuxer` finalizes the file, updating the duration in the header.

### Multi-Clip Merging
- Validates track compatibility (codecs must match for lossless concatenation).
- Shifts sample PTS values by the cumulative duration of previous segments to ensure continuity.

### Smart Silence Detection
1. ViewModel triggers `findSilence` using the extracted `waveformData`.
2. `DetectionUtils` identifies contiguous regions below the amplitude threshold.
3. `VideoEditingActivity` receives `silencePreviewRanges` and renders them on `CustomVideoSeeker`.
4. User confirms, and detected ranges are converted into `DISCARD` segments.

## 5. Development & CI
- **Testing**: 
    - JVM: `./gradlew test` (Robolectric for Engine/ViewModel).
    - Android: `./gradlew connectedAndroidTest` (Espresso for UI/Timeline).
- **Scripts**: `dev-scripts/` contains helpers for cleaning, pushing debug builds, and viewing logs.
- **CI**: GitHub Actions (`release.yml`) builds and signs production APKs on tag push.

## 6. Roadmap
1. **Smart Cut (v2.0)**: Use `MediaCodec` to decode/re-encode only the first/last GOP of a cut for true frame-accuracy while keeping the rest lossless.
2. **AI Tools**: Integration of on-device ML for automatic scene change detection.
3. **Task Orchestration**: Migrate export to `WorkManager` for background processing.

## 7. Context7 Library IDs
Use these IDs for documentation queries:
- `/androidx/media` (Media3/ExoPlayer)
- `/kotlin/kotlinx.coroutines` (Concurrency)
- `/androidx/datastore` (Preferences)
- `/material-components/material-components-android` (UI)
