package com.pulselink.app.data.model

data class VitalReading(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rawBpm: Float,
    val adjustedBpm: Float,
    val temperatureCelsius: Float,
    val isStale: Boolean = false,
    val isValid: Boolean = true
) {
    companion object {
        fun calibrateBpm(rawBpm: Float): Float {
            return if (rawBpm > 95f) rawBpm - 40f else rawBpm
        }
    }
}

