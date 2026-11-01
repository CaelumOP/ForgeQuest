package com.forgequest.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Hidden owner-only switch (tap the version 7 times). Kept in ui_prefs, not in Settings or the backup. */
object PersonalTools {
    private const val PREFS = "ui_prefs"
    private const val KEY = "personal_tools"
    private val state = MutableStateFlow<Boolean?>(null)

    private fun load(context: Context): Boolean = state.value
        ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false).also { state.value = it }

    fun enabled(context: Context): Boolean = load(context)

    /** Live value; call [enabled] once first (or use [flow]) so it is loaded. */
    fun flow(context: Context): StateFlow<Boolean?> { load(context); return state }

    /** Flips the switch and returns the new value. */
    fun toggle(context: Context): Boolean {
        val next = !load(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, next).apply()
        state.value = next
        return next
    }
}
