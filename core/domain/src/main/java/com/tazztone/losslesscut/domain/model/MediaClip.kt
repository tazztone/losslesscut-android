package com.tazztone.losslesscut.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

public object UuidSerializer : KSerializer<UUID> {
    public override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    public override fun serialize(encoder: Encoder, value: UUID): Unit = encoder.encodeString(value.toString())
    public override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Serializable
public enum class SegmentAction { KEEP, DISCARD }

@Serializable
public data class TrimSegment(
    @Serializable(with = UuidSerializer::class)
    public val id: UUID = UUID.randomUUID(),
    public val startMs: Long,
    public val endMs: Long,
    public val action: SegmentAction = SegmentAction.KEEP
)

@Serializable
public data class MediaTrack(
    public val id: Int,
    public val mimeType: String,
    public val isVideo: Boolean,
    public val isAudio: Boolean,
    public val language: String? = null,
    public val title: String? = null
)

@Serializable
public data class MediaClip(
    @Serializable(with = UuidSerializer::class)
    public val id: UUID = UUID.randomUUID(),
    public val uri: String,
    public val fileName: String,
    public val durationMs: Long,
    public val width: Int,
    public val height: Int,
    public val videoMime: String?,
    public val audioMime: String?,
    public val sampleRate: Int,
    public val channelCount: Int,
    public val fps: Float,
    public val rotation: Int,
    public val isAudioOnly: Boolean,
    public val segments: List<TrimSegment> = listOf(TrimSegment(startMs = 0, endMs = durationMs)),
    public val availableTracks: List<MediaTrack> = emptyList()
)
