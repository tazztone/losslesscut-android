package com.tazztone.losslesscut.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StrategyConfigTest {

    @Test
    fun `valid configuration initializes correctly`() {
        val config = StrategyConfig(min = 1f, max = 10f, default = 5f, step = 1f)
        assertEquals(1f, config.min)
        assertEquals(10f, config.max)
        assertEquals(5f, config.default)
        assertEquals(1f, config.step)
    }

    @Test
    fun `valid configuration with min equal to max initializes correctly`() {
        val config = StrategyConfig(min = 5f, max = 5f, default = 5f, step = 1f)
        assertEquals(5f, config.min)
        assertEquals(5f, config.max)
        assertEquals(5f, config.default)
        assertEquals(1f, config.step)
    }

    @Test
    fun `valid configuration with default equal to min initializes correctly`() {
        val config = StrategyConfig(min = 1f, max = 10f, default = 1f, step = 1f)
        assertEquals(1f, config.min)
        assertEquals(10f, config.max)
        assertEquals(1f, config.default)
        assertEquals(1f, config.step)
    }

    @Test
    fun `valid configuration with default equal to max initializes correctly`() {
        val config = StrategyConfig(min = 1f, max = 10f, default = 10f, step = 1f)
        assertEquals(1f, config.min)
        assertEquals(10f, config.max)
        assertEquals(10f, config.default)
        assertEquals(1f, config.step)
    }

    @Test
    fun `min greater than max throws IllegalArgumentException`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            StrategyConfig(min = 10f, max = 5f, default = 7f, step = 1f)
        }
        assertEquals("min (10.0) must be less than or equal to max (5.0)", exception.message)
    }

    @Test
    fun `default less than min throws IllegalArgumentException`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            StrategyConfig(min = 5f, max = 10f, default = 3f, step = 1f)
        }
        assertEquals("default (3.0) must be between min (5.0) and max (10.0)", exception.message)
    }

    @Test
    fun `default greater than max throws IllegalArgumentException`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            StrategyConfig(min = 5f, max = 10f, default = 15f, step = 1f)
        }
        assertEquals("default (15.0) must be between min (5.0) and max (10.0)", exception.message)
    }

    @Test
    fun `step less than or equal to zero throws IllegalArgumentException`() {
        var exception = assertThrows(IllegalArgumentException::class.java) {
            StrategyConfig(min = 1f, max = 10f, default = 5f, step = 0f)
        }
        assertEquals("step (0.0) must be greater than 0", exception.message)

        exception = assertThrows(IllegalArgumentException::class.java) {
            StrategyConfig(min = 1f, max = 10f, default = 5f, step = -1f)
        }
        assertEquals("step (-1.0) must be greater than 0", exception.message)
    }
}