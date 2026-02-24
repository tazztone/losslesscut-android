# LosslessCut Android — Agent Rules

## Module Boundaries
| Module | Scope | Allowed Dependencies |
| :--- | :--- | :--- |
| `:core:domain` | **Pure logic** | NO Android deps (No Context/Uri/Bitmap). |
| `:core:data` | **Data Access** | Context, Uri, ContentResolver, DataStore. |
| `:engine` | **Muxing/Cutting** | MediaExtractor, MediaMuxer, MetadataRetriever. |
| `:app` | **UI/Playback** | Full Android, Media3/ExoPlayer, ViewModels. |

## Critical Constraints
1.  **URI Handling (SAF):**
    *   Pass URIs as `String` between layers.
    *   **NEVER** use `java.io.File(path)` for content URIs; they are not files.
    *   Only `:engine` and `:core:data` may parse URIs via `ContentResolver`.
2.  **Concurrency:**
    *   **NEVER** swallow `CancellationException`. Always rethrow.
    *   Engine loops must check `currentCoroutineContext().isActive`.
3.  **Engine Implementation:**
    *   Use `setDataSourceSafely` helper (handles file descriptors).
    *   Use `TrackType` enum (do not use raw Ints).
    *   Report progress via `Flow<Float>` (0.0–1.0).
    *   **DO NOT** use Media3/ExoPlayer in `:engine`; strict separation.
4.  **Domain Purity:**
    *   Images cross boundaries as `ByteArray`, never `Bitmap`.
    *   Use `Result<T>` for errors; avoid exceptions for control flow.

## Quality Gates
*   **Detekt:** `maxIssues: 0`. No new `LongMethod` or `LargeClass` violations.
*   **Legacy:** `LosslessEngineImpl.kt` is a known God Class. Do not expand it; refactor into smaller helpers.

## Refactoring Workflow

### 1. Design Before You Code — Detekt-First Sketching
*   Before writing any refactored class, sketch its **constructor signature** and count parameters.
*   If a constructor exceeds the detekt `maxParameters` threshold (currently **5**), define the grouping
    `data class` (e.g., `UseCases`, `Collaborators`) **before** writing the implementation.
*   Name grouping types in the design sketch so the name is stable and consistent across the PR.
*   Never discover a parameter-count violation mid-implementation; it causes cascading renames.

### 2. Pre-Push & CI Safety Harness
*   **Locally**, always run the full gate before pushing:
    ```
    ./gradlew test detekt lint
    ```
*   **CI** (`.github/workflows/`) must run the same three tasks on every PR. A PR with any
    `detekt` or `lint` failure must **not** be merged.
*   Treat a CI failure on a refactor branch as a blocker, not a warning.

### 3. API Signature Alignment Is a First-Class Task
*   The `:core:domain` module **must** compile with `-Xexplicit-api=strict`. Add this to
    `domain/build.gradle.kts`:
    ```kotlin
    kotlin { explicitApi() }
    ```
*   When renaming interface parameters, update all implementations in the **same commit**.
    Parameter-name mismatches cause silent bugs with named-argument call sites.
*   If adding a new interface method, stub all implementations (`TODO("not yet implemented")`)
    in the same commit so the project always compiles on every commit in `git log`.

### 4. Toolchain Version Pinning
*   Record the current known-good version combo in `docs/build-tooling.md`:
    ```
    AGP:   <version>
    Kotlin: <version>
    KSP:   <version>
    Hilt:  <version>
    ```
*   **Update only one axis per PR.** Never bump AGP and Kotlin in the same commit.
*   The `disallowKotlinSourceSets` / KSP source-set conflict is a known dependency of the
    current Hilt setup. Do **not** attempt to resolve it without a dedicated spike branch
    that is fully reverted if the build breaks.
