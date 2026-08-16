## Verification

- Run `GRADLE_USER_HOME=/tmp/lossless-cut-gradle ./scripts/dev-scripts/project-verify.sh`; if Gradle reports a read-only cache/lock or unusable wildcard IP, rerun with approved escalation.
- Run `GRADLE_USER_HOME=/tmp/lossless-cut-gradle ./scripts/dev-scripts/gradle-test.sh <module> "*"`; keep the quoted wildcard (`:core:domain` uses `:test`, Android modules use `:testDebugUnitTest`).

## Architecture and Storage

- Access user/shared media only through SAF or `ContentResolver`; `java.io.File` is allowed only for app-private storage such as cache data.
- Keep `:engine` behind Hilt and `:core:domain` interfaces (`:app` uses `runtimeOnly(:engine)`); keep `:core:domain` pure JVM with no AndroidX or Hilt imports.
- Keep Compose under `:app/ui/compose/**`; do not use Compose for custom views or timeline rendering.

## Runtime and Tests

- Serialize `MediaExtractor`/`MediaMuxer` work on the dedicated single-threaded engine dispatcher; rethrow `CancellationException` before generic catches and call `ensureActive()` in loops.
- Put native engine instrumented tests under `:engine/src/androidTest`; catch `UnsupportedOperationException` when finalizing `IS_PENDING=0` for SAF or FileProvider URIs.

## Environment and Changes

- Verify actual files under `com/tazztone/losslesscut` with `rg` before editing; inspect `git status` before staging, and rerun `git add`/`git commit` with approved escalation if `.git/index.lock` is read-only.
- When adding or editing UI string keys in `values/strings.xml`, always add matching German translations in `values-de/strings.xml` to avoid Android Lint `MissingTranslation` failures.
- If data class signatures change and Gradle reports synthetic constructor `NoSuchMethodError` in tests, run `./scripts/dev-scripts/project-clean.sh --all` to wipe stale persistent bytecode cache.
