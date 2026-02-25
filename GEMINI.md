# LosslessCut Android — Agent Rules

## Hard Boundaries
- **No `java.io.File`**: Use SAF/Scoped Storage. Pass URIs as `String`; use `DocumentFile`/`ContentResolver`.
- **No Compose**: Strict XML ViewBinding only.
- **`:core:domain`**: Pure Kotlin, zero Android imports (`ByteArray` for images).
- **Injection**: `:app` uses `runtimeOnly` on `:engine`. Use `:core:domain` interfaces bound via Hilt.
- **Media3**: UI playback only in `:app`. `:engine` uses native `MediaExtractor`/`Muxer`.

## Detekt Rules (maxIssues: 0)
- **Flatten Code**: `NestedBlockDepth` limit: 4. Use early returns (guard clauses) immediately.
- **Extract Logic**: `CyclomaticComplexMethod` limit: 15. Break complex `when`/`if` blocks into local functions.
- **Limit Conditionals**: `ComplexCondition` limit: 4. Extract into local boolean variables.
- **No Wildcard Imports**: `WildcardImport` is active. Import classes explicitly.
- **Formatting**: `MaxLineLength` is 150. Use trailing commas.