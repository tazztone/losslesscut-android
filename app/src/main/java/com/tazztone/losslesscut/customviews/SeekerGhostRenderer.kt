package com.tazztone.losslesscut.customviews

import android.graphics.Canvas
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase

/**
 * Extracted ghost rendering logic for SeekerRenderer.
 */
internal class SeekerGhostRenderer(
    private val seeker: CustomVideoSeeker,
    private val renderer: SeekerRenderer
) {
    fun drawSpecialSilenceOverlay(
        canvas: Canvas,
        result: SilenceDetectionUseCase.DetectionResult
    ) {
        when (seeker.activeSilenceVisualMode) {
            CustomVideoSeeker.SilenceVisualMode.PADDING -> drawPaddingGhosts(canvas, result)
            CustomVideoSeeker.SilenceVisualMode.MIN_SILENCE -> drawDroppedSilenceGhosts(canvas, result)
            CustomVideoSeeker.SilenceVisualMode.MIN_SEGMENT -> drawBridgedNoiseGhosts(canvas, result)
            else -> {}
        }
    }

    private fun drawPaddingGhosts(canvas: Canvas, result: SilenceDetectionUseCase.DetectionResult) {
        renderer.drawSimpleSilencePreviews(canvas, result.durationFilteredRanges)
        result.durationFilteredRanges.zip(result.finalRanges).forEach { (orig, current) ->
            if (current.first > orig.first) {
                canvas.drawRect(
                    seeker.timeToX(orig.first), 0f,
                    seeker.timeToX(current.first), seeker.height.toFloat(),
                    renderer.thresholdMaskPaint
                )
            }
            if (current.last < orig.last) {
                canvas.drawRect(
                    seeker.timeToX(current.last), 0f,
                    seeker.timeToX(orig.last), seeker.height.toFloat(),
                    renderer.thresholdMaskPaint
                )
            }
        }
    }

    private fun drawDroppedSilenceGhosts(canvas: Canvas, result: SilenceDetectionUseCase.DetectionResult) {
        renderer.drawSimpleSilencePreviews(canvas, result.finalRanges)
        result.noiseMergedRanges.forEach { raw ->
            val isKept = result.durationFilteredRanges.any { it.first == raw.first && it.last == raw.last }
            if (!isKept) {
                canvas.drawRect(
                    seeker.timeToX(raw.first), 0f,
                    seeker.timeToX(raw.last), seeker.height.toFloat(),
                    renderer.droppedSilencePaint
                )
            }
        }
    }

    private fun drawBridgedNoiseGhosts(canvas: Canvas, result: SilenceDetectionUseCase.DetectionResult) {
        renderer.drawSimpleSilencePreviews(canvas, result.noiseMergedRanges)
        var lastEnd = -1L
        result.rawRanges.forEach { raw ->
            if (lastEnd != -1L && raw.first > lastEnd) {
                val isBridge = result.noiseMergedRanges.any { it.first <= lastEnd && it.last >= raw.first }
                if (isBridge) {
                    canvas.drawRect(
                        seeker.timeToX(lastEnd), 0f,
                        seeker.timeToX(raw.first), seeker.height.toFloat(),
                        renderer.bridgedNoisePaint
                    )
                }
            }
            lastEnd = raw.last
        }
    }
}
