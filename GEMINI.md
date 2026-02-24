# LosslessCut Android — Agent Rules

## Module Boundaries
| Module | Scope | Allowed Dependencies |
| :--- | :--- | :--- |
| `:core:domain` | Pure Logic | NO Android deps. |
| `:core:data` | Data Access | Context, Uri, ContentResolver. |
| `:engine` | Muxing | MediaExtractor, MediaMuxer. |
| `:app` | UI | Android, Media3, ViewModels. |

## Critical Constraints
1. **URI (SAF):** Pass as `String`. **NEVER** use `java.io.File`. Parse only in `:engine`/`:core:data`.
2. **Concurrency:** Rethrow `CancellationException`. Check `isActive` in loops.
3. **Engine:** Use `setDataSourceSafely`. Use `TrackType`. Progress via `Flow`. **NO** Media3/ExoPlayer.
4. **Domain:** Images as `ByteArray` (no `Bitmap`). Use `Result<T>` for errors.

## Workflow & Gates
1. **Detekt:** Max params=5. If >5, define grouping `data class` *before* impl. `maxIssues: 0`.
2. **Safety:** Run `./gradlew test detekt lint` pre-push. CI failures are blockers.
3. **API:** Use `explicitApi()` in domain. Update interface/impls atomically.
4. **Tooling:** Pin versions in `docs/build-tooling.md`. Update 1 axis/PR. Keep `disallowKotlinSourceSets`.
5. **Legacy:** `LosslessEngineImpl` is legacy God Class. Shrink, don't grow.
