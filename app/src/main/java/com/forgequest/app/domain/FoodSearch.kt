package com.forgequest.app.domain

import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.min

/**
 * Forgiving search over the person's own saved foods and restaurants (nothing from outside): Arabic spelling variants
 * (أ/ا، ة/ه، ى/ي), tashkeel, vowel letters (كفتيريا = كافتيرا), Arabic and English by sound (شاورما = shawarma), typos and
 * words typed together or apart (كوكا كولا = كوكاكولا). The web app has the same rules in web/src/domain/food-search.ts;
 * both test against app/src/test/resources/food_search_cases.json.
 */
object FoodSearch {
    private val MARKS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val REPEATS = Regex("(.)\\1+")
    private val LATIN = Regex("[a-z]")

    /** Lowercase, no tashkeel or accents, one form per Arabic letter, Western digits, words split by single spaces. */
    fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "") // أ إ آ ؤ ئ lose their hamza/madda here
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when (ch) {
                'ٱ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى', 'ی', 'ې' -> sb.append('ي')
                'ک' -> sb.append('ك')
                'ء', 'ـ', '\'', '’', '`' -> Unit // Barn's = barns
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(NOT_WORD, " ").trim()
    }

    /** Words of [s], without a leading "ال" on longer words; one-letter words (like a lone "و") are left out. */
    fun words(s: String): List<String> =
        normalize(s).split(' ').filter { it.length > 1 }.map { if (it.length > 4) it.removePrefix("وال").removePrefix("ال") else it }

    /** [w] and, when it starts with the "و" of "and", the word without it (افراح وتهاني = افراح و تهاني). */
    private fun variants(w: String): List<String> = if (w.length > 3 && w.startsWith('و')) listOf(w, w.substring(1)) else listOf(w)

    private val ARABIC_SOUND = mapOf(
        'ب' to "b", 'ت' to "t", 'ث' to "s", 'ج' to "g", 'ح' to "h", 'خ' to "k", 'د' to "d", 'ذ' to "z", 'ر' to "r", 'ز' to "z",
        'س' to "s", 'ش' to "x", 'ص' to "s", 'ض' to "d", 'ط' to "t", 'ظ' to "z", 'ع' to "", 'غ' to "g", 'ف' to "f", 'ق' to "k",
        'ك' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ه' to "h", 'و' to "w", 'ي' to "y", 'ا' to "",
    )

    /** The consonant sounds of one word, the same for Arabic and English spellings (كفتيريا = cafeteria = kftr). */
    fun soundKey(w: String): String {
        var s = if (LATIN.containsMatchIn(w)) {
            // Double letters are one sound in English spelling (latte, coffee); Arabic writes them once anyway.
            w.replace(REPEATS, "$1")
                .replace(Regex("sh|ch"), "x").replace("kh", "k").replace("gh", "g").replace("th", "s").replace("ph", "f")
                .replace(Regex("ck|q|c"), "k").replace('v', 'f').replace('p', 'b').replace('j', 'g')
        } else {
            // A final ه/ة is a vowel: كبسة = kabsa.
            w.removeSuffix("ه").map { ARABIC_SOUND[it] ?: it.toString() }.joinToString("")
        }
        s = s.replace('z', 's') // بارنز = barns
        if (s.isEmpty()) return ""
        // Keep the first letter; drop vowels and weak letters after it.
        val key = s[0] + s.substring(1).filterNot { it in "aeiouwy" }
        return key.trimStart('a', 'e', 'i', 'o', 'u')
    }

    private fun typoDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    private fun typosAllowed(length: Int) = when {
        length <= 3 -> 0
        length <= 6 -> 1
        else -> 2
    }

    /**
     * How well one typed word matches one saved word: 1 the same, .9 starts with it, .8 sounds the same, .75 inside it,
     * .7 sound starts with it, .65 a short sound between longer words (latte = لاتيه), .6 a typo or two, 0 no match.
     */
    private fun wordScore(q: String, w: String): Float {
        if (q == w) return 1f
        if (q.length >= 2 && w.startsWith(q)) return 0.9f
        val kq = soundKey(q)
        val kw = soundKey(w)
        if (q.length >= 3 && kq.length >= 3 && kq == kw) return 0.8f
        if (q.length >= 3 && w.contains(q)) return 0.75f
        if (q.length >= 3 && kq.length >= 3 && kw.startsWith(kq)) return 0.7f
        if (q.length >= 4 && w.length >= 4 && kq.length == 2 && kq == kw) return 0.65f
        if (typoDistance(q, w) <= typosAllowed(q.length)) return 0.6f
        return 0f
    }

    /**
     * How well [query] matches a food: every typed word must match a word of its name or restaurant (0 otherwise); the
     * score is their average. Words typed apart can match a name written as one word, and the other way round.
     */
    fun score(query: String, name: String, place: String = ""): Float {
        val nameWords = words(name)
        val saved = (nameWords + words(place)).flatMap(::variants) + nameWords.joinToString("")
        val typed = words(query)
        if (typed.isEmpty()) return 0f
        fun all(list: List<String>): Float {
            var total = 0f
            for (q in list) {
                val best = variants(q).maxOf { v -> saved.maxOfOrNull { wordScore(v, it) } ?: 0f }
                if (best == 0f) return 0f
                total += best
            }
            return total / list.size
        }
        return maxOf(all(typed), if (typed.size > 1) all(listOf(typed.joinToString(""))) else 0f)
    }

    /** Venue words: they say what kind of place it is, not which one, so they don't count when comparing restaurants. */
    private val VENUE_WORDS: Set<String> = listOf(
        "مطعم", "مطاعم", "كفتيريا", "كافيه", "كوفي", "مقهى", "شاورما", "بروستد", "مطبخ", "بوفيه", "مخبز", "مخابز", "حلويات",
        "فطائر", "مشويات", "restaurant", "cafe", "coffee", "cafeteria", "kitchen", "grill", "bakery", "shawarma", "broast",
    ).map { soundKey(words(it).first()) }.toSet()

    private fun placeKeys(p: String): Set<String> = words(p).map { soundKey(variants(it).last()) }.filter { it.isNotEmpty() }.toSet()

    /**
     * How alike two restaurant names are, 0 to 1. Venue words count for nothing; other words count more the rarer they
     * are among [allPlaces] (the person's restaurants). شاورما افراح وتهاني and كفتيريا افراح وتهاني come out at 1.
     */
    fun placeSimilarity(a: String, b: String, allPlaces: Collection<String>): Float {
        if (normalize(a).replace(" ", "") == normalize(b).replace(" ", "")) return if (a.isBlank()) 0f else 1f
        val docs = allPlaces.map(::placeKeys)
        fun weight(k: String): Double = if (k in VENUE_WORDS) 0.0 else ln((docs.size + 1.0) / (1 + docs.count { k in it })) + 0.1
        val ka = placeKeys(a)
        val kb = placeKeys(b)
        var shared = 0.0
        var all = 0.0
        for (k in ka + kb) {
            val w = weight(k)
            all += w
            if (k in ka && k in kb) shared += w
        }
        return if (all == 0.0) 0f else (shared / all).toFloat()
    }

    fun samePlace(a: String, b: String, allPlaces: Collection<String>): Boolean = placeSimilarity(a, b, allPlaces) >= 0.5f

    /**
     * The same saved food: the same name (ignoring case, tashkeel and أ/ا، ة/ه، ى/ي) at the same restaurant, or both with
     * none. Names are compared by spelling, not sound: Coke and Cake sound alike but are different foods.
     */
    fun sameFood(nameA: String, placeA: String, nameB: String, placeB: String, allPlaces: Collection<String>): Boolean {
        if (words(nameA) != words(nameB)) return false
        if (placeA.isBlank() || placeB.isBlank()) return placeA.isBlank() && placeB.isBlank()
        return samePlace(placeA, placeB, allPlaces)
    }
}
