package com.forgequest.app.domain

object Barcodes {
    /** Digits only, and only lengths a shop product can have (EAN-8, UPC-A, EAN-13); null otherwise. */
    fun clean(raw: String): String? = raw.filter(Char::isDigit).takeIf { it.length == 8 || it.length == 12 || it.length == 13 }

    /**
     * Every way the same product can be written: UPC-A is the EAN-13 with a leading 0, and catalogues use either.
     * The scanned form comes first.
     */
    fun variants(raw: String): List<String> {
        val c = clean(raw) ?: return emptyList()
        return when {
            c.length == 12 -> listOf(c, "0$c")
            c.length == 13 && c.startsWith("0") -> listOf(c, c.drop(1))
            else -> listOf(c)
        }
    }
}
