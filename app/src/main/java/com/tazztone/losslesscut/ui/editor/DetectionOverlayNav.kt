package com.tazztone.losslesscut.ui.editor

internal fun stepSliderValue(
    value: Float,
    valueFrom: Float,
    valueTo: Float,
    stepSize: Float,
    direction: Int
): Float {
    val step = if (stepSize > 0f) stepSize else 1f
    val rawValue = value + (direction * step)
    val clamped = rawValue.coerceIn(valueFrom, valueTo)
    if (stepSize > 0f) {
        val stepsFromMin = Math.round((clamped - valueFrom) / stepSize)
        val stepped = valueFrom + (stepsFromMin * stepSize)
        return stepped.coerceIn(valueFrom, valueTo)
    }
    return clamped
}

internal fun findMatchTarget(
    ranges: List<LongRange>,
    currentPosMs: Long,
    direction: Int
): Long {
    val targetRange = if (direction > 0) {
        ranges.firstOrNull { it.first > currentPosMs + 50L } ?: ranges.first()
    } else {
        ranges.lastOrNull { it.first < currentPosMs - 50L } ?: ranges.last()
    }
    return targetRange.first
}
