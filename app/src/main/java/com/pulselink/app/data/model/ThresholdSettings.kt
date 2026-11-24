package com.pulselink.app.data.model

data class ThresholdSettings(
    val hrMinBpm: Float = 50.0f,
    val hrMaxBpm: Float = 100.0f,
    val tempThresholdCelsius: Float = 35.0f,
    val alarmSoundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val alarmTone: AlarmToneType = AlarmToneType.MEDICAL_BUZZER,
    val autoReconnect: Boolean = true,
    val staleTimeoutMillis: Long = 5000L
)

enum class AlarmToneType(val displayName: String) {
    MEDICAL_BUZZER("Medical Emergency Buzzer"),
    PULSE_CHIME("Hospital Vital Alert"),
    RAPID_SIREN("ICU Critical Siren")
}

