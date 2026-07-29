# LosslessCut (MP4)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=26)

**LosslessCut (MP4)** is a high-performance, open-source Android application for **instant, lossless media trimming and merging**. By manipulating compatible codecs (**H.264, H.265, AAC**) directly, it preserves original quality and processes files at lightning speed, remuxing them into standard `.mp4` and `.m4a` containers without re-encoding.

<p align="center">
  <img src="docs/images/ic_banner.webp" width="400" alt="LosslessCut Banner">
</p>

## ✨ Features

- 🚀 **Zero Quality Loss**: Trims and merges video (`.mp4`) and audio (`.m4a`) using native `MediaExtractor` and `MediaMuxer`—no transcoding involved.
- 🎞️ **Pro Timeline**: Desktop-class NLE timeline supporting multi-segment editing (Split, Discard, and Drag).
- 🔍 **Precision Seeking**: Zoom up to 20x for frame-accurate edits.
- 🧲 **Keyframe Snapping**: Mandatory, strict keyframe snapping in lossless mode ensures frame-perfect cuts. Features haptic feedback and visual snapping.
- 📱 **Adaptive UI**: Ergonomic landscape sidebars and a unified floating player overlay for maximum screen real estate.
- ➕ **Smart Playlist**: Inline "Add Media" shortcut and intelligent duplicate detection on import.
- 🎵 **Audio-Only Mode**: Intelligent UI adaptation for audio files with waveform visualization.
- 📦 **Batch Export & Merge**: Export multiple "KEEP" regions as individual clips or merge them into a single seamless file in one pass.
- 🎼 **Smart Audio Extraction**: Automatically saves audio-only exports (when video is unchecked) as lossless `.m4a` files in the `Music` folder.
- ♿ **Accessibility First**: Comprehensive screen reader support via virtual view hierarchies (`ExploreByTouchHelper`).
- 🔄 **Non-Destructive Workflow**: Full **Undo/Redo** stack for all segment operations.
- 🤖 **Smart Cut (v2.0)**: Unified, tabbed interface combining **Silence Cut** and **Visual Detection**.
    - **Silence**: Automated, parameterized removal of quiet sections with interactive "Ghost State" visualizations and live savings previews.
    - **Visual**: Algorithm-driven segment detection for **Scene Changes**, **Black Frames**, **Freeze Frames**, and **Blur Quality**.
- ⏸️ **Intelligent Focus**: **Auto-pause** playback when opening settings, export options, or silence detection to prevent missing content.
- ✨ **Contextual UX**: Seamless, auto-dismissing timeline hints and haptic feedback for a clean, professional interface.
- 💾 **Project Persistence**: Seamless session restoration—resume your edits exactly where you left off.
- 📂 **Custom Output Path**: Flexible export folder selection via Storage Access Framework (SAF).
- 🔄 **Remux & Convert**: Change container formats (e.g., MKV to MP4) instantly without re-encoding.
- 🏷️ **Quick Metadata Fix**: Correct video orientation and rotation flags in seconds.
- 🏗️ **Clean Architecture**: Context-free ViewModels and a centralized Repository pattern for maximum maintainability.
- 🧊 **Format Compatibility**: Optimized for modern codecs and containers (MP4, AAC). See [Technical Reference](#7-format-compatibility) for details.

## 📸 Screenshots

<p align="center">
  <img src="docs/images/screenshot_landscape.webp" width="64%" alt="Main Editor UI">
  <img src="docs/images/screenshot_portrait.webp" width="28%" alt="Portrait Mode">
</p>

## 🛠️ How it Works

Unlike traditional video editors that decode and re-encode every frame, LosslessCut operates at the **container level**:

1. **Probe**: Scans the file structure to identify stream metadata and track availability.
2. **Visualize**: Renders a zoomable timeline where keyframes are marked as snapping points.
3. **Mux**: During export, the app extracts the original encoded samples between cut points and remuxes them into a new container. If the video track is excluded, it smartly routes to an audio-only `.m4a` container to preserve original quality.

## 🚀 Getting Started

### Prerequisites
- Android Studio Koala+
- Android SDK 36 (Target) / 26 (Min)

### Development
```bash
# Clone the repo
git clone https://github.com/tazztone/lossless-video-cut.git

# Generate icons (Consolidated tool)
java scripts/dev-scripts/asset-generate-icons.java

# Build debug APK using Gradle Kotlin DSL
./gradlew assembleDebug
```

## 🤝 Contributing

We follow **MVVM + Clean Architecture** with a strict separation between UI, Domain, and Data layers.

- **Multi-Module**: Core logic belongs in `:core:domain`, `:core:data`, or `:engine`.
- **Use Cases**: All business logic MUST reside in Use Cases within the `:core:domain` module.
- **Workflow**: Create a feature branch for every change and ensure CI passes before opening a PR.
- **Code Style**: Follow standard Kotlin conventions and avoid "God Classes".

## 🛡️ Security Policy

### Reporting a Vulnerability

If you discover a security vulnerability within LosslessCut, please do not open a public issue. Instead, report it privately to the maintainers. We aim to respond to all reports within 48 hours and provide a fix or mitigation plan as soon as possible.

## 🔒 Permissions & Privacy
- **Privacy-First Model**: Removed all unnecessary runtime permissions (Notifications, Media Access). The app relies on the **Storage Access Framework (SAF)** for user-initiated file selection.
- **Scoped Storage**: Uses `MediaStore` to save results to `Movies/LosslessCut` (video) or `Music/LosslessCut` (audio extraction). 
- **Privacy**: 100% offline. No analytics, no tracking, no data collection.

## 🗺️ Roadmap

- [x] **Smart Cut (v2.0)**: Integrated advanced algorithm-driven visual detection (Scene, Black, Freeze, Blur) and unified it with Silence Cut.
- [ ] ~~**Task Orchestration**~~ (Shelved: Background orchestration not required for near-instant exports)
- [ ] **Advanced Tags**: Title, artist, and creation date editing.
- [x] **Architectural Enforcement**: Implemented Konsist testing to safeguard module boundaries in the CI pipeline.
- [x] **Domain Purification**: Extracted standard JVM domain module for maximum portability and testability.
- [x] **Metadata Tuning**: Quick rotation and orientation flag fixes.
- [x] **Remux Utility**: Instant container switching.
- [x] **Activity Decomposition**: Refactored major UI logic into specialized delegates.
- [x] **Modern Build**: Full migration to Gradle Kotlin DSL and AGP 9.0.

## 📚 Documentation Index

For detailed technical specifications, architecture blueprints, developer workflows, and privacy details, refer to:

- 🏗️ **[Architecture & Technical Reference](docs/architecture.md)**: System architecture, multi-module structure, component blueprints (`CustomVideoSeeker`, `LosslessEngine`, `VideoEditingViewModel`), key workflows, and format compatibility matrix.
- 🛠️ **[Contributing Guide](CONTRIBUTING.md)**: Development setup, PR checklist, and documentation for the `./scripts/dev-scripts/` automation suite.
- ⚙️ **[Build Tooling Matrix](docs/build-tooling.md)**: Stable toolchain versions (AGP, Gradle, Kotlin, KSP), standard flags, and modernization roadmap.
- 🔒 **[Privacy Policy](docs/privacy.html)**: Storage Access Framework (SAF) privacy model.

## 📄 License
Licensed under the **MIT License**. See [LICENSE](LICENSE) for details.
