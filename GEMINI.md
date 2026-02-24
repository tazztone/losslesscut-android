# LosslessCut Android
Stack: Media3, MediaExtractor, MediaMuxer, Coroutines, DataStore.

## Architecture & Rules
- `:core:domain`: Use Cases/Interfaces. STRICTLY NO Android framework deps (No `Context`, `Uri`, `Bitmap`).
- `:core:data`: `AppPreferences` (DataStore), `StorageUtils` (SAF).
- `:engine`: `LosslessEngine` handles actual muxing/merging.
- **DI:** Dagger Hilt (`@HiltViewModel`, `@AndroidEntryPoint`, `@Inject`).
- **Errors:** Avoid exception-as-control-flow. Never swallow `CancellationException` in coroutines.

## UI Layer (`:app`)
- XML + ViewBinding (No Compose, no `findViewById`).
- Jetpack Navigation (`Editor`, `Remux`, `Metadata` fragments).
- ViewModels expose `StateFlow` (state) and `SharedFlow` (events). No `LiveData`.
- `CustomVideoSeeker` handles the timeline and zoom.
- Design: Vibrant HSL, dark mode, glassmorphism.
