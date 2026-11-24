package com.pulselink.app.engine

import com.pulselink.app.data.model.AlarmAckState
import com.pulselink.app.data.model.AlarmCondition
import com.pulselink.app.data.model.AlertEvent
import com.pulselink.app.data.model.AlertType
import com.pulselink.app.data.model.PhoneSoundState
import com.pulselink.app.data.model.ThresholdSettings
import com.pulselink.app.data.model.VitalReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmStateMachine(
    var settings: ThresholdSettings = ThresholdSettings(),
    private val onStartPhoneAlarm: (AlertType) -> Unit = {},
    private val onStopPhoneAlarm: () -> Unit = {},
    private val onSendBuzzerOn: () -> Unit = {},
    private val onSendBuzzerOff: () -> Unit = {},
    private val onAlertCreated: (AlertEvent) -> Unit = {},
    private val onAlertAcknowledged: (Long) -> Unit = {},
    private val onAlertResolved: (AlertType) -> Unit = {}
) {
    private val _condition = MutableStateFlow(AlarmCondition.NORMAL)
    val condition: StateFlow<AlarmCondition> = _condition.asStateFlow()

    private val _ackState = MutableStateFlow(AlarmAckState.NOT_ACKNOWLEDGED)
    val ackState: StateFlow<AlarmAckState> = _ackState.asStateFlow()

    private val _phoneSoundState = MutableStateFlow(PhoneSoundState.STOPPED)
    val phoneSoundState: StateFlow<PhoneSoundState> = _phoneSoundState.asStateFlow()

    private val _activeAlert = MutableStateFlow<AlertEvent?>(null)
    val activeAlert: StateFlow<AlertEvent?> = _activeAlert.asStateFlow()

    /**
     * Evaluates incoming vital reading and executes appropriate state transitions.
     */
    @Synchronized
    fun processReading(reading: VitalReading, currentTime: Long = System.currentTimeMillis()): AlarmCondition {
        if (!reading.isValid) {
            return _condition.value
        }

        // Determine new condition based on priority rules:
        // 1. High Temperature (>= 35.0 C) has absolute priority
        // 2. High HR (> 100 BPM)
        // 3. Low HR (< 50 BPM)
        // 4. Normal (50 - 100 BPM)
        val newCondition: AlarmCondition = when {
            !reading.temperatureCelsius.isNaN() && reading.temperatureCelsius >= settings.tempThresholdCelsius -> {
                AlarmCondition.TEMP_HIGH
            }
            !reading.adjustedBpm.isNaN() && reading.adjustedBpm < settings.hrMinBpm -> {
                AlarmCondition.HR_LOW
            }
            !reading.adjustedBpm.isNaN() && reading.adjustedBpm > settings.hrMaxBpm -> {
                AlarmCondition.HR_HIGH
            }
            else -> {
                AlarmCondition.NORMAL
            }
        }

        val previousCondition = _condition.value
        val previousAck = _ackState.value

        when {
            // Case 1: Returned to NORMAL
            newCondition == AlarmCondition.NORMAL -> {
                if (previousCondition != AlarmCondition.NORMAL) {
                    val resolvedType = previousCondition.alertType
                    if (resolvedType != null) {
                        onAlertResolved(resolvedType)
                    }

                    _condition.value = AlarmCondition.NORMAL
                    _ackState.value = AlarmAckState.NOT_ACKNOWLEDGED
                    _activeAlert.value = null

                    // Silence phone and buzzer on clearance
                    if (_phoneSoundState.value == PhoneSoundState.PLAYING) {
                        _phoneSoundState.value = PhoneSoundState.STOPPED
                        onStopPhoneAlarm()
                    }
                    onSendBuzzerOff()
                }
            }

            // Case 2: New Alert Condition (or condition changed to a different alert)
            newCondition != previousCondition -> {
                // If transitioning from a previous alert, resolve the old one
                if (previousCondition != AlarmCondition.NORMAL) {
                    previousCondition.alertType?.let { onAlertResolved(it) }
                }

                _condition.value = newCondition
                _ackState.value = AlarmAckState.NOT_ACKNOWLEDGED // Reset latch for new alert

                val alertType = newCondition.alertType ?: AlertType.HIGH_HEART_RATE
                val measurement = if (newCondition == AlarmCondition.TEMP_HIGH) {
                    reading.temperatureCelsius
                } else {
                    reading.adjustedBpm
                }
                val threshold = if (newCondition == AlarmCondition.TEMP_HIGH) {
                    settings.tempThresholdCelsius
                } else if (newCondition == AlarmCondition.HR_LOW) {
                    settings.hrMinBpm
                } else {
                    settings.hrMaxBpm
                }

                val notes = when (newCondition) {
                    AlarmCondition.TEMP_HIGH -> "Elevated body temperature: ${String.format("%.1f", measurement)}°C (Limit: ${threshold}°C)"
                    AlarmCondition.HR_HIGH -> "Tachycardia detected: ${measurement.toInt()} BPM (Maximum: ${threshold.toInt()} BPM)"
                    AlarmCondition.HR_LOW -> "Bradycardia detected: ${measurement.toInt()} BPM (Minimum: ${threshold.toInt()} BPM)"
                    AlarmCondition.NORMAL -> ""
                }

                val event = AlertEvent(
                    id = System.nanoTime(),
                    alertType = alertType,
                    measurementValue = measurement,
                    thresholdValue = threshold,
                    timestamp = currentTime,
                    isAcknowledged = false,
                    notes = notes
                )

                _activeAlert.value = event
                onAlertCreated(event)

                // Activate phone alarm and send BUZZER_ON once
                _phoneSoundState.value = PhoneSoundState.PLAYING
                onStartPhoneAlarm(alertType)
                onSendBuzzerOn()
            }

            // Case 3: Condition remains the same alert
            else -> {
                // If already acknowledged, KEEP latch closed: DO NOT turn buzzer or sound back on!
                if (previousAck == AlarmAckState.ACKNOWLEDGED) {
                    // Safety check: Ensure phone sound and buzzer remain off
                    if (_phoneSoundState.value == PhoneSoundState.PLAYING) {
                        _phoneSoundState.value = PhoneSoundState.STOPPED
                        onStopPhoneAlarm()
                    }
                } else {
                    // Unacknowledged: keep sound playing, but do not spam duplicate buzzer_on commands
                    if (_phoneSoundState.value != PhoneSoundState.PLAYING) {
                        _phoneSoundState.value = PhoneSoundState.PLAYING
                        newCondition.alertType?.let { onStartPhoneAlarm(it) }
                    }
                }
            }
        }

        return newCondition
    }

    /**
     * Acknowledges an active alert.
     * Silences both the phone alarm/vibration and the Arduino hardware buzzer immediately,
     * and sets the acknowledgment latch to prevent reactivation during the same event.
     */
    @Synchronized
    fun acknowledgeAlert(alertId: Long? = null) {
        val current = _activeAlert.value
        val effectiveId = alertId ?: current?.id ?: return

        _ackState.value = AlarmAckState.ACKNOWLEDGED

        // 1. Silence phone sound and vibration immediately
        if (_phoneSoundState.value == PhoneSoundState.PLAYING) {
            _phoneSoundState.value = PhoneSoundState.STOPPED
            onStopPhoneAlarm()
        }

        // 2. Silence Arduino buzzer immediately
        onSendBuzzerOff()

        // 3. Mark alert event as acknowledged
        if (current != null && (alertId == null || current.id == alertId)) {
            val updated = current.copy(
                isAcknowledged = true,
                acknowledgedTimestamp = System.currentTimeMillis()
            )
            _activeAlert.value = updated
        }

        onAlertAcknowledged(effectiveId)
    }

    /**
     * Resets state machine on STOP, RESET, or full system reset.
     */
    @Synchronized
    fun reset() {
        if (_phoneSoundState.value == PhoneSoundState.PLAYING) {
            _phoneSoundState.value = PhoneSoundState.STOPPED
            onStopPhoneAlarm()
        }
        onSendBuzzerOff()

        _condition.value = AlarmCondition.NORMAL
        _ackState.value = AlarmAckState.NOT_ACKNOWLEDGED
        _activeAlert.value = null
    }

    /**
     * Explicit STOP command handling.
     */
    @Synchronized
    fun stop() {
        reset()
    }
}

