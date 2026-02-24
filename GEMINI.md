# Gemini Integration Guide

## Project Context
Lossless video editor for Android (Media3, MediaExtractor, MediaMuxer).
Clean Architecture (MVVM) with multi-module Gradle setup.

## Module Responsibilities
- `:app`: UI (Fragments, ViewModels), Navigation.
- `:core:domain`: Domain Layer (Models, Use Cases, Engine interfaces).
- `:core:data`: Data Layer (Repository Impl, Persistence, Utils).
- `:engine`: Media Processing Layer (Lossless Engines).

## Clean Architecture & Design
- Use Cases exclusively in `:core:domain`. Logic decoupled from `Context`.
- Palette: Modern, vibrant colors (HSL), dark mode, glassmorphism.
- UI: Jetpack Navigation, Fragments (`Editor`, `Remux`, `Metadata`).
- `CustomVideoSeeker`: High-performance timeline with accessibility & zoom.

## Data & Engine
- `LosslessEngine`: Core muxing/merging logic.
- `AppPreferences`: DataStore for settings.
- `StorageUtils`: Scoped Storage (SAF) helper.

## CI/CD & Tooling
- GitHub Actions (JDK 21, Pinned SHAs).
- JVM Tests: `./gradlew test` | Lint: `./gradlew lintDebug`.

## Context7 Library IDs
- `/androidx/media` (Media3) | `/kotlin/kotlinx.coroutines`
- `/androidx/datastore` | `/material-components/material-components-android`
