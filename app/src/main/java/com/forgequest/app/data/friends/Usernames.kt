package com.forgequest.app.data.friends

/** Client copy of the Worker's username rules (worker/src/validate.ts normalizeUsername), kept in sync with web. */
object Usernames {
    private val SYNTAX = Regex("^[A-Za-z0-9_.]{3,20}$")
    private val RESERVED = setOf(
        "admin", "administrator", "root", "system", "support", "help", "forgequest", "forge", "official",
        "staff", "moderator", "mod", "team", "null", "undefined", "me", "you", "friends", "api", "www",
    )

    const val MAX_INPUT = 21 // 20 chars plus an optional leading "@"

    /** Trim, drop one leading "@", ASCII check BEFORE lowercasing, no edge/double dots, not reserved. Null when invalid. */
    fun normalize(raw: String): String? {
        var v = raw.trim()
        if (v.startsWith("@")) v = v.substring(1)
        if (!SYNTAX.matches(v)) return null
        v = v.lowercase(java.util.Locale.ROOT)
        if (v.startsWith(".") || v.endsWith(".") || ".." in v) return null
        if (v in RESERVED || v.startsWith("forgequest")) return null
        return v
    }

    /** Bidi-isolates a username (LRI..PDI) so a Latin handle never reorders an Arabic sentence around it. */
    fun ltr(username: String): String = "\u2066$username\u2069"

    const val SHARE_URL = "https://caelumop.github.io/ForgeQuest/"
}
