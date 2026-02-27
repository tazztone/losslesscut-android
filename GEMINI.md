# LosslessCut Android — Agent Rules

## 1. Architectural Boundaries (Build-Enforced)
- **Storage (External)**: SAF/Scoped Storage ONLY for user-facing media. Pass URIs as `String`; use `DocumentFile`/`ContentResolver`. NO `java.io.File` for shared storage.
- **Storage (Internal)**: App-private directories (`cacheDir`, `filesDir`) may use `java.io.File` — Scoped Storage does not apply.
- **Modules**: `:core:domain` is a **Pure JVM library** (compiler-enforced; no `androidx.*` or Hilt). `:app` has `runtimeOnly(:engine)`; do NOT import `:engine` directly from `:app`. Route through Hilt and `:core:domain` interfaces.
- **Media**: `Media3/ExoPlayer` is strictly for playback UI in `:app`. Processing in `:engine` isolated from the data layer via `IMediaFinalizer`. Use `MediaExtractor`/`MediaMuxer` only.

## 2. UI & Compose Policy (Hybrid)
- **Allowed**: Compose ONLY for new, isolated UI (settings, sheets, dialogs) under `:app/ui/compose/**`. Embed via `ComposeView`; share state from existing ViewModels.
- **Forbidden**: NO Compose in `customviews/**`. Do NOT rewrite the XML/ViewBinding timeline or seeker rendering.

## 3. Workflow & Tooling
- **Commands**: `./gradlew detekt lint testDebugUnitTest` — module-specific. `maxIssues: 0`; fix by refactoring.
- **File Edits**: Prefer `replace_file_content` over `multi_replace_file_content` for fast-moving files.
- **First-Pass Patterns**:
  - *Complexity (DeepNesting)*: Detekt flags nested conditionals; satisfy with early returns and extracted boolean predicates before `if`.
  - *Refactoring Splits*: When decomposing a `LargeClass`, verify `init` blocks (e.g., `Paint` colors) and domain imports survive the move.
  - *Cleanup*: Strip `UnusedPrivateProperty` immediately after modifying logic.
