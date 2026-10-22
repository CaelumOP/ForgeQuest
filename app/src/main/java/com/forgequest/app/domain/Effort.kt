package com.forgequest.app.domain

/** How a set felt, from the one-tap chips under a finished set. Not tapping counts as OK. */
enum class SetEffort {
    EASY, OK, HARD, PAIN;

    companion object {
        /** Reads the stored name; empty or unknown is OK. */
        fun parse(raw: String?): SetEffort = entries.firstOrNull { it.name == raw } ?: OK

        private val strainWords = listOf("rir 0", "rpe 10", "fail", "صعب")
        private val painWords = listOf("pain", "twinge", "hurt", "ألم")

        /**
         * The effort of a set, from its chip or, for sets logged before the chips existed, from words in its note.
         * A tapped chip always wins over the note.
         */
        fun of(effort: String, note: String): SetEffort {
            if (effort.isNotBlank()) return parse(effort)
            val n = note.lowercase()
            return when {
                painWords.any { it in n } -> PAIN
                strainWords.any { it in n } -> HARD
                else -> OK
            }
        }
    }
}
