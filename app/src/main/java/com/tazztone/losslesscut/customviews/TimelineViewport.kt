package com.tazztone.losslesscut.customviews

import kotlin.math.max

public data class TimelineViewport(
    public val durationMs: Long = 0L,
    public val viewWidthPx: Int = 0,
    public val zoomFactor: Float = MIN_ZOOM,
    public val scrollXPx: Float = 0f,
    public val tileWidthPx: Int = DEFAULT_TILE_WIDTH_PX,
    public val paddingPx: Float = DEFAULT_PADDING_PX
) {
    public val contentWidthPx: Float
        get() = max(1f, (viewWidthPx - 2 * paddingPx) * zoomFactor)

    public val maxScrollXPx: Float
        get() = max(0f, contentWidthPx - (viewWidthPx - 2 * paddingPx))

    public fun timeToPixel(timestampMs: Long): Float {
        if (durationMs <= 0L) return paddingPx
        val clampedTime = timestampMs.coerceIn(0L, durationMs)
        val fraction = clampedTime.toFloat() / durationMs.toFloat()
        return paddingPx + (fraction * contentWidthPx) - scrollXPx
    }

    public fun pixelToTime(pixelX: Float): Long {
        if (durationMs <= 0L || contentWidthPx <= 0f) return 0L
        val relativeX = pixelX - paddingPx + scrollXPx
        val fraction = (relativeX / contentWidthPx).coerceIn(0f, 1f)
        return (fraction * durationMs).toLong()
    }

    public fun clampScrollX(targetScrollX: Float): Float {
        return targetScrollX.coerceIn(0f, maxScrollXPx)
    }

    public fun zoomAt(focusPixelX: Float, newZoomFactor: Float): TimelineViewport {
        val clampedZoom = newZoomFactor.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (clampedZoom == zoomFactor || durationMs <= 0L) {
            return copy(zoomFactor = clampedZoom)
        }

        val focusTimeMs = pixelToTime(focusPixelX)
        val newContentWidth = max(1f, (viewWidthPx - 2 * paddingPx) * clampedZoom)
        val fraction = (focusTimeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val newUnclampedScrollX = (fraction * newContentWidth) - (focusPixelX - paddingPx)
        val newClampedScrollX = newUnclampedScrollX.coerceIn(0f, max(0f, newContentWidth - (viewWidthPx - 2 * paddingPx)))

        return copy(
            zoomFactor = clampedZoom,
            scrollXPx = newClampedScrollX
        )
    }

    public fun visibleTileRange(): IntRange {
        if (contentWidthPx <= 0f || tileWidthPx <= 0) return 0..0
        val startTile = max(0, (scrollXPx / tileWidthPx).toInt())
        val endTile = max(startTile, ((scrollXPx + viewWidthPx) / tileWidthPx).toInt())
        return startTile..endTile
    }

    public companion object {
        public const val MIN_ZOOM: Float = 1.0f
        public const val MAX_ZOOM: Float = 20.0f
        public const val DEFAULT_TILE_WIDTH_PX: Int = 2048
        public const val DEFAULT_PADDING_PX: Float = 50.0f
    }
}
