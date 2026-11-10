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
    private val latest = 10
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
}
