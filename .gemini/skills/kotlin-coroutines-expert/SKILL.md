---
name: kotlin-coroutines-expert
description: Best practices for asynchronous programming with Coroutines, StateFlow, and SharedFlow. Use when handling background video processing, progress updates, or complex state transitions in the ViewModel.
---

# Kotlin Coroutines Expert

## Core Concepts
- **viewModelScope**: Lifecycle-aware Coroutine scope for ViewModels.
- **SharedFlow**: One-time UI actions (Toasts, Navigation, ExportComplete).
- **StateFlow**: Represents persistent UI state (Success, Error, Loading).

## Workflow: Export
1. Launch coroutine in `viewModelScope`.
2. Move to `Dispatchers.IO` for heavy file processing.
3. Emit progress updates to `StateFlow`.
4. Handle success/error via `SharedFlow` events.

## Error Handling
- Use `CoroutineExceptionHandler` for unexpected errors.
- Prefer `runCatching` for structured results.
