package com.pulselink.app.data.model

enum class AlertType(val title: String, val priority: Int) {
    HIGH_TEMPERATURE("High Temperature Alert", 10), // Highest priority per specification
    HIGH_HEART_RATE("Tachycardia (High HR)", 5),
    LOW_HEART_RATE("Bradycardia (Low HR)", 5),
    SENSOR_DISCONNECTED("Sensor / Link Loss", 2)
}

data class AlertEvent(
    val id: Long = 0,
    val alertType: AlertType,
    val measurementValue: Float,
    val thresholdValue: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val resolvedTimestamp: Long? = null,
    val isAcknowledged: Boolean = false,
    val acknowledgedTimestamp: Long? = null,
    val notes: String = ""
) {
    val isActive: Boolean
        get() = resolvedTimestamp == null
}

