# Lossless Video Cut

Lossless Video Cut is a lightweight, open-source Android application designed for **fast, lossless video trimming**. By leveraging native Android APIs (`MediaExtractor` and `MediaMuxer`), it avoids re-encoding the video stream, preserving the original quality and ensuring lightning-fast processing speeds.

## Features

- **Lossless Trim**: Instantly cut videos at keyframe boundaries without losing any quality.
- **Keyframe Visualization**: Automatically probles and displays keyframe (Sync Frame) positions on the timeline to help you make precise cuts.
- **Pure Native SDK**: Completely removed heavy FFmpeg dependencies, resulting in a significantly smaller APK size (~20MB) and better performance.
- **Media3 Integration**: Built on top of the modern `androidx.media3` (ExoPlayer) stack for robust playback.
- **Modern UI**: A clean, minimalist interface for quick editing tasks.

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

Unlike traditional video editors that transcode the entire clip, Lossless Video Cut identifies **Sync Frames** (I-frames) in your video. 

1. **Probing**: The app scans the video for keyframes.
2. **Visualizing**: These keyframes are displayed on the seek bar as white ticks.
3. **Muxing**: When you trim, the app extracts the encoded samples and remuxes them into a new container. No decompression or re-compression occurs.

## Permissions

The app requires minimal permissions to function:

- **READ_MEDIA_VIDEO**: Required on Android 13+ to access video files.
- **READ_EXTERNAL_STORAGE**: Required on older Android versions.
- **WRITE_EXTERNAL_STORAGE**: Required on older versions to save output.

## Developer Guide

For detailed information on the codebase, architecture, and future roadmap, please refer to [AGENTS.md](AGENTS.md).

## Contributing

Contributions are welcome! Whether it's bug fixes, performance improvements, or new features (like smart-rendering for precise cuts), feel free to open a PR.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.