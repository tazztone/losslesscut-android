# LosslessCut Android — Agent Rules

## Build & Verification Commands
- Run verification: `./scripts/dev-scripts/project-verify.sh`
- Run module test: `./scripts/dev-scripts/gradle-test.sh <module> "*"` (Must use wildcard `"*"` to prevent shell glob errors; applies `:test` for `:core:domain`, `:testDebugUnitTest` for Android modules).

## Architectural Guardrails
- **External Storage**: SAF / `ContentResolver` ONLY for user media. `java.io.File` is strictly forbidden for shared storage.
- **Module Isolation**: Do NOT import `:engine` directly in `:app` (`runtimeOnly(:engine)`). Route via Hilt and `:core:domain` interfaces. `:core:domain` must remain a pure JVM library (no `androidx.*` or Hilt).
- **UI Policy**: Jetpack Compose allowed ONLY under `:app/ui/compose/**`. NO Compose in `customviews/**` or timeline rendering.
- **Coroutine Cancellation**: Always rethrow `CancellationException` (`catch (e: CancellationException) { throw e }`) before catching generic `Exception` in engine/domain coroutines, and include `ensureActive()` in loop iterations.
- **Native Thread Safety**: `MediaMuxer` and `MediaExtractor` operations in `:engine` MUST be confined to sequential execution on a dedicated single-threaded dispatcher. Concurrent calls cause native C++ SIGSEGV crashes.

## Runtime Gotchas
- **Engine Instrumented Tests**: Engine instrumented tests MUST reside in `:engine/src/androidTest` (not `:app`).
- **Non-MediaStore URIs**: Catch `UnsupportedOperationException` when invoking `resolver.update(uri, IS_PENDING=0)` on SAF or FileProvider URIs. (For extended test harness details, see docs/architecture.md#7-testing-architecture).

