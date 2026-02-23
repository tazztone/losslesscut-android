---
name: repo-auditor
description: Audit the repository for major "best practice" violations, glaring gaps, antipatterns, issues, or room for improvement. Use when the user asks for a codebase health check, architectural review, or general improvements.
---

# Repo Auditor

Audit the codebase to identify architectural weaknesses, "best practice" violations, and opportunities for optimization. This skill coordinates specialized audits across different domains of the project.

## Audit Workflow

When performing an audit, follow these steps to ensure a comprehensive review:

### 1. Project Topology & Health Survey
- Map the high-level architecture (e.g., MVVM, Clean Architecture).
- Identify core components and their responsibilities.
- Check for consistent naming conventions and package structures.
- Evaluate the use of `AGENTS.md` and `CHANGELOG.md` for project governance.

### 2. Dependency & Build Audit
- Review `gradle/libs.versions.toml` for version management consistency.
- Check for unnecessary dependencies or "fat" builds.
- **Delegate to:** `gradle-optimizer` for deep build performance and dependency analysis.

### 3. Architecture & Clean Code Audit
- Identify tightly coupled components or "God Objects."
- Check for proper Separation of Concerns.
- **Delegate to:** `clean-code-reviewer` to enforce SOLID principles and naming standards.

### 4. Concurrency & State Management Audit
- Evaluate `ViewModel` state handling (StateFlow vs. LiveData).
- Check for potential memory leaks or incorrect coroutine scoping.
- **Delegate to:** `kotlin-coroutines-expert` for deep analysis of async patterns.

### 5. Domain-Specific Audit (Media & UI)
- **Media Processing:** Review `MediaExtractor`, `MediaMuxer`, and `Media3` usage for efficiency and edge-case handling.
  - **Delegate to:** `android-native-media-processing`.
- **UI/UX:** Review Material 3 implementation and accessibility.
  - **Delegate to:** `android-material3-ui`.

### 6. Testing & Quality Audit
- Identify gaps in unit and UI test coverage.
- Evaluate the testability of the code (DI usage, etc.).
- **Delegate to:** `android-test-automation`.

### 7. Documentation Audit
- Check if `README.md` and public APIs are well-documented.
- Ensure the project history is maintainable.
- **Delegate to:** `doc-expert`.

## Audit Report Structure

Synthesize findings into a report with the following sections:

1.  **Summary:** High-level health score and "State of the Union."
2.  **Critical Issues:** Immediate risks (e.g., potential crashes, security flaws, major antipatterns).
3.  **Gaps & Missing Features:** Glaring omissions in testing, documentation, or functionality.
4.  **Best Practice Violations:** Deviations from standard Android/Kotlin/Project patterns.
5.  **Room for Improvement:** "Nice-to-haves" and architectural refactoring suggestions.
6.  **Prioritized Action Plan:** A list of recommended next steps, sorted by impact vs. effort.

## Example Requests

- "Run a full audit of the project and tell me where we are failing."
- "What are the biggest architectural weaknesses in our media processing logic?"
- "Check the repo for any glaring antipatterns or best practice violations."
- "Give me a health report of the project."
