---
name: android-native-media-processing
description: Specialized knowledge of Android `MediaExtractor`, `MediaMuxer`, and `Media3/ExoPlayer` for lossless operations. Use when the user needs to cut video without re-encoding, map streams, or handle GOP-aware extraction.
---

# Android Native Media Processing

## Core Technologies
- **MediaExtractor**: Seeks to nearest keyframe and extracts encoded samples.
- **MediaMuxer**: Writes samples to output formats (MP4, M4A).
- **Media3/ExoPlayer**: High-level playback and metadata probing.

## Workflow: Lossless Cut
1. Use `MediaExtractor` to seek to `sync` (keyframe).
2. Validate track compatibility (codecs, profiles).
3. Extract samples and write directly to `MediaMuxer`.
4. Adjust PTS (Presentation Time Stamp) for continuity.

## References
- Use `seekTo(startUs, SEEK_TO_PREVIOUS_SYNC)` for keyframe accuracy.
- Ensure `MediaFormat.KEY_ROTATION` is handled in `MODE_METADATA`.
- For audio-only exports, use `.m4a` and skip video tracks.
