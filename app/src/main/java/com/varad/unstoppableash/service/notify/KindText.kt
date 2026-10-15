package com.varad.unstoppableash.service.notify

import android.content.Context
import com.varad.unstoppableash.R

/** The same labels as Home's kindLabel, for places without Compose (notifications). */
object KindText {
    fun of(context: Context, kind: String, text: String?): String = when (kind) {
        "home_safe" -> context.getString(R.string.kind_home_safe)
        "on_my_way" -> context.getString(R.string.kind_on_my_way)
        "thinking_of_you" -> context.getString(R.string.kind_thinking_of_you)
        "call_me" -> context.getString(R.string.kind_call_me)
        "are_you_ok" -> context.getString(R.string.kind_are_you_ok)
        "arrived" -> if (text != null) context.getString(R.string.kind_arrived_at, text) else context.getString(R.string.kind_arrived)
        else -> text ?: context.getString(R.string.kind_custom)
    }
}
