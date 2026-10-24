package com.forgequest.app.domain

/** How a set felt, from the one-tap chips under a finished set. Not tapping counts as OK. */
enum class SetEffort {
    EASY, OK, HARD, PAIN;

    companion object {
        /** Reads the stored name; empty or unknown is OK. */
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
