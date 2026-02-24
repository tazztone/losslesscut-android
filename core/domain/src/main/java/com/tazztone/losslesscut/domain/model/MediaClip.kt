package com.tazztone.losslesscut.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Serializable
enum class SegmentAction { KEEP, DISCARD }

@Serializable
data class TrimSegment(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val startMs: Long,
    val endMs: Long,
    val action: SegmentAction = SegmentAction.KEEP
)

@Serializable
data class MediaTrack(
    val id: Int,
    val mimeType: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val language: String? = null,
    val title: String? = null
)

@Serializable
data class MediaClip(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val uri: String,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoMime: String?,
    val audioMime: String?,
    val sampleRate: Int,
    val channelCount: Int,
    val fps: Float,
    val rotation: Int,
    val isAudioOnly: Boolean,
    val segments: List<TrimSegment> = listOf(TrimSegment(startMs = 0, endMs = durationMs)),
    val availableTracks: List<MediaTrack> = emptyList()
)
