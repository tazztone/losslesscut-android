# Developer Guide & Knowledge Base (`AGENTS.md`)

## Project Overview
**Name**: Lossless Video Cut
**Package**: `com.tazztone.losslesscut`
**Tech Stack**: Kotlin, Android SDK (API 31+ target), Media3 (ExoPlayer), Lottie.

This project is a lightweight, open-source Android application designed for **lossless video trimming**. It avoids re-encoding the video stream, preserving original quality and ensuring near-instant processing speeds.

## Core Architecture

### 1. Engine Layer (`LosslessEngine.kt`)
The heart of the application. It replaces the heavy FFmpeg dependency with native Android APIs.

*   **Logic**: Uses `MediaExtractor` to read encoded sample data and `MediaMuxer` to write it to a new container (MP4).
*   **Key Functions**:
    *   `probeKeyframes(context, uri)`: Scans the video file to identify **Sync Frames** (I-frames). These timestamps are crucial for accurate cutting, as we can only cut at these points without re-encoding.
    *   `executeLosslessCut(context, uri, outputFile, startMs, endMs)`: Performs the actual trim operation. It reads samples from the input, filters based on the time range, adjusts timestamps (PTS/DTS) to start at zero, and writes to the output file.
*   **Constraints**: Cuts are currently limited to Keyframe boundaries (GOP structure).

### 2. UI Layer (`VideoEditingActivity.kt`)
Handles user interaction, video playback, and timeline visualization.

*   **Player**: Uses `androidx.media3.exoplayer` for playback.
*   **Timeline**: `CustomVideoSeeker` draws the timeline and overlays white ticks representing keyframes found by the engine.
*   **Frame Extraction**: `FrameAdapter` uses `MediaMetadataRetriever` to generate thumbnails for the seek bar.
*   **State Management**: Manages `Loader` visibility, player lifecycle, and handles the "Lossless/Precise" toggle state.

### 3. Entry Point (`MainActivity.kt`)
*   Provides a clean landing page with a "Select Video" button.
*   Handles Permission requests (Storage/Media).
*   Displays "About" dialog with licensing info.

## Key Design Decisions

*   **Native SDK vs FFmpeg**: Switched from `FFmpegKit` to `MediaExtractor`/`MediaMuxer` to reduce APK size (~100MB -> ~20MB) and avoid GPL licensing complexities.
*   **Lossless-Only (v1.0)**: The initial release focuses purely on speed and quality. "Precise Mode" (re-encoding) is disabled in the UI until a smart-rendering implementation (re-encoding only the cut boundaries) is ready.
*   **Media3 Migration**: Upgraded from legacy ExoPlayer to the modern Jetpack Media3 library for better long-term support.

## Developer Workflow

### Building
*   Standard Android Gradle build.
*   `./gradlew assembleRelease` or `assembleDebug`.
*   **Note**: Ensure `local.properties` contains your SDK path if not set in environment.
*   **Icons**: Run `./gradlew updateAppIcons` to regenerate `res/mipmap-*` assets from `logo.png` in the project root.

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

1.  **Smart Cut (Precise Mode)**: Implement "Smart Rendering".
    *   Decode and re-encode only the frames between the cut point and the nearest keyframe.
    *   Stream copy the rest.
    *   Requires `MediaCodec` concatenation logic.
2.  **Audio Fixes**: Ensure audio tracks are correctly offset when video tracks are trimmed.
3.  **Merge Functionality**: Re-implement video merging using `MediaMuxer` (requires resolution/codec matching) or `MediaCodec` transcoding.
4.  **UI Polish**: Enhanced timeline zooming for frame-accurate selection.

## Code Style & Conventions
*   **Kotlin**: Use idiomatic Kotlin (coroutines for async work, extension functions).
*   **Async**: UI operations on Main thread, heavy lifting (probing, cutting) on IO dispatchers.
*   **Permissions**: Handle runtime permissions gracefully (especially Android 13+ media permissions).
