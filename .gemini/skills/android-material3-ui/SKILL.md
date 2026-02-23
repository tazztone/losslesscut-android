---
name: android-material3-ui
description: Guidelines for implementing Material Design 3 and custom View accessibility. Use when updating the UI, creating new layouts, or improving `CustomVideoSeeker`.
---

# Android Material 3 & Custom UI

## Core Principles
- **Material 3**: Use `Theme.Material3.Dark.NoActionBar`.
- **Responsive Layouts**: Use `layout` (portrait) and `layout-land` (landscape).
- **Custom Views**: `CustomVideoSeeker` for complex timeline interactions.

## Accessibility Workflow
1. Implement `ExploreByTouchHelper` for custom views.
2. Expose virtual nodes (e.g., playhead, segment handles).
3. Support standard accessibility actions (Scroll Forward/Backward).

## Reference
- **MD3 Color**: Use `colorSurface`, `colorOnSurface`, `colorPrimary`.
- **Animations**: Implement auto-dismissing hint animations for user gestures (e.g., "pinch to zoom").
