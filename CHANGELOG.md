# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-02-23

### Added
- **Build System**: Fully migrated to Gradle Kotlin DSL (`.gradle.kts`) and AGP 9.0 (`c778297`).
- **UI Customization**: Added accent color customization and improved theme support (`e1a7924`).
- **Play/Pause Control**: Added play/pause control to the video player (`83f12a3`).

### Changed
- **Documentation**: Updated README banner and added screenshots (`00ab181`).
- **Internal Docs**: Updated `AGENTS.md` and `README.md` for MVVM architecture and MediaStore (`8772c83`, `6921cd3`).
- **State Preservation**: Refreshed documentation and improved orientation state preservation (`07011b5`).
- **CI/CD**: Improved release workflow with conditional keystore handling (`0ebdccb`).

### Fixed
- **Tests**: Improved test coverage and resolved test failures (`5b0830a`, `6921cd3`).
- **Keyframes**: Resolved keyframes state reset and final cleanups (`8943792`).
- **Rotation & Sync**: Fixed critical rotation loss and A/V sync issues (`83f12a3`).
- **Lint**: Resolved lint errors (`6921cd3`).

### Security
- **Sentinel**: Fixed exported `VideoEditingActivity` visibility [MEDIUM] (`dadfdd5`).

## [1.0.0] - Early 2026
- Initial release.
