# Contributing to LosslessCut (MP4)

Thank you for your interest in contributing! This guide outlines local environment setup, developer tooling, coding standards, and verification steps.

---

## 🚀 Quick Start & Development Setup

### Prerequisites
- **JDK 17+** (JDK 21 recommended for Gradle Daemon)
- **Android Studio Koala+** or Android SDK with Target API 36 / Min API 26
- **ADB** (in your `PATH` or standard Android SDK platform-tools location)

### Building the App
```bash
# Clone the repository
git clone https://github.com/tazztone/losslesscut-android.git
cd losslesscut-android

# Build the debug APK
./gradlew assembleDebug
```

---

## 🛠️ Developer Scripts Suite

All developer automation scripts reside under `./scripts/dev-scripts/`.

### 1. Full Project Verification (CI Equivalent)
Run complete static analysis, unit tests, and coverage checks before pushing changes:
```bash
./scripts/dev-scripts/project-verify.sh
```
*Executes Detekt, Android Lint, unit tests, and generates Kover HTML coverage reports.*

### 2. Targeted Unit Testing
Run tests for specific modules or classes:
```bash
./scripts/dev-scripts/gradle-test.sh <module> <test_pattern>

# Examples:
./scripts/dev-scripts/gradle-test.sh :core:domain "*.SilenceCutUseCaseTest"
./scripts/dev-scripts/gradle-test.sh :engine "*.TrackInspectorTest"
./scripts/dev-scripts/gradle-test.sh :app "*.VideoEditingViewModelTest"
```
*Automatically selects `:test` for pure JVM modules (`:core:domain`) and `:testDebugUnitTest` for Android library/app modules.*

### 3. App Deployment & ADB Tooling
- **Build & Launch App**:
  ```bash
  ./scripts/dev-scripts/adb-run-app.sh
  ```
- **Clean Uninstall & Reinstall** (resolves signature or Scoped Storage cache conflicts):
  ```bash
  ./scripts/dev-scripts/adb-reinstall.sh
  ```
- **Stream Filtered Logcat Logs**:
  ```bash
  ./scripts/dev-scripts/adb-logcat.sh
  ```

### 4. Build Cleaning & Asset Generation
- **Clean Build Caches**:
  ```bash
  ./scripts/dev-scripts/project-clean.sh
  ```
- **Generate Launcher App Icons**:
  ```bash
  java scripts/dev-scripts/asset-generate-icons.java
  ```

---

## 📐 Architecture & Coding Standards

We follow **MVVM + Clean Architecture** with strict layer separation. See [docs/architecture.md](docs/architecture.md) for the complete system architecture, module design, and component blueprints.

1. **Module Boundaries**:
   - `:core:domain` is a **pure JVM library** (no Android or Hilt dependencies). Business logic and Use Cases live here.
   - `:engine` handles native `MediaExtractor` / `MediaMuxer` media processing and is isolated from storage and UI.
   - `:app` owns UI, Fragments, and Jetpack ViewModels. `:app` accesses `:engine` via `runtimeOnly(:engine)` and domain interfaces.
2. **Storage Policy**:
   - **External/Shared Storage**: Access user media strictly via Storage Access Framework (SAF) and `ContentResolver`. `java.io.File` is forbidden for shared media.
   - **Internal Storage**: App-private storage (`cacheDir`, `filesDir`) may use `java.io.File`.
3. **UI Policy**:
   - Jetpack Compose is permitted ONLY for new, isolated UI under `:app/ui/compose/**`.
   - Custom timeline and seeker rendering remain XML / ViewBinding based (`CustomVideoSeeker`).

---

## 🧪 Pull Request Checklist

Before submitting a Pull Request:
- [ ] Run `./scripts/dev-scripts/project-verify.sh` locally and ensure all checks pass (`detekt`, `lint`, and unit tests).
- [ ] Verify that new business logic is covered by unit tests in `:core:domain`, `:engine`, or `:app`.
- [ ] Ensure `:core:domain` remains free of `android.*` or `hilt` imports.
