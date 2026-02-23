---
name: gradle-optimizer
description: Strategies for optimizing Gradle build times and managing Version Catalogs (`libs.versions.toml`). Use when the build is slow, dependency conflicts arise, or `build.gradle` needs refactoring.
---
# Gradle Optimizer
- **Version Catalogs**: Use `libs.versions.toml` for all dependencies.
- **Build Speed**: Use Gradle Build Cache and Configuration Caching.
- **Custom Scripts**: Maintain `dev-scripts/` for cleaning, pushing debug builds, and viewing logs.
