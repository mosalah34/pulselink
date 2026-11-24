package com.pulselink.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pulselink.app.data.model.AlertEvent
import com.pulselink.app.data.model.AlertType

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alertType: String,
    val measurementValue: Float,
    val thresholdValue: Float,
    val timestamp: Long,
    val resolvedTimestamp: Long? = null,
    val isAcknowledged: Boolean = false,
    val acknowledgedTimestamp: Long? = null,
    val notes: String = ""
) {
    fun toModel(): AlertEvent = AlertEvent(
        id = id,
        alertType = try { AlertType.valueOf(alertType) } catch (e: Exception) { AlertType.HIGH_TEMPERATURE },
        measurementValue = measurementValue,
        thresholdValue = thresholdValue,
        timestamp = timestamp,
        resolvedTimestamp = resolvedTimestamp,
        isAcknowledged = isAcknowledged,
        acknowledgedTimestamp = acknowledgedTimestamp,
        notes = notes
    )

    companion object {
        fun fromModel(model: AlertEvent): AlertEventEntity = AlertEventEntity(
            id = model.id,
            alertType = model.alertType.name,
            measurementValue = model.measurementValue,
            thresholdValue = model.thresholdValue,
            timestamp = model.timestamp,
            resolvedTimestamp = model.resolvedTimestamp,
            isAcknowledged = model.isAcknowledged,
            acknowledgedTimestamp = model.acknowledgedTimestamp,
            notes = model.notes
        )
    }
}

