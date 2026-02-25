# LosslessCut Android — Agent Rules

## Hard Boundaries & Traps
- **No `java.io.File`**: All I/O is SAF/Scoped Storage. Pass URIs as `String`; use `DocumentFile`/`ContentResolver`.
- **No Jetpack Compose**: The project uses XML ViewBinding strictly. 
- **`:core:domain` Isolation**: Pure Kotlin only. Zero Android imports allowed (e.g., use `ByteArray` for images).
- **Injection Trap**: `:app` uses `runtimeOnly` on `:engine`. You cannot import `:engine` classes into `:app`. Use `:core:domain` interfaces bound via Hilt.
- **Media3 / ExoPlayer**: Allowed ONLY in `:app` for UI playback. `:engine` must strictly use native `MediaExtractor`/`MediaMuxer`.

## Detekt & Code Quality
- **Keep Methods Small**: Touch events (`handleActionMove`) and drawing logic get complex fast. Extract logic into small helper functions immediately to avoid `CyclomaticComplexMethod`.
- **Flatten Code**: Use early returns (guard clauses) to avoid `NestedBlockDepth`. Extract complex conditions into well-named local boolean variables.
- **Formatting**: Use trailing commas and put arguments on new lines to avoid `MaxLineLength` violations.
- **Validation**: After generating code, run `./gradlew test detekt lint` and iteratively fix any flagged lines to ensure `maxIssues: 0`.