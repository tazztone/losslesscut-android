# Developer Guide & Knowledge Base (`AGENTS.md`)

## Project Overview
**Name**: Lossless Video Cut
**Package**: `com.tazztone.losslesscut`
**Tech Stack**: Kotlin, Android SDK (API 31+ target), Media3 (ExoPlayer), Lottie.

This project is a lightweight, open-source Android application designed for **lossless video trimming**. It avoids re-encoding the video stream, preserving original quality and ensuring near-instant processing speeds.

## Core Architecture (v2.0 MVVM)

### 1. Engine Layer (`LosslessEngine.kt`)
The heart of the application. It replaces the heavy FFmpeg dependency with native Android APIs.

*   **Logic**: Uses `MediaExtractor` to read encoded sample data and `MediaMuxer` to write it to a new container (MP4).
*   **Key Functions**:
    *   `probeKeyframes(context, uri)`: Scans the video file to identify **Sync Frames** (I-frames). These timestamps are crucial for accurate cutting, as we can only cut at these points without re-encoding.
    *   `executeLosslessCut(context, uri, outputUri, startMs, endMs)`: Performs the actual trim operation. Returns a `Result<Uri>` for robust error handling.
*   **Resource Management**: Uses Kotlin's `.use {}` pattern for `MediaExtractor` and `MediaMuxer` to ensure resources are always released, even on failure.
*   **Constraints**: Cuts are currently limited to Keyframe boundaries (GOP structure).

### 2. ViewModel Layer (`VideoEditingViewModel.kt`) [NEW in v2.0]
Centralizes business logic and manages the UI state.
*   **State Management**: Uses `StateFlow<VideoEditingUiState>` to represent Loading, Success, and Error states.
*   **Threading**: Leverages `viewModelScope` and `Dispatchers.IO` for non-blocking engine operations.
*   **Storage**: Coordinates with `StorageUtils` to handle `MediaStore` URIs for output files.

### 3. UI Layer (`VideoEditingActivity.kt`)
Observer-based activity that handles user interaction and timeline visualization.
*   **Observation**: Observes the `ViewModel` state flow and updates UI components (Player, Seekbar, Loading screen) accordingly.
*   **Player**: Uses `androidx.media3.exoplayer` for playback.
*   **Timeline**: `CustomVideoSeeker` draws the timeline and overlays white ticks representing keyframes found by the engine.

### 4. Storage Utility (`StorageUtils.kt`) [NEW in v2.0]
*   Handles modern Android `MediaStore` integration.
*   Enables Scoped Storage compliance by creating output URIs in public folders (e.g., `Movies/LosslessCut`) without requiring legacy storage permissions.

## Key Design Decisions

*   **MVVM Migration (v2.0)**: Transitioned from a monolithic Activity to MVVM to separate playback logic from file processing, making the codebase more testable (Robolectric) and maintainable.
*   **MediaStore over File API**: Adopted `MediaStore` for all output operations to ensure seamless operation on Android 11+ and avoid the deprecated `Environment.getExternalStoragePublicDirectory`.
*   **Result Types**: Switched engine APIs from `Boolean` to `Result<T>` to provide meaningful error messages to the UI.
*   **Native SDK vs FFmpeg**: Maintained the decision to use `MediaExtractor`/`MediaMuxer` to keep APK size low (~20MB).

## Developer Workflow

### Building & Testing
*   Standard Android Gradle build.
*   **Unit Tests**: Run `./gradlew test` to execute JVM-based unit tests (including Robolectric engine tests).
*   **Instrumented Tests**: Run `./gradlew connectedAndroidTest` to verify UI on a device/emulator.

## CI/CD (GitHub Actions)

The project includes a `release.yml` workflow that automatically builds and publishes the app when a tag starting with `v` (e.g., `v1.0.0`) is pushed.

### Secrets Required
Configure the following secrets in your GitHub Repository settings:
*   `ANDROID_KEYSTORE_BASE64`: Base64 encoded content of your `keystore.jks`.
*   `ANDROID_KEYSTORE_PASSWORD`: Keystore password.
*   `ANDROID_KEY_ALIAS`: Key alias.
*   `ANDROID_KEY_PASSWORD`: Key password.
*   `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`: Service account JSON for Play Store publishing.

### Debugging
*   **Logs**: Filter Logcat by `LosslessEngine`, `VideoEditingActivity`, or `ExoPlayer`.
*   **Visual Debugging**: Keyframes are visualized as white ticks on the timeline. If these are missing, `probeKeyframes` might be failing or the video has a strange GOP structure.

## Future Roadmap (v2.0+)

1.  **Phase 1 [COMPLETED]**: Architectural Foundation (MVVM), MediaStore integration, and Engine robustness.
2.  **Phase 2 [DEFERRED]**: Smart Cut (Precise Mode), Video Merging, and Overlays.
    *   Precise Trim: Decode and re-encode only the frames between the cut point and the nearest keyframe.
    *   Merging: Sequence multiplexing of multiple MP4 sources.

## Code Style & Conventions
*   **Kotlin**: Use idiomatic Kotlin (coroutines for async work, extension functions).
*   **Async**: UI operations on Main thread, heavy lifting (probing, cutting) on IO dispatchers.
*   **Permissions**: Handle runtime permissions gracefully (especially Android 13+ media permissions).
