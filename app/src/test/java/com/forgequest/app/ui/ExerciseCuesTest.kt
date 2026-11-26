package com.forgequest.app.ui

import com.forgequest.app.data.seed.ExerciseCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCuesTest {

    private fun keys(vararg paths: String): Set<String> =
        paths.flatMap { path ->
            Regex("""<string name="(cue_[a-z0-9_]+)"""").findAll(File(path).readText()).map { it.groupValues[1] }
        }.toSet()

    @Test fun everyCatalogIdHasACueEntry() {
        val missing = ExerciseCatalog.all.map { it.id }.filter { exerciseCues(it) == null }
        assertTrue("no cues for $missing", missing.isEmpty())
        assertNull(exerciseCues("not_a_built_in_exercise"))
    }

    @Test fun cueEntriesAreDistinct() {
        val all = ExerciseCatalog.all.flatMap { exerciseCues(it.id)!!.toList() }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun englishFileHasThreeStringsPerCatalogId() {
        val en = keys("src/main/res/values/strings_cues.xml", "src/main/res/values/strings_catalog.xml")
        for (e in ExerciseCatalog.all) {
            val k = e.id.replace(Regex("[^a-z0-9_]"), "_")
            for (suffix in listOf("1", "2", "mistake")) assertTrue("cue_${k}_$suffix missing", "cue_${k}_$suffix" in en)
        }
        assertEquals(ExerciseCatalog.all.size * 3, en.size)
        assertNotNull(en)
    }

    @Test fun arabicFileHasSameKeysInSameOrderAndShortText() {
        val enFiles = listOf("src/main/res/values/strings_cues.xml", "src/main/res/values/strings_catalog.xml")
        val arFiles = listOf("src/main/res/values-ar/strings_cues.xml", "src/main/res/values-ar/strings_catalog.xml")
        val re = Regex("""<string name="(cue_[a-z0-9_]+)">(.*?)</string>""")
        for ((enPath, arPath) in enFiles.zip(arFiles)) {
            val en = re.findAll(File(enPath).readText()).map { it.groupValues[1] }.toList()
            val ar = re.findAll(File(arPath).readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
            assertEquals("key order mismatch between $enPath and $arPath", en, ar.map { it.first })
            for ((k, v) in ar) assertTrue("$k is empty or over 90 chars", v.isNotBlank() && v.length < 90)
        }
        assertEquals(
            keys("src/main/res/values/strings_cues.xml", "src/main/res/values/strings_catalog.xml"),
            keys("src/main/res/values-ar/strings_cues.xml", "src/main/res/values-ar/strings_catalog.xml"),
        )
    }

    @Test fun noCueIsTruncatedOrEndsWithDanglingWord() {
        val danglingEn = setOf(
            "a", "an", "the",
            "of", "in", "on", "at", "to", "for", "with", "from", "by",
            "into", "onto", "upon", "during", "underneath", "among", "against",
            "toward", "towards", "until", "till", "within", "than", "as", "via", "per",
            "beside", "between",
            "and", "or", "but", "nor",
            "if", "when", "while", "where", "that", "which", "who", "whom", "whose",
            "your", "my", "his", "her", "its", "our", "their",
            "these", "those", "some", "any", "every", "each", "either", "neither", "such",
            "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "does", "did",
            "will", "would", "shall", "should", "could", "may", "might", "must",
            "not", "very", "quite", "non-working",
        )
        val validEnPhrasal = listOf(
            Regex("""\b(cave|breathe|elbows|facing|tuck|tucked|pull|pulls|pulling|pulled|sink|sinks)\s+in$""", RegexOption.IGNORE_CASE),
        )
        val danglingAr = setOf(
            "في", "من", "إلى", "الى", "على", "عن", "مع", "بين", "حتى", "عند",
            "نحو", "تجاه", "ضد", "دون", "بدون", "قبل", "بعد", "أثناء", "خلال",
            "حول", "فوق", "تحت", "أمام", "خلف", "بجانب", "وراء",
            "و", "أو", "ثم", "لكن", "بل",
            "أن", "إن", "كي", "لكي", "لأن", "حيث", "إذا", "لو", "عندما", "بينما", "كما", "مثل",
            "كل", "بعض", "جميع", "غير", "سوى",
            "هذا", "هذه", "ذلك", "تلك", "التي", "الذي", "الذين",
            "ما", "لا", "لم", "لن", "قد", "سوف",
            "كان", "تكون", "يكون", "يجب", "يمكن", "عدم", "ال",
        )
        val re = Regex("""<string name="(cue_[a-z0-9_]+)">([\s\S]*?)</string>""")
        fun decode(raw: String): String = raw
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()

        val enFiles = listOf("src/main/res/values/strings_cues.xml", "src/main/res/values/strings_catalog.xml")
        val arFiles = listOf("src/main/res/values-ar/strings_cues.xml", "src/main/res/values-ar/strings_catalog.xml")
        val bad = mutableListOf<String>()
        for (path in enFiles + arFiles) {
            val isAr = path.contains("values-ar")
            for (m in re.findAll(File(path).readText())) {
                val key = m.groupValues[1]
                val text = decode(m.groupValues[2])
                if ("..." in text || "…" in text || ".." in text || ",." in text || ";." in text || ":." in text || "،." in text) {
                    bad += "$key: broken punctuation ($text)"
                }
                if (text.count { it == '"' } % 2 != 0) bad += "$key: unmatched quote ($text)"
                if (text.count { it == '(' } != text.count { it == ')' }) bad += "$key: unmatched paren ($text)"
                if (!Regex("""[.!?»)"]$""").containsMatchIn(text)) bad += "$key: missing terminal punctuation ($text)"
                val clean = text.replace(Regex("""[.!?,;:"'»)]+$"""), "").trim()
                val lastWord = clean.split(Regex("""\s+""")).lastOrNull().orEmpty()
                if (!isAr) {
                    if (lastWord.lowercase() in danglingEn && validEnPhrasal.none { it.containsMatchIn(clean) }) {
                        bad += "$key: dangling EN '$lastWord' ($text)"
                    }
                    if (Regex("""\byour\s+starting$""", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
                        bad += "$key: truncated EN 'your starting' ($text)"
                    }
                } else {
                    if (lastWord in danglingAr || lastWord.endsWith("ـ")) {
                        bad += "$key: dangling AR '$lastWord' ($text)"
                    }
                }
            }
        }
        assertTrue("Truncated/broken cues:\n${bad.joinToString("\n")}", bad.isEmpty())
    }
}
