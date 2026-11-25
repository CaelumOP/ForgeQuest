package com.forgequest.app.data.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.BaselineTestEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupBaselineTestsTest {

    @Test
    fun baselineTestsRoundTripThroughJsonAndRoom() = runBlocking {
        val tests = listOf(
            BaselineTestEntity(id = 1L, exerciseId = "push_up", kind = "REPS", reps = 15, createdAt = 1000L),
            BaselineTestEntity(id = 2L, exerciseId = "plank", kind = "HOLD", seconds = 45, createdAt = 2000L),
            BaselineTestEntity(id = 3L, exerciseId = "bench_press", kind = "WEIGHTED", reps = 8, weightKg = 60f, createdAt = 3000L),
        )
        val file = BackupFile(
            createdAt = 1761200000000L,
            exercises = emptyList(),
            plans = emptyList(),
            planDays = emptyList(),
            planItems = emptyList(),
            sessions = emptyList(),
            sets = emptyList(),
            foods = emptyList(),
            meals = emptyList(),
            weights = emptyList(),
            chat = emptyList(),
            badges = emptyList(),
            baselineTests = tests,
        )

        val encoded = backupJson.encodeToString(BackupFile.serializer(), file)
        assertTrue(encoded.contains("\"baselineTests\""))
        val decoded = backupJson.decodeFromString(BackupFile.serializer(), encoded)
        assertEquals(file, decoded)

        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val b = db.backup()
            b.replaceAll(
                decoded.exercises, decoded.plans, decoded.planDays, decoded.planItems, decoded.sessions, decoded.sets,
                decoded.foods, decoded.meals, decoded.weights, decoded.chat, decoded.badges, decoded.dayNotes,
                decoded.readinessDays, decoded.bodyMeasurements, decoded.baselineTests,
            )
            assertEquals(tests, b.baselineTests())
        } finally {
            db.close()
        }
    }

    @Test
    fun oldBackupJsonWithoutBaselineTestsRestoresCleanly() = runBlocking {
        val oldJson = """
            {
              "version": 2,
              "createdAt": 1761200000000,
              "exercises": [],
              "plans": [],
              "planDays": [],
              "planItems": [],
              "sessions": [],
              "sets": [],
              "foods": [],
              "meals": [],
              "weights": [],
              "chat": [],
              "badges": []
            }
        """.trimIndent()

        val decoded = backupJson.decodeFromString(BackupFile.serializer(), oldJson)
        assertTrue(decoded.baselineTests.isEmpty())

        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            db.baselineTests().insert(
                BaselineTestEntity(exerciseId = "push_up", kind = "REPS", reps = 10, createdAt = 500L),
            )
            val b = db.backup()
            b.replaceAll(
                decoded.exercises, decoded.plans, decoded.planDays, decoded.planItems, decoded.sessions, decoded.sets,
                decoded.foods, decoded.meals, decoded.weights, decoded.chat, decoded.badges, decoded.dayNotes,
                decoded.readinessDays, decoded.bodyMeasurements, decoded.baselineTests,
            )
            assertTrue(db.baselineTests().all().isEmpty())
        } finally {
            db.close()
        }
    }
}
