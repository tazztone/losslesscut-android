package com.tazztone.losslesscut.domain.model

import android.content.Context
import androidx.annotation.StringRes

public sealed class UiText {
    public data class DynamicString(val value: String) : UiText()
    public class StringResource(
        @param:StringRes public val resId: Int,
        public vararg val args: Any
    ) : UiText()

    public fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
        }
    }
}
