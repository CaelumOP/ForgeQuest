package com.forgequest.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrading from any old database must reach today's schema: a broken migration would crash the app on update for
 * every tester. The schemas come from app/schemas (Room's exported JSON), loaded as test assets.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one schedules WorkManager jobs, which Robolectric has no setup for.
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {
    private val latest = 13
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrationsChainFromOneToLatestWithoutGaps() {
        val steps = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until latest).map { it to it + 1 }, steps)
        // One exported schema per version, so a new version can't ship without its JSON.
        val schemas = File("schemas/${AppDatabase::class.java.name}").list().orEmpty().filter { it.endsWith(".json") }
        assertEquals((1..latest).map { "$it.json" }.toSet(), schemas.toSet())
    }

    @Test fun everyVersionUpgradesToLatest() {
        for (from in 1 until latest) {
            val name = "$dbName-$from"
            helper.createDatabase(name, from).close()
            // Validates the result against the exported schema of each version on the way.
            for (to in from + 1..latest) {
                helper.runMigrationsAndValidate(name, to, true, *AppDatabase.MIGRATIONS).close()
            }
        }
    }

    @Test fun populatedTenUpgradesWithoutLosingAnUnfinishedWorkout() {
        val name = "$dbName-foundation"
        helper.createDatabase(name, 10).apply {
            execSQL("INSERT INTO exercises (id, name, kind, `primary`, secondary, custom) VALUES ('custom', 'My press', 'WEIGHTED', 'CHEST', '', 1)")
            execSQL("INSERT INTO plans (name, description, source, active, createdAt) VALUES ('My plan', '', 'manual', 1, 1)")
            execSQL("INSERT INTO sessions (title, epochDay, startedAt, note, debrief) VALUES ('Open', 1, 1, '', '')")
            execSQL("INSERT INTO sets (sessionId, exerciseId, itemPosition, block, circuit, setIndex, targetLabel, done, isPr, restSeconds, note, effort) VALUES (1, 'custom', 0, 'Warm-up', 0, 0, '10', 0, 0, 60, '', '')")
            close()
        }
        helper.runMigrationsAndValidate(name, 11, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT setupNote, unitOverride, pinned, mediaKey FROM exercises WHERE id = 'custom'").use {
                assertEquals(true, it.moveToFirst()); assertEquals("", it.getString(0))
                assertEquals("", it.getString(1)); assertEquals(0, it.getInt(2)); assertEquals("", it.getString(3))
            }
            db.query("SELECT scheduleMode, rotationCursor FROM plans").use {
                assertEquals(true, it.moveToFirst()); assertEquals("WEEKDAY", it.getString(0)); assertEquals(0, it.getInt(1))
            }
            db.query("SELECT setType, block FROM sets").use {
                assertEquals(true, it.moveToFirst()); assertEquals("WORKING", it.getString(0)); assertEquals("Warm-up", it.getString(1))
            }
            db.query("SELECT finishedAt FROM sessions").use {
                assertEquals(true, it.moveToFirst()); assertEquals(true, it.isNull(0))
            }
            db.execSQL("INSERT INTO body_measurements (epochDay, metric, valueCm, loggedAt) VALUES (1, 'WAIST', 80.5, 1)")
            db.query("SELECT valueCm FROM body_measurements").use {
                assertEquals(true, it.moveToFirst()); assertEquals(80.5, it.getDouble(0), 0.0)
            }
        }
    }

    @Test fun savedFoodsKeepTheirDataAndGetAnEmptyBarcode() {
        helper.createDatabase("$dbName-barcode", 9).apply {
            execSQL("INSERT INTO foods (name, category, kcal, protein, serving, carbs, pinned, createdAt, place, parts) VALUES ('Kabsa', 'LUNCH', 600, 30.0, '1 plate', 70.0, 0, 1, '', '[]')")
            close()
        }
        val db = helper.runMigrationsAndValidate("$dbName-barcode", 10, true, *AppDatabase.MIGRATIONS)
        db.query("SELECT name, kcal, barcode FROM foods").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("Kabsa", c.getString(0))
            assertEquals(600, c.getInt(1))
            assertEquals("", c.getString(2))
        }
        db.close()
    }

    @Test fun populatedElevenUpgradesWithAutoTickedFalse() {
        val name = "$dbName-autoticked"
        helper.createDatabase(name, 11).apply {
            execSQL("INSERT INTO exercises (id, name, kind, `primary`, secondary, custom) VALUES ('bench', 'Bench', 'WEIGHTED', 'CHEST', '', 0)")
            execSQL("INSERT INTO sessions (title, epochDay, startedAt, note, debrief) VALUES ('Push', 1, 1, '', '')")
            execSQL("INSERT INTO sets (sessionId, exerciseId, itemPosition, block, circuit, setIndex, targetLabel, done, isPr, restSeconds, note, effort, setType) VALUES (1, 'bench', 0, 'Main', 0, 0, '8', 1, 1, 60, '', '', 'WORKING')")
            close()
        }
        helper.runMigrationsAndValidate(name, 12, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT autoTicked, isPr FROM sets").use { c ->
                assertEquals(true, c.moveToFirst())
                assertEquals(0, c.getInt(0))
                assertEquals(1, c.getInt(1))
            }
        }
    }

    @Test fun populatedTwelveUpgradesWithBaselineTestsTable() {
        val name = "$dbName-baseline"
        helper.createDatabase(name, 12).apply {
            execSQL("INSERT INTO exercises (id, name, kind, `primary`, secondary, custom) VALUES ('push_up', 'Push-up', 'REPS', 'CHEST', '', 0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 13, true, *AppDatabase.MIGRATIONS).use { db ->
            db.execSQL("INSERT INTO baseline_tests (exerciseId, kind, reps, seconds, weightKg, createdAt) VALUES ('push_up', 'REPS', 12, NULL, NULL, 1000)")
            db.query("SELECT exerciseId, kind, reps, createdAt FROM baseline_tests").use { c ->
                assertEquals(true, c.moveToFirst())
                assertEquals("push_up", c.getString(0))
                assertEquals("REPS", c.getString(1))
                assertEquals(12, c.getInt(2))
                assertEquals(1000L, c.getLong(3))
            }
        }
    }
}
