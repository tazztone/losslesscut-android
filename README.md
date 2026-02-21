# LosslessCut

LosslessCut is a lightweight, open-source Android application designed for **fast, lossless video and audio trimming**. By leveraging native Android APIs (`MediaExtractor` and `MediaMuxer`), it avoids re-encoding media streams, preserving the original quality and ensuring lightning-fast processing speeds.

## Features

- **Lossless Video & Audio Trimming**: Native `MediaExtractor` and `MediaMuxer` logic ensures lightning-fast processing without re-encoding for both video (`.mp4`) and audio (`.m4a`) files.
- **Desktop-Class NLE Timeline**: A fully interactive timeline supporting multi-segment editing (Split, Discard, and Drag).
- **Pinch-to-Zoom & Pan**: Smoothly zoom in up to 20x for frame-perfect edits and pan across long media with ease.
- **Audio-Only Mode**: Automatically detects audio files, providing a dedicated interface with an audio placeholder and simplified tools.
- **Haptic Keyframe Snapping**: Tactile feedback (Clock Tick) whenever a segment boundary snaps to a keyframe in Lossless Mode (for video).
- **Undo/Redo History**: Robust edit history managed by a ViewModel stack, allowing you to revert segment changes instantly.
- **Multi-Segment Export**: Split media into multiple "KEEP" regions and export them as individual clips in one click.
- **MVVM Architecture**: Clean state management using `ViewModel` and `StateFlow` for a reactive UI.
- **Scoped Storage & Modern SDK**: Modern Android storage handling saving to `Movies/LosslessCut` or `Music/LosslessCut` without heavy FFmpeg dependencies.

## Getting Started

### Prerequisites

- Android Studio Koala or newer
- Android SDK (Target API 35)

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

Unlike traditional editors that transcode the entire clip, LosslessCut manipulates the media container directly.

1. **Probing**: The app scans the file for tracks and keyframes (for video).
2. **Visualizing**: Media is displayed on a zoomable timeline. For video, keyframes are shown as white ticks for snapping.
3. **Muxing**: When you trim, the app extracts the encoded samples and remuxes them into a new container (`.mp4` for video, `.m4a` for audio). No decompression or re-compression occurs.

## Permissions

The app follows modern Android security best practices:

- **READ_MEDIA_VIDEO & READ_MEDIA_AUDIO**: Required on Android 13+ to browse media files.
- **Scoped Storage**: On modern Android versions, the app uses `MediaStore` to save trimmed media to the public `Movies/LosslessCut` or `Music/LosslessCut` folders, eliminating the need for `WRITE_EXTERNAL_STORAGE` for output.

## Developer Guide

For detailed information on the codebase, architecture (MVVM), and future roadmap, please refer to [AGENTS.md](AGENTS.md).

## Contributing

Contributions are welcome! Whether it's bug fixes, performance improvements, or new features (like smart-rendering for precise cuts), feel free to open a PR.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
