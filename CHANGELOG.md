# Changelog

All notable changes to **LosslessCut** are documented in this file.

## Latest Changes: Refactoring & Build Modernization
- **Build System**: Migrated the entire build system from Groovy to Gradle Kotlin DSL (`.gradle.kts`) and updated to AGP 9.0.
- **Player Controls**: Refactored player state handling from VideoEditingActivity to a dedicated `PlayerManager` class. Added playback speed controls with pitch correction toggle and frame-by-frame nudge functionality.
- **Architecture**: Introduced `RotationManager` and `ShortcutHandler` to decouple logic from the main activity. Updated `VideoEditingViewModel` to use Kotlinx Serialization.
- **Theming**: Added accent color customization (Cyan, Purple, Green, Yellow, Red, Orange) with persistent storage via DataStore.
- **MediaClipAdapter**: Migrated to `RecyclerView.Adapter` with UUID-based selection for robust drag-and-drop support.
- **Testing**: Improved test coverage for preferences, storage, and view models.

## Workflow & Usability Enhancements
- **Redo Functionality**: Added a redo stack and button to the video editing toolbar.
- **Action Buttons**: Introduced dedicated buttons for Export, Remux, and Metadata operations in the bottom action bar.
- **Launch Modes**: Implemented `MODE_CUT` (default), `MODE_REMUX` (pass-through muxing), and `MODE_METADATA` (rotation/metadata editing) to streamline specific tasks.
- **Silence Detection UI**: Replaced the silence detection dialog with an integrated overlay for better visual feedback.
- **Export Validation**: Prevent export when no segments are selected.

## Core Feature Expansion
- **Silence Detection**: Added a dedicated silence detection feature with adjustable thresholds and visual preview on the waveform.
- **Multi-Track Selection**: Added support for selecting specific video/audio tracks during export, with metadata display (language, title).
- **Custom Output**: Enabled custom output folder selection via the system document picker.
- **Session Restoration**: Implemented automatic session restoration to recover editing progress (clips, segments, zoom).
- **Intent Handling**: Added support for opening files via "Share" and "Open with" intents from other apps.
- **Snapshot Improvements**: Added toggle for PNG/JPEG format and improved snapshot extraction performance.

## UI Redesign & Playlist Management
- **Sidebar Navigation**: Reorganized the video editing interface with left (primary actions) and right (editing tools) sidebars for better accessibility.
- **Playlist Management**: Added a dedicated playlist container with drag-and-drop reordering, long-press removal, and a clear "Add Clip" button.
- **Multi-File Import**: Enabled importing multiple video/audio files simultaneously.
- **Lossless Merge**: Implemented functionality to merge multiple clips or segments into a single file with strict codec validation.
- **Timeline Polish**: Added timeline padding, improved time-to-coordinate conversion, and enhanced waveform visualization (noise floor suppression, peak amplification).

## Foundation & Audio Support
- **Audio Support**: Added full support for lossless audio cutting and merging.
- **Waveform Rendering**: Implemented high-performance, cached audio waveform rendering.
- **Dependency Injection**: Migrated the entire app to **Hilt** for dependency injection.
- **Accessibility**: Added comprehensive accessibility support for the video timeline (TalkBack, keyboard navigation).
- **Native Engine Rewrite**: Pivoted from FFmpeg to Android's native `MediaExtractor` and `MediaMuxer` APIs, significantly reducing app size.
- **MVVM Architecture**: Adopted MVVM pattern with Jetpack ViewModels and Repository layer.
