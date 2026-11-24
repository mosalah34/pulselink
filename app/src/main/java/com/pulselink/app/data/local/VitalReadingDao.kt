package com.pulselink.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: VitalReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<VitalReadingEntity>): List<Long>

    @Query("SELECT * FROM vital_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentReadings(limit: Int = 100): Flow<List<VitalReadingEntity>>

    @Query("SELECT * FROM vital_readings WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getReadingsInRange(startTime: Long, endTime: Long): Flow<List<VitalReadingEntity>>

    @Query("SELECT * FROM vital_readings ORDER BY timestamp DESC")
    suspend fun getAllReadingsList(): List<VitalReadingEntity>

    @Query("SELECT COUNT(*) FROM vital_readings")
    suspend fun getCount(): Int

    @Query("DELETE FROM vital_readings")
    suspend fun deleteAll()

    @Query("DELETE FROM vital_readings WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int
}

