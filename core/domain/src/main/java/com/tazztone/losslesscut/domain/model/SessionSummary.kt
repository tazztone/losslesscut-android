package com.tazztone.losslesscut.domain.model

import kotlinx.serialization.Serializable

@Serializable
public data class SessionSummary(
    public val uri: String,
    public val fileName: String,
    public val clipCount: Int,
    public val updatedAtEpochMs: Long,
    public val sessionId: String = ""
)
