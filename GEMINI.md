# Gemini Integration Guide

## Project Context
This project is a lossless video editor for Android, using Media3, MediaExtractor, and MediaMuxer.
It follows Clean Architecture with a multi-module Gradle setup.

## Module Responsibilities
- `:app`: UI (Fragments, Activities, ViewModels).
- `:core:domain`: Domain Layer (Use Cases, Repository/Engine Interfaces, Models).
- `:core:data`: Data Layer (Repository Implementations, Persistence, low-level Utils).
- `:engine`: Media Processing Layer (Lossless Engine implementations).

## Clean Architecture Rules
- Use Cases must reside in `:core:domain`.
- UI must depend on `:core:domain` via Interfaces.
- Logic should be decoupled from `android.content.Context` where possible (delegated to Repository/Engine).

## CI/CD
- GitHub Actions is used for CI.
- Pinned SHAs and JDK 21 are required for all workflows.
