# Changelog

All notable changes to this project will be documented in this file.

## [1.1.1] - 2026-02-23

### Added
- **Gradle Kotlin DSL**: Fully migrated all build scripts to Kotlin DSL (`.gradle.kts`) for better type safety and maintenance.
- **AGP 9.0 Built-in Kotlin**: Native support for Android Gradle Plugin 9.0's built-in Kotlin, reducing plugin boilerplate.
- **Standalone Icon Generator**: Extracted icon processing logic into a standalone Java utility and bash script (`dev-scripts/`).
- **CI Linting**: Integrated automated linting into the GitHub Actions workflow.

### Fixed
- **Media3 UnsafeOptInUsage**: Resolved all lint errors related to unstable API usage via refined `@OptIn` scoping.
- **ViewModel Synchronization**: Fixed critical syntax and concurrency errors in `VideoEditingViewModel` initialization and session restoration.
- **Activity Context Leak**: Resolved an incorrect context reference in `VideoEditingActivity` state observers.

## [1.1.0] - 2026-02-23

## [1.0.0] - Early 2026
- Initial release with basic lossless cutting and waveform extraction.
