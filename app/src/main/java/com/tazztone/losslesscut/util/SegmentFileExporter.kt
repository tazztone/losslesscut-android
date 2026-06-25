package com.tazztone.losslesscut.util

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MS_TO_SEC = 1000.0
private const val LLC_VERSION = 2

@Serializable
private data class LlcSegment(
    val start: Double,
    val end: Double,
    val name: String = "",
    val selected: Boolean = true
)

@Serializable
private data class LlcFile(
    val version: Int = LLC_VERSION,
    val mediaFileName: String,
    val cutSegments: List<LlcSegment>
)

object SegmentFileExporter {

    fun generateLlcContent(clips: List<MediaClip>): String {
        val allKeepSegments = clips
            .flatMap { clip -> clip.segments }
            .filter { segment -> segment.action == SegmentAction.KEEP }
            .sortedBy { segment -> segment.startMs }
            .map { segment ->
                LlcSegment(
                    start = segment.startMs / MS_TO_SEC,
                    end = segment.endMs / MS_TO_SEC
                )
            }

        val primaryFileName = clips.firstOrNull()?.fileName ?: "media"

        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        return json.encodeToString(
            LlcFile(
                version = LLC_VERSION,
                mediaFileName = primaryFileName,
                cutSegments = allKeepSegments
            )
        )
    }

    fun deriveLlcFileName(mediaFileName: String): String {
        val baseName = if (mediaFileName.contains('.')) {
            mediaFileName.substringBeforeLast('.')
        } else {
            mediaFileName
        }
        return "$baseName.llc"
    }
}