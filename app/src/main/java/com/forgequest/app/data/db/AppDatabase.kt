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
        ExerciseEntity::class, PlanEntity::class, PlanDayEntity::class, PlanItemEntity::class,
        SessionEntity::class, SetEntity::class, FoodEntity::class, MealEntity::class,
        WeightEntity::class, ChatEntity::class, BadgeEntity::class, ReadinessDayEntity::class, DayNoteEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
    }
}
