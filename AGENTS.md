# LosslessCut Android — Agent Rules

## Build & Verification Commands
- Run verification: `./scripts/dev-scripts/project-verify.sh` (Executes Detekt, Lint, unit tests, and Kover).
- Run module test: `./scripts/dev-scripts/gradle-test.sh <module> "*"` (Applies `:test` for `:core:domain` JVM module, `:testDebugUnitTest` for Android modules; use wildcard `"*"` to avoid pattern matching errors).

## Architectural Guardrails
- **External Storage**: SAF / `ContentResolver` ONLY for user media. `java.io.File` is strictly forbidden for shared storage.
- **Module Isolation**: Do NOT import `:engine` directly in `:app` (`runtimeOnly(:engine)`). Route via Hilt and `:core:domain` interfaces. `:core:domain` must remain a pure JVM library (no `androidx.*` or Hilt).
- **UI Policy**: Jetpack Compose allowed ONLY under `:app/ui/compose/**`. NO Compose in `customviews/**` or timeline rendering.
- **Coroutine Cancellation**: Always rethrow `CancellationException` (`catch (e: CancellationException) { throw e }`) before catching generic `Exception` in engine/domain coroutines, and include `ensureActive()` in loop iterations.

## Testing & Runtime Gotchas
- **Engine Instrumented Tests**: Engine instrumented tests MUST reside in `:engine/src/androidTest` (not `:app`).
- **Library Test FileProvider**: Mock `FileProvider` authorities or use local storage in library instrumented tests (`:engine:connectedDebugAndroidTest`).
- **Non-MediaStore URIs**: Catch `UnsupportedOperationException` when invoking `resolver.update(uri, IS_PENDING=0)` on SAF or FileProvider URIs.
- **Shared Storage in Instrumented Tests**: Copy test assets to `cacheDir` via `UiAutomation.executeShellCommand()` for targetSdk 33+ test setup.
