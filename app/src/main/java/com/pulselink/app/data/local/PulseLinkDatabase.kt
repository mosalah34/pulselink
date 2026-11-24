package com.pulselink.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VitalReadingEntity::class, AlertEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PulseLinkDatabase : RoomDatabase() {
    abstract fun vitalReadingDao(): VitalReadingDao
    abstract fun alertEventDao(): AlertEventDao

    companion object {
        @Volatile
        private var INSTANCE: PulseLinkDatabase? = null

        fun getDatabase(context: Context): PulseLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PulseLinkDatabase::class.java,
                    "pulselink_medical_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

