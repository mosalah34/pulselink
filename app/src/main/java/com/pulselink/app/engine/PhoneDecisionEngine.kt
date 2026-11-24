package com.pulselink.app.engine

import com.pulselink.app.data.model.AlertEvent
import com.pulselink.app.data.model.AlertType
import com.pulselink.app.data.model.PatientStatus
import com.pulselink.app.data.model.ThresholdSettings
import com.pulselink.app.data.model.VitalReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DecisionResult(
    val status: PatientStatus,
    val activeAlerts: List<AlertEvent>,
    val primaryAlert: AlertEvent?,
    val isStale: Boolean
)

class PhoneDecisionEngine(
    var settings: ThresholdSettings = ThresholdSettings()
) {
    private var lastReadingTimestamp: Long = 0L
    private var lastValidReading: VitalReading? = null

    // Track active alert types to avoid duplicates
    private val activeAlertsMap = mutableMapOf<AlertType, AlertEvent>()

    private val _decisionFlow = MutableStateFlow(
        DecisionResult(
            status = PatientStatus.IDLE,
            activeAlerts = emptyList(),
            primaryAlert = null,
            isStale = false
        )
    )
    val decisionFlow: StateFlow<DecisionResult> = _decisionFlow.asStateFlow()

    /**
     * Analyzes incoming vital reading according to configurable experimental thresholds.
     */
    fun evaluateReading(
        reading: VitalReading,
        currentTime: Long = System.currentTimeMillis()
    ): DecisionResult {
        if (!reading.isValid) {
            return checkStaleness(currentTime)
        }

        lastReadingTimestamp = currentTime
        lastValidReading = reading

        val evaluatedAlerts = mutableListOf<AlertEvent>()

        // 1. Evaluate Temperature Alert (Highest priority per spec)
        if (!reading.temperatureCelsius.isNaN()) {
            if (reading.temperatureCelsius >= settings.tempThresholdCelsius) {
                val existing = activeAlertsMap[AlertType.HIGH_TEMPERATURE]
                val alert = existing ?: AlertEvent(
                    id = System.nanoTime(),
                    alertType = AlertType.HIGH_TEMPERATURE,
                    measurementValue = reading.temperatureCelsius,
                    thresholdValue = settings.tempThresholdCelsius,
                    timestamp = currentTime,
                    notes = "Probe temperature reached ${String.format("%.1f", reading.temperatureCelsius)}°C (Threshold: ${settings.tempThresholdCelsius}°C)"
                )
                activeAlertsMap[AlertType.HIGH_TEMPERATURE] = alert
                evaluatedAlerts.add(alert)
            } else {
                activeAlertsMap.remove(AlertType.HIGH_TEMPERATURE)
            }
        }

        // 2. Evaluate Heart Rate Alert (Using calibrated adjusted BPM)
        if (!reading.adjustedBpm.isNaN()) {
            val adjustedBpm = reading.adjustedBpm
            when {
                adjustedBpm < settings.hrMinBpm -> {
                    activeAlertsMap.remove(AlertType.HIGH_HEART_RATE)
                    val existing = activeAlertsMap[AlertType.LOW_HEART_RATE]
                    val alert = existing ?: AlertEvent(
                        id = System.nanoTime(),
                        alertType = AlertType.LOW_HEART_RATE,
                        measurementValue = adjustedBpm,
                        thresholdValue = settings.hrMinBpm,
                        timestamp = currentTime,
                        notes = "Bradycardia detected: ${adjustedBpm.toInt()} BPM (Minimum threshold: ${settings.hrMinBpm.toInt()} BPM)"
                    )
                    activeAlertsMap[AlertType.LOW_HEART_RATE] = alert
                    evaluatedAlerts.add(alert)
                }
                adjustedBpm > settings.hrMaxBpm -> {
                    activeAlertsMap.remove(AlertType.LOW_HEART_RATE)
                    val existing = activeAlertsMap[AlertType.HIGH_HEART_RATE]
                    val alert = existing ?: AlertEvent(
                        id = System.nanoTime(),
                        alertType = AlertType.HIGH_HEART_RATE,
                        measurementValue = adjustedBpm,
                        thresholdValue = settings.hrMaxBpm,
                        timestamp = currentTime,
                        notes = "Tachycardia detected: ${adjustedBpm.toInt()} BPM (Maximum threshold: ${settings.hrMaxBpm.toInt()} BPM)"
                    )
                    activeAlertsMap[AlertType.HIGH_HEART_RATE] = alert
                    evaluatedAlerts.add(alert)
                }
                else -> {
                    // Normal range
                    activeAlertsMap.remove(AlertType.LOW_HEART_RATE)
                    activeAlertsMap.remove(AlertType.HIGH_HEART_RATE)
                }
            }
        }

        // Sort: Temperature alert always gets highest priority
        val sortedAlerts = activeAlertsMap.values.sortedWith(
            compareByDescending<AlertEvent> { it.alertType.priority }
                .thenByDescending { it.timestamp }
        )

        val primaryAlert = sortedAlerts.firstOrNull()

        val patientStatus = when {
            primaryAlert?.alertType == AlertType.HIGH_TEMPERATURE -> PatientStatus.CRITICAL
            primaryAlert?.alertType == AlertType.HIGH_HEART_RATE -> PatientStatus.WARNING
            primaryAlert?.alertType == AlertType.LOW_HEART_RATE -> PatientStatus.WARNING
            else -> PatientStatus.NORMAL
        }

        val result = DecisionResult(
            status = patientStatus,
            activeAlerts = sortedAlerts,
            primaryAlert = primaryAlert,
            isStale = false
        )
        _decisionFlow.value = result
        return result
    }

    /**
     * Checks if sensor data has become stale based on staleTimeoutMillis.
     */
    fun checkStaleness(currentTime: Long = System.currentTimeMillis()): DecisionResult {
        val isStale = if (lastReadingTimestamp == 0L) {
            true
        } else {
            (currentTime - lastReadingTimestamp) > settings.staleTimeoutMillis
        }

        val currentVal = _decisionFlow.value
        val updatedResult = if (isStale && currentVal.status != PatientStatus.IDLE) {
            currentVal.copy(
                isStale = true,
                status = if (currentVal.activeAlerts.isEmpty()) PatientStatus.WARNING else currentVal.status
            )
        } else {
            currentVal.copy(isStale = isStale)
        }
        _decisionFlow.value = updatedResult
        return updatedResult
    }

    fun acknowledgeAlert(alertId: Long) {
        val entry = activeAlertsMap.entries.firstOrNull { it.value.id == alertId }
        entry?.let {
            val updated = it.value.copy(
                isAcknowledged = true,
                acknowledgedTimestamp = System.currentTimeMillis()
            )
            activeAlertsMap[it.key] = updated
            val currentList = activeAlertsMap.values.sortedWith(
                compareByDescending<AlertEvent> { a -> a.alertType.priority }
            )
            _decisionFlow.value = _decisionFlow.value.copy(
                activeAlerts = currentList,
                primaryAlert = currentList.firstOrNull()
            )
        }
    }

    fun reset() {
        activeAlertsMap.clear()
        lastReadingTimestamp = 0L
        lastValidReading = null
        _decisionFlow.value = DecisionResult(
            status = PatientStatus.IDLE,
            activeAlerts = emptyList(),
            primaryAlert = null,
            isStale = false
        )
    }
}

