package com.pulselink.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pulselink.app.data.model.VitalReading

@Entity(tableName = "vital_readings")
data class VitalReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val rawBpm: Float,
    val adjustedBpm: Float,
    val temperatureCelsius: Float,
    val isStale: Boolean
) {
    fun toModel(): VitalReading = VitalReading(
        id = id,
        timestamp = timestamp,
        rawBpm = rawBpm,
        adjustedBpm = adjustedBpm,
        temperatureCelsius = temperatureCelsius,
        isStale = isStale
    )

    companion object {
        fun fromModel(model: VitalReading): VitalReadingEntity = VitalReadingEntity(
            id = model.id,
            timestamp = model.timestamp,
            rawBpm = model.rawBpm,
            adjustedBpm = model.adjustedBpm,
            temperatureCelsius = model.temperatureCelsius,
            isStale = model.isStale
        )
    }
}

