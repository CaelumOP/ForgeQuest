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
}
