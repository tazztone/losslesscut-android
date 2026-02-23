---
name: project-skill-creator
description: Recommends specialized skills to create for the Lossless Video Cut project based on its codebase (Android, Kotlin, Media3). Use when the user wants to enhance the Gemini CLI's capabilities for this specific project.
---

# Project Skill Creator

## Overview

This skill provides a list of recommended specialized skills tailored for the Lossless Video Cut project and general engineering excellence. These skills help transform Gemini CLI into a more efficient developer for video processing, Android UI, and high-quality software engineering.

## Recommended Project-Specific Skills

These skills are tailored specifically to the technologies used in this codebase:

### 1. `android-native-media-processing`
- **Description**: Specialized knowledge of Android `MediaExtractor`, `MediaMuxer`, and `Media3/ExoPlayer` for lossless operations.
- **Triggers**: When the user needs to cut video without re-encoding, map streams, or handle GOP-aware extraction.
- **Contents**: 
  - `references/`: Documentation on sample-accurate extraction, PTS shifting, and codec-specific flags.
  - `assets/`: Boilerplate for `MediaMuxer` loops and track validation.

### 2. `android-material3-ui`
- **Description**: Guidelines for implementing Material Design 3 and custom View accessibility.
- **Triggers**: When the user asks to update the UI, create new layouts, or improve `CustomVideoSeeker`.
- **Contents**:
  - `references/`: MD3 color palettes, typography standards, and `ExploreByTouchHelper` patterns.
  - `assets/`: XML layout templates and reusable UI styles.

### 3. `kotlin-coroutines-expert`
- **Description**: Best practices for asynchronous programming with Coroutines, StateFlow, and SharedFlow.
- **Triggers**: When handling background video processing, progress updates, or complex state transitions in the ViewModel.
- **Contents**:
  - `references/`: Dispatcher usage, lifecycle-aware scopes, and error handling patterns for long-running I/O.

### 4. `gradle-optimizer`
- **Description**: Strategies for optimizing Gradle build times and managing Version Catalogs (`libs.versions.toml`).
- **Triggers**: When the build is slow, dependency conflicts arise, or `build.gradle` needs refactoring.
- **Contents**:
  - `scripts/`: Shell scripts for cleaning build caches or analyzing dependency trees.

### 5. `android-test-automation`
- **Description**: Expertise in writing Robolectric (Unit/Engine) and Espresso (UI) tests.
- **Triggers**: When adding new features or fixing bugs to ensure quality.
- **Contents**:
  - `assets/`: Test boilerplate for Activities and ViewModels.
  - `references/`: Testing patterns for ViewModel and Repository layers.

## Recommended General Engineering Skills

These skills are valuable for any project to maintain high code quality and consistency:

### 6. `clean-code-reviewer`
- **Description**: Enforces high-quality code standards, naming conventions, and SOLID principles.
- **Triggers**: Before committing code or when asked to review a refactor.
- **Contents**:
  - `references/`: Project-specific naming conventions and architectural boundaries (MVVM).

### 7. `git-pr-workflow`
- **Description**: Manages high-quality commit messages, PR descriptions, and branch management.
- **Triggers**: When preparing to commit, merge, or document changes.
- **Contents**:
  - `references/`: Conventional Commits standard and PR templates.

### 8. `doc-expert`
- **Description**: Specialized in keeping `README.md`, `AGENTS.md`, and API documentation up to date.
- **Triggers**: After implementing a new feature or changing architectural patterns.
- **Contents**:
  - `references/`: Documentation templates and style guide.

## Workflow: How to create these skills

1. Choose a skill from the list above.
2. Activate the built-in `skill-creator` to follow the creation process.
3. Use `node init_skill.cjs` to scaffold the new skill.
4. Populate it with project-specific resources.
