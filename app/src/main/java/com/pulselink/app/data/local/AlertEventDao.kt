package com.pulselink.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: AlertEventEntity): Long

    @Update
    suspend fun update(alert: AlertEventEntity)

    @Query("SELECT * FROM alert_events WHERE resolvedTimestamp IS NULL ORDER BY timestamp DESC")
    fun getActiveAlerts(): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events WHERE resolvedTimestamp IS NOT NULL ORDER BY timestamp DESC")
    fun getResolvedAlerts(): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events WHERE resolvedTimestamp IS NULL AND alertType = :alertType LIMIT 1")
    suspend fun findActiveAlertByType(alertType: String): AlertEventEntity?

    @Query("UPDATE alert_events SET resolvedTimestamp = :resolvedTime WHERE resolvedTimestamp IS NULL AND alertType = :alertType")
    suspend fun resolveActiveAlerts(alertType: String, resolvedTime: Long)

    @Query("UPDATE alert_events SET isAcknowledged = 1, acknowledgedTimestamp = :ackTime WHERE id = :id")
    suspend fun acknowledgeAlert(id: Long, ackTime: Long)

    @Query("DELETE FROM alert_events")
    suspend fun deleteAll()
}

