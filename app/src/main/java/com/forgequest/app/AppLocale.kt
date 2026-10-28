package com.forgequest.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language: "system", "en" or "ar", chosen under Profile → Appearance and applied to every Context
 * the app creates. Arabic always uses Western digits (0-9), the way most Gulf and Levant gym apps show weights
 * and reps; Android's Arabic locale would otherwise format numbers with Arabic-Indic digits.
 */
object AppLocale {
    const val SYSTEM = "system"
    private const val PREFS = "ui_prefs"
    private const val KEY = "language"

    fun current(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    /** Saves the choice. The caller recreates its activity so the new strings show at once. */
    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            // Keep the system's per-app language setting in step, so both places agree.
            context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales =
                if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }

    fun wrap(base: Context): Context {
        val chosen = current(base)
        val wanted = if (chosen == SYSTEM) base.resources.configuration.locales[0] else Locale.forLanguageTag(chosen)
        val fixed = withLatinDigits(wanted) ?: wanted
        if (chosen == SYSTEM && fixed == wanted) return base
        Locale.setDefault(fixed)
        val config = Configuration(base.resources.configuration).apply { setLocale(fixed) }
        return base.createConfigurationContext(config)
    }

    /**
     * Puts the Latin-digit default back. On every configuration change (rotation, dark mode, even with the app in the
     * background) Android resets the default locale to the system's plain one, so until the next activity starts,
     * String.format in a worker would print Arabic-Indic digits.
     */
    fun restoreDefault(context: Context) {
        val chosen = current(context)
        val wanted = if (chosen == SYSTEM) android.content.res.Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(chosen)
        Locale.setDefault(withLatinDigits(wanted) ?: wanted)
    }

    internal fun withLatinDigits(locale: Locale): Locale? =
        if (locale.language == "ar" && locale.getUnicodeLocaleType("nu") != "latn") {
            Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
        } else {
            null
        }
}
