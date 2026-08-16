# Contributing to LosslessCut (MP4)

Thank you for your interest in contributing! This guide outlines local environment setup, developer tooling, coding standards, and verification steps.

---

## 🚀 Quick Start & Development Setup

### Prerequisites
- **JDK 17 or 21**
- **Android SDK 36** (Target) / **29** (Min)
- **AGP 9.0+ / Gradle 9.2+**
- **ADB** (in system `PATH`)

### Building & Running
```bash
# Clone the repository
git clone https://github.com/tazztone/lossless-video-cut.git
cd lossless-video-cut

# Build debug APK
./gradlew assembleDebug

# Deploy & launch on connected device/emulator
./scripts/dev-scripts/adb-run-app.sh
```

---

## 🛠️ Developer Scripts Suite & Automation

All developer automation scripts reside under `./scripts/dev-scripts/`:

| Script | Command | Purpose |
| :--- | :--- | :--- |
| **Verification Gate** | `./scripts/dev-scripts/project-verify.sh` | Executes Detekt, unit tests, Lint, Kover reports, and the domain coverage threshold sequentially. |
| **Targeted Testing** | `./scripts/dev-scripts/gradle-test.sh <module> "*"` | Runs unit tests for specific modules (`:core:domain`, `:engine`, `:app`). |
| **Launch App** | `./scripts/dev-scripts/adb-run-app.sh` | Builds and launches debug APK on target device. |
| **Clean Reinstall** | `./scripts/dev-scripts/adb-reinstall.sh` | Performs clean uninstall and reinstall to resolve storage/signature cache conflicts. |
| **Logcat Stream** | `./scripts/dev-scripts/adb-logcat.sh` | Streams filtered logcat logs for `com.tazztone.losslesscut`. |
| **Clean Caches** | `./scripts/dev-scripts/project-clean.sh [--all]` | Cleans Gradle build outputs; `--all` / `--cache` also wipes persistent transform/build cache directories. |

> [!NOTE]
> All scripts under `./scripts/dev-scripts/` automatically configure `GRADLE_USER_HOME` to `/tmp/lossless-cut-gradle` if not already defined.

---

## 📐 Architectural Standards & Enforcement

We enforce **MVVM + Clean Architecture** with strict layer boundaries. For full architectural specifications, see [docs/architecture.md](docs/architecture.md).

### Konsist Architectural Guardrails (`ArchitectureTest.kt`)
Module isolation is automatically enforced in CI via Konsist unit tests:
1. **Pure JVM Domain**: `:core:domain` must remain pure JVM (zero `android.*` or `hilt` imports).
2. **Engine Encapsulation**: `:app` accesses `:engine` strictly via `runtimeOnly(:engine)` and domain interfaces.
3. **Storage Access**: Shared media access must rely on Storage Access Framework (SAF) or `ContentResolver` (`java.io.File` is forbidden for shared storage).
4. **UI Scoping**: Jetpack Compose is restricted to `:app/ui/compose/**`.

---

## 🧪 Pull Request & CI Verification Gates

Before opening or merging a Pull Request, every change must pass the 4-gate verification pipeline. The script runs these Gradle tasks sequentially because Android Lint and generated KSP/Hilt sources can conflict when their work overlaps:

1. **Gate 1: Static Analysis & Formatting** — Detekt rules (`./gradlew detekt`) pass cleanly.
2. **Gate 2: Unit Tests** — All JVM unit tests pass in `:core:domain`, `:engine`, and `:app`.
3. **Gate 3: Android Lint** — Zero severe lint issues across all modules (`./gradlew lint`).
4. **Gate 4: Code Coverage Target** — Kover generates reports and verifies the repository target (at least 80% domain coverage).

Execute the full suite locally prior to pushing:
```bash
./scripts/dev-scripts/project-verify.sh
```

---

## 🚀 Release Pipeline & Keystore Secrets

Production release tags and manual release dispatches use `.github/workflows/release.yml` and require GitHub Repository Secrets to sign release APKs/AABs and publish to GitHub Releases and Google Play Store. Manual dispatches require a semantic `version_name` such as `1.2.3`; tag pushes use the version in the `v*` tag. Signing passwords and aliases are provided to Gradle through environment variables; they are not placed in command-line `-P` arguments.

### Required GitHub Secrets

| Secret Name | Description |
| :--- | :--- |
| **`ANDROID_KEYSTORE_BASE64`** | Base64-encoded string of the `.jks`/`.keystore` release signing key (`base64 -w 0 app/release.keystore`). |
| **`ANDROID_KEYSTORE_PASSWORD`** | Password for the Java Keystore store. |
| **`ANDROID_KEY_ALIAS`** | Alias name of the release key inside the keystore. |
| **`ANDROID_KEY_PASSWORD`** | Password for the key alias. |
| **`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`** | Service Account JSON credentials for Google Play Store publishing. |

### Configuring Secrets via GitHub CLI (`gh`)

You can set or update all repository release secrets directly from your terminal using `gh cli`:

```bash
# 1. Base64 Keystore Secret
base64 -w 0 app/release.keystore | gh secret set ANDROID_KEYSTORE_BASE64

# 2. Keystore Passwords & Key Alias
gh secret set ANDROID_KEYSTORE_PASSWORD -b"YOUR_STORE_PASSWORD"
gh secret set ANDROID_KEY_ALIAS -b"YOUR_KEY_ALIAS"
gh secret set ANDROID_KEY_PASSWORD -b"YOUR_KEY_PASSWORD"

# 3. Google Play Service Account JSON
gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON < /path/to/service_account.json
```

---
