package com.tazztone.losslesscut.domain.model

public data class SessionRestoreResult(
    public val clips: List<MediaClip>,
    public val missingUris: List<String> = emptyList()
)
