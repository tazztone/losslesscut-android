# Android & Kotlin Best Practices

## Kotlin Guidelines

- **Null Safety:** Leverage `?` and `!!` correctly. Prefer `?.let { ... }` or `?:` over `!!`.
- **Immutability:** Use `val` by default. Use `data class` for data holders.
- **Coroutines:** Avoid `GlobalScope`. Use `viewModelScope` or `lifecycleScope`. Prefer `Flow` for continuous streams.
- **Functional Programming:** Use `map`, `filter`, `fold`, etc., where appropriate.

## Android Architecture (MVVM/MVI)

- **Separation of Concerns:** ViewModels should not have references to Android `Context` (use `AndroidViewModel` only if necessary).
- **State Management:** Use `StateFlow` or `SharedFlow` in ViewModels to expose UI state.
- **Dependency Injection:** Use Hilt or manual DI to manage dependencies. Avoid "Service Locator" pattern where possible.
- **Testing:** Aim for 70%+ coverage of business logic in ViewModels. Use Robolectric for JVM-based Android tests.

## Build System (Gradle)

- **Version Catalog:** Use `libs.versions.toml` to manage all dependencies and plugins.
- **Modularization:** Keep logical features in separate modules or packages to improve build times and separation.
- **Optimization:** Use R8/ProGuard for release builds.

## Media Processing (Android Native)

- **Native Extraction:** Handle GOP (Group of Pictures) correctly when cutting video without re-encoding.
- **Resource Management:** Always close `MediaExtractor`, `MediaMuxer`, and `MediaCodec` in `finally` blocks.
- **Efficiency:** Minimize memory copies between native and Java layers.

## Material Design 3

- **Dynamic Color:** Support dynamic coloring where appropriate.
- **Accessibility:** Provide content descriptions for all non-decorative UI elements.
- **Responsive Layouts:** Support different screen sizes and orientations (landscape/portrait).
