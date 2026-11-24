package com.pulselink.app.engine

import com.pulselink.app.data.model.AlertType
import com.pulselink.app.data.model.PatientStatus
import com.pulselink.app.data.model.ThresholdSettings
import com.pulselink.app.data.model.VitalReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhoneDecisionEngineTest {

    private lateinit var engine: PhoneDecisionEngine

    @Before
    fun setUp() {
        engine = PhoneDecisionEngine(
            ThresholdSettings(
                hrMinBpm = 50.0f,
                hrMaxBpm = 100.0f,
                tempThresholdCelsius = 35.0f,
                staleTimeoutMillis = 5000L
            )
        )
    }

    @Test
    fun testNormalVitalsEvaluation() {
        val reading = VitalReading(
            rawBpm = 72f,
            adjustedBpm = 72f,
            temperatureCelsius = 31.5f
        )
        val result = engine.evaluateReading(reading)
        assertEquals(PatientStatus.NORMAL, result.status)
        assertTrue(result.activeAlerts.isEmpty())
        assertNull(result.primaryAlert)
    }

    @Test
    fun testLowHeartRateAlert() {
        val reading = VitalReading(
            rawBpm = 45f,
            adjustedBpm = 45f, // Below 50 BPM
            temperatureCelsius = 30.0f
        )
        val result = engine.evaluateReading(reading)
        assertEquals(PatientStatus.WARNING, result.status)
        assertEquals(1, result.activeAlerts.size)
        assertEquals(AlertType.LOW_HEART_RATE, result.primaryAlert?.alertType)
    }

    @Test
    fun testHighHeartRateAlertWithCalibration() {
        // Raw HR 150 -> adjusted BPM = 150 - 40 = 110 (> 100)
        val reading = VitalReading(
            rawBpm = 150f,
            adjustedBpm = 110f,
            temperatureCelsius = 30.0f
        )
        val result = engine.evaluateReading(reading)
        assertEquals(PatientStatus.WARNING, result.status)
        assertEquals(1, result.activeAlerts.size)
        assertEquals(AlertType.HIGH_HEART_RATE, result.primaryAlert?.alertType)
    }

    @Test
    fun testHighTemperatureAlertPriorityOverHeartRate() {
        // Both high HR and high Temp active at the same time
        val reading = VitalReading(
            rawBpm = 160f,
            adjustedBpm = 120f, // > 100
            temperatureCelsius = 36.8f // >= 35.0
        )
        val result = engine.evaluateReading(reading)

        // Status must be CRITICAL
        assertEquals(PatientStatus.CRITICAL, result.status)
        assertEquals(2, result.activeAlerts.size)

        // Temperature must have higher priority than Heart Rate
        assertNotNull(result.primaryAlert)
        assertEquals(AlertType.HIGH_TEMPERATURE, result.primaryAlert?.alertType)
    }

    @Test
    fun testAlertRecoveryWhenVitalsNormalize() {
        // Trigger high temp
        val alertReading = VitalReading(
            rawBpm = 70f,
            adjustedBpm = 70f,
            temperatureCelsius = 36.0f
        )
        engine.evaluateReading(alertReading)
        assertEquals(1, engine.decisionFlow.value.activeAlerts.size)

        // Recovered reading
        val recoveredReading = VitalReading(
            rawBpm = 70f,
            adjustedBpm = 70f,
            temperatureCelsius = 31.0f
        )
        val result = engine.evaluateReading(recoveredReading)
        assertEquals(PatientStatus.NORMAL, result.status)
        assertTrue(result.activeAlerts.isEmpty())
        assertNull(result.primaryAlert)
    }

    @Test
    fun testStalenessDetection() {
        val now = 1000000L
        val reading = VitalReading(
            timestamp = now,
            rawBpm = 70f,
            adjustedBpm = 70f,
            temperatureCelsius = 31.0f
        )
        engine.evaluateReading(reading, currentTime = now)

        // 2 seconds later -> not stale (< 5000ms)
        val result1 = engine.checkStaleness(currentTime = now + 2000L)
        assertTrue(!result1.isStale)

        // 6 seconds later -> stale (> 5000ms)
        val result2 = engine.checkStaleness(currentTime = now + 6000L)
        assertTrue(result2.isStale)
    }
}

