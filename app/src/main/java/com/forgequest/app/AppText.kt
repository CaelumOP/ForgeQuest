package com.forgequest.app

import android.content.Context
import androidx.annotation.StringRes

/**
 * Resource strings for code that has no Context of its own (error types thrown deep in the data layer).
 * Resolved when read, so an error shows in the language the app is in at that moment.
 */
object AppText {
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context
    }

    /** Falls back to the resource id in plain JVM unit tests, where there are no resources. */
    fun get(@StringRes id: Int, vararg args: Any): String = context?.getString(id, *args) ?: "#$id"
}

/** Base for errors whose message is a string resource. */
abstract class LocalizedException(@StringRes val messageRes: Int, private vararg val args: Any) : Exception() {
    val userMessage: String get() = AppText.get(messageRes, *args)
    override val message: String get() = userMessage
}
