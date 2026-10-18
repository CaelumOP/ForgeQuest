package com.forgequest.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Arabic UI with Western digits (0-9), the way most Gulf and Levant gym apps show weights and reps.
 * Android's Arabic locale formats numbers with Arabic-Indic digits by default; adding the Unicode "nu-latn"
 * extension keeps every String.format, stringResource and date formatter on Latin digits.
 */
object AppLocale {
    fun wrap(base: Context): Context {
        val fixed = withLatinDigits(base.resources.configuration.locales[0]) ?: return base
        Locale.setDefault(fixed)
        val config = Configuration(base.resources.configuration).apply { setLocale(fixed) }
        return base.createConfigurationContext(config)
    }

    internal fun withLatinDigits(locale: Locale): Locale? =
        if (locale.language == "ar" && locale.getUnicodeLocaleType("nu") != "latn") {
            Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
        } else {
            null
        }
}
