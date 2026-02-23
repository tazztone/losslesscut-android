# Changelog

### Late February 2026: Build Modernization, UI Polish & Advanced Workflows
* **Build System Overhaul**: Fully migrated from Groovy to Gradle Kotlin DSL and updated to AGP 9.0, improving build performance and type safety.
* **Player & Playback Controls**: Refactored player state handling into a dedicated `PlayerManager`. Added playback speed controls, pitch correction toggling, and nudge functionality for precise seeking. 
* **UI State & Accessibility**: Transitioned to an event-driven UI state management system. Introduced customizable accent colors, improved theme support, and expanded tooltips and accessibility labels.
* **Editing Enhancements**: Added redo stack functionality, multi-file sharing support via `SEND_MULTIPLE` intents, and streamlined adapter mechanisms with UUID-based selection.

### Mid-Late February 2026: Specialized Modes & Automation Features
* **Dedicated Launch Modes**: Introduced `MODE_CUT` (full editor), `MODE_REMUX` (full-file pass-through), and `MODE_METADATA` (rotation/metadata overrides without timeline overhead).
* **Silence Detection**: Implemented an automated silence detection tool with visual previews in the waveform and customizable duration/threshold sliders to automatically discard silent segments.
* **Export & File Management**: Added custom output folder selection via Android's document picker, session restoration for reopening previous edits, and multi-track selection for targeted video/audio export.
* **Engine Optimizations**: Improved keyframe handling, codec validation, and continuous audio waveform extraction with disk caching and noise floor suppression.

### Mid February 2026: Multi-Clip Merging & Desktop-Class Timeline
* **Playlist & Multi-File Assembly**: Added support to import, append, and drag-and-drop reorder multiple media files. Introduced lossless merging of multiple clips with continuous PTS alignment.
* **Timeline Overhaul**: Redesigned the video editing layout with sidebar navigation. Added pinch-to-zoom animations, dynamic timeline padding, and animated drag handles for segment adjustment.
* **Lossless Audio Support**: Expanded the core engine to support precise lossless audio cutting alongside video.
* **UX Refinements**: Introduced haptic feedback for timeline snapping, auto-pan navigation, and an unsaved changes confirmation dialog.

### Early February 2026: Architecture Rebuild & Rebranding
* **Native Android SDK Migration**: Removed the heavy FFmpegKit dependency in favor of a fast, native Android `MediaExtractor`/`MediaMuxer` engine (LosslessEngine), drastically reducing app size.
* **App Rebranding**: Officially renamed the project to "LosslessCut".
* **Modernization**: Transitioned to an MVVM architecture using Jetpack DataStore for preferences and Hilt for dependency injection.
* **Infrastructure Setup**: Added comprehensive CI/CD workflows via GitHub Actions for automated builds, linting, and Play Store release generation. 

### Late 2024 - 2025: Initial Prototypes & Foundational Tools
* **Early Export Features**: Prototyped video merging and basic trimming functionality.
* **Visual Editing Utilities**: Added tools for aspect ratio video cropping (16:9, 9:16, 1:1) and adding text overlays to video frames.
* **Foundational UI**: Created initial loading screens, timeline frame extraction, and error handling for metadata retrieval.