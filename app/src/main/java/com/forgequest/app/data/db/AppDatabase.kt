package com.forgequest.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BodyMeasurementEntity::class, ExerciseEntity::class, PlanEntity::class, PlanDayEntity::class, PlanItemEntity::class,
        SessionEntity::class, SetEntity::class, FoodEntity::class, MealEntity::class,
        WeightEntity::class, ChatEntity::class, BadgeEntity::class, ReadinessDayEntity::class, DayNoteEntity::class,
        BaselineTestEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bodyMeasurements(): BodyMeasurementDao
    abstract fun exercises(): ExerciseDao
    abstract fun plans(): PlanDao
    abstract fun sessions(): SessionDao
    abstract fun food(): FoodDao
    abstract fun weights(): WeightDao
    abstract fun chat(): ChatDao
    abstract fun badges(): BadgeDao
    abstract fun backup(): BackupDao
    abstract fun readiness(): ReadinessDao
    abstract fun dayNotes(): DayNoteDao
    abstract fun baselineTests(): BaselineTestDao

    companion object {
        const val NAME = "forgequest.db"

        /** v1 → v2: per-set notes. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sets ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 → v3: technical detail behind coach error bubbles. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat ADD COLUMN detail TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3 → v4: optional carbs on foods and logged meals. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN carbs REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meals ADD COLUMN carbs REAL NOT NULL DEFAULT 0")
            }
        }

        /** v4 → v5: daily Fitbit readiness scores. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS readiness_days (epochDay INTEGER NOT NULL, recovery INTEGER NOT NULL, sleepPerf INTEGER NOT NULL, " +
                        "sleepMin INTEGER NOT NULL, strain REAL NOT NULL, hrv REAL NOT NULL, rhr REAL NOT NULL, feltGreat INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, PRIMARY KEY(epochDay))"
                )
            }
        }

        /** v5 → v6: a journal note on each workout. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v6 → v7: one-tap set effort, the AI debrief on each workout, and excused days. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sets ADD COLUMN effort TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN debrief TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS day_notes (epochDay INTEGER NOT NULL, tag TEXT NOT NULL, note TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, PRIMARY KEY(epochDay))"
                )
            }
        }

        /** v7 → v8: a restaurant and removable parts on saved foods. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN place TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE foods ADD COLUMN parts TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** v8 → v9: each workout keeps its planned set count, so editing the plan later can't change old scores. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN plannedSets INTEGER")
                db.execSQL("ALTER TABLE sets ADD COLUMN doneAt INTEGER")
            }
        }

        /** v9 → v10: a saved food can remember the barcode it was scanned from. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN barcode TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN equipment TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN pattern TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN setupNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN aliases TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN unitOverride TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN mediaKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plans ADD COLUMN scheduleMode TEXT NOT NULL DEFAULT 'WEEKDAY'")
                db.execSQL("ALTER TABLE plans ADD COLUMN rotationCursor INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plan_items ADD COLUMN supersetGroup INTEGER")
                db.execSQL("ALTER TABLE plan_items ADD COLUMN autoWarmup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plan_items ADD COLUMN progressionMode TEXT NOT NULL DEFAULT 'DOUBLE'")
                db.execSQL("ALTER TABLE sets ADD COLUMN setType TEXT NOT NULL DEFAULT 'WORKING'")
                db.execSQL("CREATE TABLE IF NOT EXISTS body_measurements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, epochDay INTEGER NOT NULL, metric TEXT NOT NULL, valueCm REAL NOT NULL, loggedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_body_measurements_epochDay ON body_measurements (epochDay)")
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sets ADD COLUMN autoTicked INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v12 → v13: starting-point baseline test results. */
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS baseline_tests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, exerciseId TEXT NOT NULL, kind TEXT NOT NULL, reps INTEGER, seconds INTEGER, weightKg REAL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baseline_tests_exerciseId ON baseline_tests (exerciseId)")
            }
        }

        /** Every upgrade step in order, 1 to the current version; the migration test walks the same list. */
        internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
