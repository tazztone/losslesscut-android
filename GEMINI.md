# LosslessCut Android Context
Android lossless video editor (Media3, MediaExtractor, MediaMuxer).
Clean Architecture (MVVM), multi-module Gradle.

## Modules & Rules
- `:app`: UI (Fragments, ViewModels), Jetpack Navigation. Fragments: `Editor`, `Remux`, `Metadata`.
- `:core:domain`: Use Cases, Models, Interfaces. STRICT: Zero Android framework deps (No `Context`, `Uri`, `Bitmap`).
- `:core:data`: Repo Impls, Persistence (`AppPreferences` DataStore), SAF (`StorageUtils`).
- `:engine`: Media processing. `LosslessEngine` handles muxing/merging.

## Design Specs
- Palette: Vibrant HSL, dark mode, glassmorphism.
- Timeline: Use `CustomVideoSeeker` (supports zoom/a11y).

## Tooling & CI
- JDK 21, GitHub Actions (Pinned SHAs).
- Verify: `./gradlew test lintDebug detekt`

## Key Libraries
Media3, Coroutines, DataStore, Material Components.

## LLM Directives
- Never swallow `CancellationException` in coroutines.
- Avoid exception-as-control-flow (e.g., redundant `setDataSource` try/catch).
- Do not pass `Context` into `:core:domain`.
