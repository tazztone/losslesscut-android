# LosslessCut Android — Agent Rules

## Hard Rules
- **No `java.io.File`**: All I/O is SAF/Scoped Storage. Pass URIs as `String`; use `DocumentFile`/`ContentResolver`.
- **UI System**: The project uses XML ViewBinding. DO NOT generate Jetpack Compose code.
- **`:core:domain` is pure Kotlin**: zero Android imports. Use `ByteArray` for images.
- **Media3 / ExoPlayer**: Allowed ONLY in `:app` for UI playback. `:engine` must strictly use native `MediaExtractor`/`MediaMuxer`.
- **Injection Trap**: `:app` uses `runtimeOnly` on `:engine`. You cannot import `:engine` classes into `:app`. Use `:core:domain` interfaces bound via Hilt.
- **Activity is just a Host**: `VideoEditingActivity` routes via Jetpack Navigation. Put all UI logic in Fragments.
- **State & Concurrency**: Use Coroutines (`viewModelScope`, `IoDispatcher`). Use `StateFlow` for state and `Channel` for one-time events in ViewModels. No RxJava or LiveData.
- **`LosslessEngineImpl` is a legacy God Class**: shrink it, never add to it.

## CI & Testing Gates
- **Static Analysis**: `./gradlew test detekt lint` — `maxIssues: 0` blocks merges. Suppress `@UnstableApi` for Media3 when necessary.
- **Testing Stack**: Use JUnit4, MockK, Robolectric, and `kotlinx-coroutines-test`. Do not use Mockito.