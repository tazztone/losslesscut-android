package com.tazztone.losslesscut.util

import android.content.Context
import com.tazztone.losslesscut.domain.model.UiText

@Suppress("SpreadOperator")
fun UiText.asString(context: Context): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResource -> context.getString(resId, *args)
}
