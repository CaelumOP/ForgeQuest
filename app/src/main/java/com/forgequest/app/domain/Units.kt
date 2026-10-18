package com.forgequest.app.domain

import java.util.Locale

/** Display unit for weights. Everything is stored in kilograms; only the screen converts. */
enum class WeightUnit(val label: String, private val perKg: Float, val loadStep: Float, val bodyStep: Float) {
    KG("kg", 1f, 2.5f, 0.1f),
    LB("lb", 2.2046225f, 5f, 0.2f);

    fun fromKg(kg: Float): Float = kg * perKg
    fun toKg(value: Float): Float = value / perKg

    /** "72.5 kg" / "160.0 lb" */
    fun format(kg: Float, decimals: Int = 1): String = "%.${decimals}f %s".format(Locale.US, fromKg(kg), label)

    /** Just the number, e.g. for compact "8×60" labels. */
    fun number(kg: Float, decimals: Int = 1): String = "%.${decimals}f".format(Locale.US, fromKg(kg)).removeSuffix(".0")
}
