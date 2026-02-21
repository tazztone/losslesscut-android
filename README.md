# LosslessCut

LosslessCut is a lightweight, open-source Android application designed for **fast, lossless video trimming**. By leveraging native Android APIs (`MediaExtractor` and `MediaMuxer`), it avoids re-encoding the video stream, preserving the original quality and ensuring lightning-fast processing speeds.

## Features

- **Desktop-Class NLE Timeline**: A fully interactive timeline supporting multi-segment editing (Split, Discard, and Drag).
- **Pinch-to-Zoom & Pan**: Smoothly zoom in up to 20x for frame-perfect edits and pan across long videos with ease.
- **Haptic Keyframe Snapping**: Tactile feedback (Clock Tick) whenever a segment boundary snaps to a keyframe in Lossless Mode.
- **Undo/Redo History**: Robust edit history managed by a ViewModel stack, allowing you to revert segment changes instantly.
- **Multi-Segment Export**: Split a video into multiple "KEEP" regions and export them as individual clips in one click.
- **Lossless Trim & Extraction**: Native `MediaExtractor` and `MediaMuxer` logic ensures lightning-fast processing without re-encoding.
- **MVVM Architecture**: Clean state management using `ViewModel` and `StateFlow` for a reactive UI.
- **Scoped Storage & Modern SDK**: Modern Android storage handling saving to `Movies/LosslessCut` without heavy FFmpeg dependencies.

## Getting Started

### Prerequisites

- Android Studio Koala or newer
- Android SDK (Min API 26, Target API 35)

### Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/tazztone/lossless-video-cut.git
   ```
   
2. **Open the project in Android Studio**:
   - Launch Android Studio and select "Open."
   - Navigate to the cloned directory and select it.

3. **Build the project**:
   - Let Gradle sync and download dependencies.
   - Run `./gradlew assembleRelease` to generate a production build.

## How it Works

Unlike traditional video editors that transcode the entire clip, LosslessCut identifies **Sync Frames** (I-frames) in your video.

1. **Probing**: The app scans the video for keyframes.
2. **Visualizing**: These keyframes are displayed on the seek bar as white ticks.
3. **Muxing**: When you trim, the app extracts the encoded samples and remuxes them into a new container. No decompression or re-compression occurs.

## Permissions

The app follows modern Android security best practices:

- **READ_MEDIA_VIDEO**: Required on Android 13+ to browse video files.
- **Scoped Storage**: On modern Android versions, the app uses `MediaStore` to save trimmed videos to the public `Movies/LosslessCut` folder, eliminating the need for `WRITE_EXTERNAL_STORAGE` for output.

## Developer Guide

For detailed information on the codebase, architecture (MVVM), and future roadmap, please refer to [AGENTS.md](AGENTS.md).

## Contributing

Contributions are welcome! Whether it's bug fixes, performance improvements, or new features (like smart-rendering for precise cuts), feel free to open a PR.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
