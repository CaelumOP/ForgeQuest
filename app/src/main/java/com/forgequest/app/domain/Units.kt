package com.forgequest.app.domain

import java.util.Locale

/** Display unit for weights. Everything is stored in kilograms; only the screen converts. */
enum class WeightUnit(
    val code: String,
    private val arabic: String,
    private val perKg: Float,
    val loadStep: Float,
    val bodyStep: Float,
) {
    KG("kg", "كغ", 1f, 2.5f, 0.1f),
    LB("lb", "رطل", 2.2046225f, 5f, 0.2f);

    private val isArabic get() = Locale.getDefault().language == "ar"

    /** The unit as the reader writes it: "kg" / "كغ". */
    val label: String get() = if (isArabic) arabic else code

    fun fromKg(kg: Float): Float = kg * perKg
    fun toKg(value: Float): Float = value / perKg

    /** "72.5 kg" / "160.0 lb" */
    // Isolated so the number and unit keep their order: left-to-right "72.5 kg", right-to-left "72.5 كغ".
    fun format(kg: Float, decimals: Int = 1): String =
        (if (isArabic) "\u2067%.${decimals}f %s\u2069" else "\u2066%.${decimals}f %s\u2069").format(Locale.US, fromKg(kg), label)

    /** Just the number, e.g. for compact "8×60" labels. */
    fun number(kg: Float, decimals: Int = 1): String = "%.${decimals}f".format(Locale.US, fromKg(kg)).removeSuffix(".0")

    companion object {
        /** Resolves both canonical codes ("kg", "lb") and localized labels ("كغ", "رطل") safely. */
        fun fromCodeOrLabel(raw: String?): WeightUnit {
            val cleaned = raw?.trim().orEmpty()
            return if (cleaned.equals("lb", ignoreCase = true) || cleaned.equals("lbs", ignoreCase = true) || cleaned == "رطل") {
                LB
            } else {
                KG
            }
        }
    }
}
