package com.pulselink.app.data.model

enum class PatientStatus(val label: String) {
    NORMAL("Normal"),
    WARNING("Warning"),
    CRITICAL("Critical"),
    IDLE("Standby")
}

