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
