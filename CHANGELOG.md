# Changelog

### Late July 2026: Settings Modal Overhaul & Localization Support
* **App Localization**: Added in-app language selection (System Default, English, Deutsch) using `AppCompatDelegate.setApplicationLocales()` for runtime language switching.
* **MVVM Settings Architecture**: Introduced `SettingsViewModel` with reactive state management, decoupling preferences and cache operations from Compose UI components.
* **Layout Jump & Drag Fixes**: Resolved initial bottom sheet snap/jump on launch and isolated vertical list scrolling from bottom sheet drag-to-dismiss gestures.
* **Debounced Slider I/O**: Debounced continuous slider updates during dragging to eliminate unnecessary DataStore disk writes and UI jank.
* **Accessibility & Usability Polish**: Added TalkBack accessibility semantics to accent color pickers, formatted SAF storage paths into clean human-readable directory trees, and added user feedback for cache clearing.

### July 2026: Timeline Actions & Persistent Analysis Cache
* **Segment Context Actions**: Long-press a kept timeline segment to reveal anchored delete and split icons at the press position with a vertical split line indicator, including when the playhead overlaps the segment; selected segments remain clearly highlighted.
* **Top-Anchored Segment Handles**: Positioned segment drag handle circles at the top of the timeline view to prevent interference with Android system gesture navigation and menu controls.
* **Clip Reset**: Added a confirmation-protected action to reset the current clip to one full KEEP segment; the operation participates in undo/redo.
* **Analysis Reuse**: Persisted waveform and visual-analysis results across sessions with clip/settings-aware cache keys.
* **Cache Governance**: Added configurable maximum cache size, retention age, usage reporting, and a confirmation-protected clear action.
* **Audit Hardening**: Preserved coroutine cancellation, restricted imported media to SAF content URIs, and removed obsolete broad external-storage permissions.
* **Test Relocation & Verification**: Relocated `LosslessEngineRealDeviceTest` into `:engine/src/androidTest` with module-local Hilt entry point and FileProvider setup, resolving Kotlin FIR compiler crashes during Android Lint analysis.

### Late February 2026: Architectural Purification & Domain Isolation
* **Domain Purity**: Converted `:core:domain` into a **pure JVM library**, stripping all Android SDK and Hilt dependencies. Established a clean business logic layer that is agnostic of the platform.
* **Engine Decoupling**: Fully isolated the `:engine` module by removing direct dependencies on `:core:data` and Media3. Introduced `IMediaFinalizer` abstraction to handle SAF post-processing.
* **Storage Governance**: Clarified and enforced storage rules in `AGENTS.md`, distinguishing between SAF-only external media and `java.io.File`-permissible internal storage.
* **UI Resilience**: Implemented `UiText` resolution via `:app`-level extension functions, ensuring the domain layer remains context-free.

### Late February 2026: Build Modernization, UI Polish & Advanced Workflows
* **Build System Overhaul**: Fully migrated from Groovy to Gradle Kotlin DSL and updated to AGP 9.0, improving build performance and type safety.
* **Player & Playback Controls**: Refactored player state handling into a dedicated `PlayerManager`. Added playback speed controls, pitch correction toggling, and nudge functionality for precise seeking. 
* **UI State & Accessibility**: Transitioned to an event-driven UI state management system. Introduced customizable accent colors, improved theme support, and expanded tooltips and accessibility labels.
* **Editing Enhancements**: Added redo stack functionality, multi-file sharing support via `SEND_MULTIPLE` intents, and streamlined adapter mechanisms with UUID-based selection.
* **Seeker Architecture**: Decomposed the monolithic `CustomVideoSeeker` into modular, highly-testable components for rendering, touch handling, and accessibility.
* **Timeline Visuals & Snapping**: Added color-coded "Ghost State" visualizations and interactive threshold previews for silence detection. Enforced mandatory, frame-accurate keyframe snapping in lossless mode.

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
