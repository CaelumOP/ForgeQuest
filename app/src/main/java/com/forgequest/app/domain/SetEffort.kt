package com.forgequest.app.domain

/**
 * How a set felt, as a reps-in-reserve scale: EASY (3+ left), HARD (1-2 left), FAILURE (none left).
 * PAIN is not on the scale: it is the separate "it hurt" button. Not tapping counts as OK (neutral). A stored "OK" from
 * before the scale still counts as neutral in the maths but the chips show it as Hard ([chipOf]).
 */
enum class SetEffort {
    EASY, OK, HARD, FAILURE, PAIN;

    /** Reached the limit: HARD or FAILURE hold progression back. */
    val isStrain: Boolean get() = this == HARD || this == FAILURE

    companion object {
        /** The three chips of the scale, in display order; PAIN has its own button. */
        val chips: List<SetEffort> = listOf(EASY, HARD, FAILURE)

        /**
         * What tapping [picked] does to a set's effort and set type (names). Tapping the chosen one again clears it.
         * Failure also makes a WORKING set a FAILURE set; leaving Failure puts it back to WORKING, but only when
         * Failure had set it, never from another type.
         */
        /** The chip a stored value lights up: a stored "OK" shows as Hard; blank is no chip. */
        fun chipOf(raw: String): String = if (raw == OK.name) HARD.name else raw

        fun pick(effort: String, setType: String, picked: SetEffort): Pair<String, String> {
            val type = setType.ifBlank { SetType.WORKING.name }
            val effort = chipOf(effort)
            val next = if (effort == picked.name) "" else picked.name
            return when {
                next == FAILURE.name -> next to if (type == SetType.WORKING.name) SetType.FAILURE.name else type
                effort == FAILURE.name && type == SetType.FAILURE.name -> next to SetType.WORKING.name
                else -> next to type
            }
        }

        /** Reads the stored name; empty or unknown is OK (not tapped). */
        fun parse(raw: String?): SetEffort = entries.firstOrNull { it.name == raw } ?: OK

        // The one word list for notes: Progression and Recovery both read effort through [of].
        private val strainWords = listOf("rir 0", "rpe 10", "fail", "صعب", "فشل")
        private val painWords = listOf("pain", "twinge", "hurt", "ألم")
        private val easyWords = listOf("easy", "rir 3", "سهل")
        private val negations = setOf("no", "not", "without", "never", "zero", "لا", "ما", "بدون", "بلا", "مافي", "ليس", "مو", "مش")
        private val clauseBreak = Regex("""[.,;:!?،؛\n]| but | لكن | بس """)
        private val spaces = Regex("""\s+""")

        /**
         * True when some occurrence of a word isn't negated in its own clause, so "no pain", "pain-free", "didn't fail"
         * and «بدون ألم» don't count. Matched as substrings, so Arabic prefixes («بألم») still count.
         */
        private fun List<String>.foundIn(n: String): Boolean = any { word ->
            var i = n.indexOf(word)
            while (i >= 0) {
                val before = n.substring(0, i).split(clauseBreak).last().split(spaces)
                val after = n.substring(i + word.length)
                val negated = before.any { it in negations || it.endsWith("n't") } ||
                    after.startsWith("-free") || after.startsWith(" free") || after.startsWith("less")
                if (!negated) return@any true
                i = n.indexOf(word, i + 1)
            }
            false
        }

        /**
         * The effort of a set, from its chip or, for sets logged before the chips existed, from words in its note.
         * A tapped chip always wins over the note.
         */
        fun of(effort: String, note: String): SetEffort {
            if (effort.isNotBlank()) return parse(effort)
            val n = note.lowercase()
            return when {
                painWords.foundIn(n) -> PAIN
                strainWords.foundIn(n) -> HARD
                easyWords.foundIn(n) -> EASY
                else -> OK
            }
        }
    }
}
