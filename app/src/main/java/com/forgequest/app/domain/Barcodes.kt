package com.forgequest.app.domain

object Barcodes {
    /** Digits only, and only lengths a shop product can have (EAN-8, UPC-A, EAN-13); null otherwise. */
    fun clean(raw: String): String? = raw.filter(Char::isDigit).takeIf { it.length == 8 || it.length == 12 || it.length == 13 }

    /** The 8-digit short UPC-E (small cans, gum) written out as the 12-digit UPC-A that catalogues use. */
    fun expandUpcE(e: String): String? {
        if (e.length != 8 || !e.all(Char::isDigit)) return null
        val ns = e[0]; val d = e.substring(1, 7); val check = e[7]
        val body = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        return "$ns$body$check"
    }

    /**
     * Every way the same product can be written: UPC-A is the EAN-13 with a leading 0, and catalogues use either.
     * The scanned form comes first.
     */
    fun variants(raw: String): List<String> {
        val c = clean(raw) ?: return emptyList()
        return when {
            c.length == 8 && (c[0] == '0' || c[0] == '1') -> expandUpcE(c)?.let { listOf(c, it, "0$it") } ?: listOf(c)
            c.length == 12 -> listOf(c, "0$c")
            c.length == 13 && c.startsWith("0") -> listOf(c, c.drop(1))
            else -> listOf(c)
        }
    }
}
