package com.forgequest.app.data.import

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.csvimport.CsvExerciseMatcher
import com.forgequest.app.data.csvimport.CsvImporter
import com.forgequest.app.data.csvimport.CsvParser
import com.forgequest.app.data.csvimport.CsvSource
import com.forgequest.app.data.csvimport.ExerciseMapping
import com.forgequest.app.data.csvimport.MatchStatus
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.SetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CsvImportTest {

    private fun readFixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("csv/$name")!!.bufferedReader().use { it.readText() }

    @Test
    fun parsesStrongHevyAndFitNotesFixtures() {
        val (strongSrc, strongRows) = CsvParser.parseRows(readFixture("strong_sample.csv"))
        assertEquals(CsvSource.STRONG, strongSrc)
        assertEquals(4, strongRows.size) // Rest Timer row excluded
        assertEquals(SetType.WARMUP.name, strongRows[0].setType)
        assertEquals(0, strongRows[0].setIndex)
        assertEquals(80f, strongRows[1].weightKg!!, 0.01f)

        val (hevySrc, hevyRows) = CsvParser.parseRows(readFixture("hevy_sample.csv"))
        assertEquals(CsvSource.HEVY, hevySrc)
        assertEquals(4, hevyRows.size)
        assertEquals(SetType.WARMUP.name, hevyRows[0].setType)
        assertEquals(SetType.DROPSET.name, hevyRows[2].setType)
        assertEquals(SetType.FAILURE.name, hevyRows[3].setType)

        val (fitSrc, fitRows) = CsvParser.parseRows(readFixture("fitnotes_sample.csv"))
        assertEquals(CsvSource.FITNOTES, fitSrc)
        assertEquals(4, fitRows.size)
        assertEquals(60, fitRows[2].seconds) // 01:00 -> 60s
        assertEquals(135f / 2.2046225f, fitRows[3].weightKg!!, 0.2f) // lbs -> kg
    }

    @Test
    fun dateParsingIsTimezoneSafeForCalendarDates() {
        val expected = LocalDate.of(2026, 11, 10).toEpochDay()
        assertEquals(expected, CsvParser.parseEpochDay("2026-11-10 00:05:00", ZoneId.of("America/Los_Angeles")))
        assertEquals(expected, CsvParser.parseEpochDay("2026-11-10 23:55:00", ZoneId.of("Asia/Riyadh")))
        assertEquals(expected, CsvParser.parseEpochDay("10 Nov 2026, 17:00", ZoneId.of("UTC")))
    }

    @Test
    fun matchesBuiltInExercisesAndLeavesUnknownsForMapping() {
        val matches = CsvExerciseMatcher.matchAll(
            listOf("Bench Press (Barbell)", "Lat Pulldown (Cable)", "Squat", "Plank", "Custom Cable Fly"),
            ExerciseCatalog.all,
        )
        assertEquals("bench_press", matches["Bench Press (Barbell)"]?.matchedId)
        assertEquals("lat_pulldown", matches["Lat Pulldown (Cable)"]?.matchedId)
        assertEquals("back_squat", matches["Squat"]?.matchedId)
        assertEquals("plank", matches["Plank"]?.matchedId)
        assertEquals(MatchStatus.UNMATCHED, matches["Custom Cable Fly"]?.status)
    }

    @Test
    fun transactionalImportCreatesCustomAndDedupesOnReimport() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            db.exercises().insertAll(ExerciseCatalog.all)
            val importer = CsvImporter(db)
            val csv = readFixture("strong_sample.csv")
            val preview = importer.preview(csv)
            assertEquals(1, preview.totalWorkouts)
            assertEquals(4, preview.newSetsCount)
            assertEquals(listOf("Custom Cable Fly"), preview.unmatchedNames)

            val first = importer.importTransactional(
                preview,
                mapOf("Custom Cable Fly" to ExerciseMapping.CreateCustom("Custom Cable Fly", ExerciseKind.WEIGHTED, Muscle.CHEST)),
            )
            assertEquals(1, first.importedWorkouts)
            assertEquals(4, first.importedSets)
            assertTrue(db.exercises().all().any { it.custom && it.name == "Custom Cable Fly" })

            // Re-importing the exact same CSV skips all sets cleanly via date+exercise+setIndex dedupe.
            val preview2 = importer.preview(csv)
            assertEquals(4, preview2.duplicateSetsCount)
            val second = importer.importTransactional(preview2, emptyMap())
            assertEquals(0, second.importedWorkouts)
            assertEquals(0, second.importedSets)
            assertEquals(4, second.skippedDuplicates)
        } finally {
            db.close()
        }
    }
}
