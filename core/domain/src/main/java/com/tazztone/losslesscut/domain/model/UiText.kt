package com.tazztone.losslesscut.domain.model

public sealed class UiText {
    public data class DynamicString(val value: String) : UiText()
    public class StringResource(
        public val resId: Int,
        public vararg val args: Any
    ) : UiText()
}
