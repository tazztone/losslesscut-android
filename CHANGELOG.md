# Changelog

All notable changes to **LosslessCut** are documented in this file.

## ✨ New Features

### Multi-File Workflow & Merging
- **Batch Processing**: Import multiple video or audio files simultaneously.
- **Lossless Merge**: Concatenate multiple clips or segments into a single file without re-encoding (requires compatible codecs).
- **Playlist Management**: Reorder clips via drag-and-drop or remove them with a long-press gesture.
- **Smart Validation**: strict codec checks to prevent incompatible track mixing during merges.

### Advanced Editing Tools
- **Silence Detection**: Automatically detect and remove silent segments with adjustable thresholds and minimum duration. Includes a visual preview on the waveform.
- **Launch Modes**:
  - **Cut Mode**: Full NLE editor with timeline and segmentation.
  - **Remux Mode**: Quick pass-through for fixing container issues without editing.
  - **Metadata Mode**: Modify rotation/orientation flags without re-encoding.
- **Precise Cutting**: "Smart Split" snaps to the nearest keyframe for optimal cut quality.
- **Session Restoration**: Automatically saves and restores your editing session (clips, segments, zoom level) upon reopening.

### Audio Support
- **Lossless Audio Cutting**: Full support for trimming and merging audio files (M4A, AAC, etc.).
- **Waveform Visualization**: High-performance, cached audio waveform rendering with noise floor suppression and peak amplification.
- **Pitch Correction**: Toggle pitch correction when adjusting playback speed (0.25x - 4.0x).

### Export & Sharing
- **Custom Output**: Choose specific output folders via the system document picker.
- **Format Options**: Toggle between JPEG and PNG for frame snapshots.
- **Intent Handling**: Open media directly from other apps via "Share" or "Open with".
- **Track Selection**: Select specific video/audio tracks to export when multiple are available.

## 🎨 UI & UX Improvements

### Material 3 Redesign
- **Modern Interface**: Complete UI overhaul using Material 3 guidelines.
- **Theming**: Dynamic accent color picker (Cyan, Purple, Green, Yellow, Red, Orange) and dark mode support.
- **Responsive Layouts**: Optimized layouts for both Portrait (bottom controls) and Landscape (sidebar navigation).

### Interactive Timeline
- **Gestures**: Pinch-to-zoom with animated indicators, drag-to-seek, and precise handle manipulation.
- **Visual Feedback**: Haptic feedback on snapping/interactions, animated playhead, and dynamic time labels.
- **Accessibility**: Full TalkBack support for the timeline, including virtual nodes for playhead and segment handles.

### Player Controls
- **Playback Speed**: dedicated controls for variable playback speed.
- **Frame Stepping**: Precision "nudge" controls for frame-by-frame seeking.
- **Overlays**: Minimalist player overlays for distraction-free viewing.

## 🚀 Core Engine & Performance

### Native Media Engine
- **FFmpeg Removal**: Replaced bulky FFmpeg dependency with Android's native `MediaExtractor` and `MediaMuxer`, reducing app size by ~100MB.
- **Direct Buffering**: Implemented `ByteBuffer.allocateDirect` for zero-copy data transfer during muxing.
- **Keyframe Handling**: Optimized keyframe probing (O(1) lookup) and caching for instant seeking.

### Stability & Optimization
- **Rotation Handling**: Hardware-accelerated rotation preview using `TextureView` transforms; metadata-based rotation preservation during export.
- **Concurrency**: Moved heavy tasks (waveform extraction, frame probing) to background coroutines with proper cancellation handling.
- **Memory Management**: Implemented LRU caching for waveforms and keyframes to prevent OOM errors.

## 🏗 Architecture & Refactoring

### Modern Android Stack
- **MVVM Architecture**: Clean separation of concerns with Jetpack ViewModels and Repository pattern.
- **Dependency Injection**: Full migration to **Hilt** for robust dependency management.
- **Data Persistence**: Migrated from SharedPreferences to **Jetpack DataStore** for type-safe, reactive preferences.
- **Build System**: Converted all build scripts to **Gradle Kotlin DSL** and updated to AGP 9.0.

### Code Quality
- **Testing**: Added comprehensive test suites using **Robolectric** (Unit) and **Espresso** (UI).
- **Linting**: Enforced stricter lint checks and clean code practices.
- **Security**: Removed hardcoded secrets and implemented secure file handling via `FileProvider`.

## 🐛 Bug Fixes
- Fixed A/V sync drift by correctly calculating timestamp offsets and including initial keyframes.
- Resolved issues with video rotation being lost after export.
- Fixed crashes on Android 10+ related to scoped storage and URI handling.
- Corrected track mapping logic to prevent audio/video mismatches during multi-clip merges.
- Prevented export of invalid (zero-length) segments.
