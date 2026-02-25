package com.tazztone.losslesscut.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

public class UiTextTest {

    @Test
    public fun testDynamicString(): Unit {
        val text = UiText.DynamicString("Hello")
        assertEquals("Hello", text.value)
    }

    @Test
    public fun testStringResource(): Unit {
        val resId = 123
        val text = UiText.StringResource(resId, "arg1", 2)
        assertEquals(resId, text.resId)
        assertEquals(2, text.args.size)
        assertEquals("arg1", text.args[0])
        assertEquals(2, text.args[1])
    }
}
