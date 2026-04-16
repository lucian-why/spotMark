package com.chengjiguanjia.spotmark.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SavedSpotEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(SpotTypeConverters::class)
abstract class SpotMarkDatabase : RoomDatabase() {
    abstract fun savedSpotDao(): SavedSpotDao

    companion object {
        @Volatile
        private var instance: SpotMarkDatabase? = null

        fun get(context: Context): SpotMarkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpotMarkDatabase::class.java,
                    "spotmark.db",
                ).build().also { instance = it }
            }
    }
}
