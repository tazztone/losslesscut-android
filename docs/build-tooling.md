# Build Tooling Matrix

This document tracks the current stable toolchain and dependency matrix for Lossless Video Cut Android, as required by [GEMINI.md](../GEMINI.md).

## Core Toolchain

| Tool | Version | Notes |
| :--- | :--- | :--- |
| **Gradle** | 9.2.1 | Minimum requirement for Panda 2 / AGP 9.0 |
| **AGP** | 9.0.1 | Built-in Kotlin support enabled |
| **Kotlin** | 2.2.10 | Enforced by AGP 9.0 runtime |
| **KSP** | 2.3.5 | Explicit AGP 9.0 / Panda 2 support |
| **JDK** | 17 | Baseline (Java 21 supported for Daemon) |
| **compileSdk** | 36 | "Baklava" (Stable) |
| **targetSdk** | 36 | Required for Play Store 2026 |

## Standard Flags

| Flag | Value | Status |
| :--- | :--- | :--- |
| `android.disallowKotlinSourceSets` | `true` | Enabled (KSP 2.3.5 compatibility verified) |
| `android.nonTransitiveRClass` | `true` | Standard |
| `kotlin.code.style` | `official` | Standard |
| `org.gradle.configuration-cache` | `true` | **Optimized** (verified) |
| `org.gradle.parallel` | `true` | **Optimized** |
| `org.gradle.caching` | `true` | **Optimized** |
| `-Xshare:off` | enabled | Noise reduction (OpenJDK CDS warnings) |

## Key Dependency Axes

| Axis | Version | Last Verified |
| :--- | :--- | :--- |
| **Compose BOM** | 2026.02.00 | verified |
| **Core KTX** | 1.17.0 | verified (requires SDK 36) |
| **Coroutines Test** | 1.10.2 | verified |
| **Detekt** | 1.23.8 | modern configuration |
| **Media3** | 1.5.1 | baseline |
| **Hilt** | 2.59.1 | baseline |

## Known Compatibility Warnings

| Warning | Source | Resolution Plan |
| :--- | :--- | :--- |
| `ReportingExtension.file(String)` deprecation | Detekt 1.23.8 | Upgrade to **Detekt 2.0 stable** when released. |
| Gradle 10 incompatibility notice | Plugin Internals | Upgrade to **AGP 9.1 stable** when released. |

## Modernization Roadmap

1. [x] Upgrade Compose BOM to 2026.02.00
2. [x] Upgrade Core KTX to 1.17.0
3. [x] Upgrade Coroutines Test to 1.10.x (Current: 1.10.2)
4. [ ] Upgrade to AGP 9.1 (Stability pending)
5. [ ] Upgrade to Detekt 2.0 (Stability pending)
