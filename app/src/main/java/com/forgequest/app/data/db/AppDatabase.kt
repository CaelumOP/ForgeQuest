package com.forgequest.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ExerciseEntity::class, PlanEntity::class, PlanDayEntity::class, PlanItemEntity::class,
        SessionEntity::class, SetEntity::class, FoodEntity::class, MealEntity::class,
        WeightEntity::class, ChatEntity::class, BadgeEntity::class,
    ],
    version = 1,
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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
