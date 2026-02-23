# Contributing to Lossless Video Cut

## Architectural Guidelines
- **MVVM + Clean Architecture**: Follow the separation between UI, Domain, and Data layers.
- **Multi-Module**: Place core logic in `:core:domain`, `:core:data`, or `:engine` as appropriate.
- **Use Cases**: All business logic should reside in Use Cases within the `:core:domain` module.

## Workflow
1.  **Branching**: Create a feature branch for every change.
2.  **CI**: Ensure all tests and lint checks pass via GitHub Actions.
3.  **PRs**: Open a Pull Request for review before merging into `main`.

## Code Style
- Follow standard Kotlin coding conventions.
- Use `.editorconfig` for consistent formatting.
- Avoid "God Classes"; delegate responsibilities to specialized components.
