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
        WeightEntity::class, ChatEntity::class, BadgeEntity::class,
    ],
    version = 2,
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

    companion object {
        const val NAME = "forgequest.db"

        /** v1 → v2: per-set notes. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sets ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
