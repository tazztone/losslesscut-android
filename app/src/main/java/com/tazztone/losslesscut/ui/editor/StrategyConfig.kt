package com.tazztone.losslesscut.ui.editor

data class StrategyConfig(
    val min: Float,
    val max: Float,
    val default: Float,
    val step: Float
) {
    init {
        require(min <= max) { "min ($min) must be less than or equal to max ($max)" }
        require(default in min..max) { "default ($default) must be between min ($min) and max ($max)" }
        require(step > 0) { "step ($step) must be greater than 0" }
    }
}
