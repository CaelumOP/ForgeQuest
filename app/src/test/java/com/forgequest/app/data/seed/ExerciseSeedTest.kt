package com.forgequest.app.data.seed

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExerciseSeedTest {
    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun catalogAndMediaIntegrity() {
        val ids = ExerciseCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.size >= 800)

        val assetsDir = File("src/main/assets/ex_media")
        assertTrue("ex_media dir must exist", assetsDir.isDirectory)

        var totalBytes = 0L
        for (ex in ExerciseCatalog.all) {
            assertTrue("AR name missing for ${ex.id}", !ExerciseNamesAr.byId[ex.id].isNullOrBlank())
            val key = ExerciseMedia.byId[ex.id].orEmpty()
            assertEquals("mediaKey mismatch for ${ex.id}", key, ex.mediaKey)
            if (key.isNotEmpty()) {
                for (frame in listOf("0", "1")) {
                    val f = File(assetsDir, "${key}_$frame.webp")
                    assertTrue("Missing asset ${f.name}", f.isFile)
                    assertTrue("Frame ${f.name} exceeds 20 KB", f.length() <= 20 * 1024)
                    totalBytes += f.length()
                }
            }
        }
        assertTrue("Total media size $totalBytes exceeds 18 MB", totalBytes <= 18L * 1024L * 1024L)
    }

    @Test
    fun exerciseLevelsAndStretchesAreValid() {
        val valid = setOf("beginner", "intermediate", "expert")
        for ((id, level) in ExerciseLevels.byId) {
            assertTrue("Unknown exercise $id in ExerciseLevels", ExerciseCatalog.byId.containsKey(id))
            assertTrue("Invalid level $level for $id", level in valid)
        }
        for (ex in ExerciseCatalog.all) {
            if (ex.mediaKey.isNotEmpty()) {
                assertTrue("Missing level for mapped ${ex.id}", ExerciseLevels.levelFor(ex.id) in valid)
            }
            if (ex.pattern == "MOBILITY") {
                assertTrue("Mobility ${ex.id} must be in STRETCH_IDS", ex.id in ExerciseCatalog.STRETCH_IDS)
            }
        }
    }

    @Test
    fun searchMatchesCrossLanguageAliasesAndNormalizedArabic() {
        assertEquals("بنش برس", ExerciseSearch.normalize("بَنْشُ بِرِس"))
        assertEquals("انكلاين", ExerciseSearch.normalize("إنكلاين"))
        assertEquals("اعلي", ExerciseSearch.normalize("أعلى"))
        assertEquals("سمانه", ExerciseSearch.normalize("سمانة"))

        val rdlAr = ExerciseCatalog.byId.getValue("romanian_deadlift")
            .copy(name = ExerciseNamesAr.byId.getValue("romanian_deadlift"))
        val benchEn = ExerciseCatalog.byId.getValue("bench_press")
        val calfEn = ExerciseCatalog.byId.getValue("calf_raise")
        val dbRowEn = ExerciseCatalog.byId.getValue("db_row")

        assertTrue(ExerciseSearch.matches(rdlAr, "RDL"))
        assertTrue(ExerciseSearch.matches(rdlAr, "Romanian Deadlift"))
        assertTrue(ExerciseSearch.matches(rdlAr, "romanian_deadlift"))
        assertTrue(ExerciseSearch.matches(benchEn, "بنش"))
        assertTrue(ExerciseSearch.matches(benchEn, "بَنْش"))
        assertTrue(ExerciseSearch.matches(calfEn, "سمانه"))
        assertTrue(ExerciseSearch.matches(dbRowEn, "منشار"))

        val ohpHits = ExerciseSearch.filter(ExerciseCatalog.all, "OHP")
        assertTrue(ohpHits.any { it.id == "overhead_press" })
        assertFalse(ExerciseSearch.matches(benchEn, "zzzz_no_match"))

        val benchHits = ExerciseSearch.filter(ExerciseCatalog.all.sortedBy { it.name }, "bench")
        assertEquals("bench_press", benchHits.first().id)
    }

    @Test
    fun seedLibraryUpgradesExistingRowsPreservesRenamesAndIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db = database

        database.exercises().upsertAll(
            listOf(
                ExerciseEntity(
                    id = "burpee",
                    name = "البيربي",
                    kind = ExerciseKind.REPS,
                    primary = listOf(Muscle.QUADS, Muscle.CHEST),
                    secondary = listOf(Muscle.SHOULDERS, Muscle.ABS),
                    videoUrl = null,
                    custom = false,
                    equipment = "",
                    pattern = "",
                    setupNote = "Keep steady pace",
                    aliases = "",
                    unitOverride = "",
                    mediaKey = "",
                    pinned = true,
                ),
                ExerciseEntity(
                    id = "bench_press",
                    name = "My Custom Bench",
                    kind = ExerciseKind.WEIGHTED,
                    primary = listOf(Muscle.CHEST),
                    secondary = listOf(Muscle.TRICEPS, Muscle.SHOULDERS),
                    videoUrl = "https://example.com/my-video",
                    custom = false,
                    equipment = "",
                    pattern = "",
                    setupNote = "Pin 4",
                    aliases = "",
                    unitOverride = "LB",
                    mediaKey = "",
                    pinned = false,
                ),
                ExerciseEntity(
                    id = "custom_jammer",
                    name = "Jammer Press",
                    kind = ExerciseKind.WEIGHTED,
                    primary = listOf(Muscle.CHEST),
                    secondary = listOf(Muscle.SHOULDERS),
                    videoUrl = null,
                    custom = true,
                    equipment = "MACHINE",
                    pattern = "HORIZONTAL_PUSH",
                    setupNote = "Seat 2",
                    aliases = "jammer",
                    unitOverride = "",
                    mediaKey = "",
                    pinned = true,
                ),
            )
        )

        val prevLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar"))
            val repo = WorkoutRepository(database)
            repo.seedLibrary()

            assertEquals(ExerciseCatalog.all.size + 1, database.exercises().all().size)

            val burpee = database.exercises().byId("burpee")!!
            assertEquals("بيربي", burpee.name)
            assertEquals("BODYWEIGHT", burpee.equipment)
            assertTrue(burpee.aliases.contains("البيربي"))
            assertEquals("Keep steady pace", burpee.setupNote)
            assertTrue(burpee.pinned)

            val bench = database.exercises().byId("bench_press")!!
            assertEquals("My Custom Bench", bench.name)
            assertEquals("https://example.com/my-video", bench.videoUrl)
            assertTrue(bench.equipment.isNotBlank())
            assertEquals("bench_press", bench.mediaKey)
            assertEquals("Pin 4", bench.setupNote)
            assertEquals("LB", bench.unitOverride)

            val custom = database.exercises().byId("custom_jammer")!!
            assertEquals("Jammer Press", custom.name)
            assertEquals("Seat 2", custom.setupNote)

            // Idempotent second run
            repo.seedLibrary()
            assertEquals(ExerciseCatalog.all.size + 1, database.exercises().all().size)
            assertEquals("My Custom Bench", database.exercises().byId("bench_press")!!.name)
        } finally {
            Locale.setDefault(prevLocale)
        }
    }
}
