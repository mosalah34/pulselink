package com.pulselink.app.engine

import com.pulselink.app.data.model.AlarmAckState
import com.pulselink.app.data.model.AlarmCondition
import com.pulselink.app.data.model.AlertType
import com.pulselink.app.data.model.PhoneSoundState
import com.pulselink.app.data.model.ThresholdSettings
import com.pulselink.app.data.model.VitalReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlarmStateMachineTest {

    private lateinit var stateMachine: AlarmStateMachine
    private var phoneAlarmStartedCount = 0
    private var phoneAlarmStoppedCount = 0
    private var buzzerOnCount = 0
    private var buzzerOffCount = 0
    private var lastStartedAlertType: AlertType? = null
    private var lastResolvedAlertType: AlertType? = null

    @Before
    fun setUp() {
        phoneAlarmStartedCount = 0
        phoneAlarmStoppedCount = 0
        buzzerOnCount = 0
        buzzerOffCount = 0
        lastStartedAlertType = null
        lastResolvedAlertType = null

        stateMachine = AlarmStateMachine(
            settings = ThresholdSettings(
                hrMinBpm = 50.0f,
                hrMaxBpm = 100.0f,
                tempThresholdCelsius = 35.0f
            ),
            onStartPhoneAlarm = { type ->
                phoneAlarmStartedCount++
                lastStartedAlertType = type
            },
            onStopPhoneAlarm = {
                phoneAlarmStoppedCount++
            },
            onSendBuzzerOn = {
                buzzerOnCount++
            },
            onSendBuzzerOff = {
                buzzerOffCount++
            },
            onAlertResolved = { type ->
                lastResolvedAlertType = type
            }
        )
    }

    /**
     * Test 1: Acknowledging HR_HIGH sends BUZZER_OFF.
     */
    @Test
    fun testAcknowledgingHrHighSendsBuzzerOff() {
        // Feed High HR reading (115 BPM)
        val reading = VitalReading(rawBpm = 115f, adjustedBpm = 115f, temperatureCelsius = 31.0f)
        stateMachine.processReading(reading)

        assertEquals(AlarmCondition.HR_HIGH, stateMachine.condition.value)
        assertEquals(1, buzzerOnCount)
        assertEquals(1, phoneAlarmStartedCount)

        // Acknowledge alert
        stateMachine.acknowledgeAlert()

        assertEquals(AlarmAckState.ACKNOWLEDGED, stateMachine.ackState.value)
        assertTrue("BUZZER_OFF must be transmitted upon acknowledging HR_HIGH", buzzerOffCount >= 1)
    }

    /**
     * Test 2: Acknowledging TEMP_HIGH sends BUZZER_OFF.
     */
    @Test
    fun testAcknowledgingTempHighSendsBuzzerOff() {
        // Feed High Temperature reading (36.8 C >= 35.0 C)
        val reading = VitalReading(rawBpm = 75f, adjustedBpm = 75f, temperatureCelsius = 36.8f)
        stateMachine.processReading(reading)

        assertEquals(AlarmCondition.TEMP_HIGH, stateMachine.condition.value)
        assertEquals(1, buzzerOnCount)
        assertEquals(1, phoneAlarmStartedCount)
        assertEquals(AlertType.HIGH_TEMPERATURE, lastStartedAlertType)

        // Acknowledge alert
        stateMachine.acknowledgeAlert()

        assertEquals(AlarmAckState.ACKNOWLEDGED, stateMachine.ackState.value)
        assertTrue("BUZZER_OFF must be transmitted upon acknowledging TEMP_HIGH", buzzerOffCount >= 1)
    }

    /**
     * Test 3: Acknowledging an alert stops phone sound and vibration.
     */
    @Test
    fun testAcknowledgingAlertStopsPhoneSoundAndVibration() {
        val reading = VitalReading(rawBpm = 120f, adjustedBpm = 120f, temperatureCelsius = 31.0f)
        stateMachine.processReading(reading)

        assertEquals(PhoneSoundState.PLAYING, stateMachine.phoneSoundState.value)

        // Acknowledge alert
        stateMachine.acknowledgeAlert()

        assertEquals(PhoneSoundState.STOPPED, stateMachine.phoneSoundState.value)
        assertTrue(phoneAlarmStoppedCount >= 1)
    }

    /**
     * Test 4: Repeated telemetry during an acknowledged alert does NOT reactivate the buzzer.
     */
    @Test
    fun testRepeatedTelemetryDuringAcknowledgedAlertDoesNotReactivateBuzzer() {
        // Trigger HR_HIGH
        val reading1 = VitalReading(rawBpm = 110f, adjustedBpm = 110f, temperatureCelsius = 30.0f)
        stateMachine.processReading(reading1)
        assertEquals(1, buzzerOnCount)

        // User acknowledges
        stateMachine.acknowledgeAlert()
        val initialBuzzerOffCount = buzzerOffCount
        assertTrue(initialBuzzerOffCount >= 1)

        // Simulate 20 continuous packets with High HR while still acknowledged
        for (i in 1..20) {
            val readingNext = VitalReading(rawBpm = 112f + (i % 5), adjustedBpm = 112f + (i % 5), temperatureCelsius = 30.0f)
            stateMachine.processReading(readingNext)
        }

        // BUZZER_ON must NEVER be called again for the same acknowledged alert!
        assertEquals("BUZZER_ON count must remain 1 during continuous acknowledged alert", 1, buzzerOnCount)
        assertEquals(PhoneSoundState.STOPPED, stateMachine.phoneSoundState.value)
        assertEquals(AlarmAckState.ACKNOWLEDGED, stateMachine.ackState.value)
    }

    /**
     * Test 5: A new alert after returning to normal activates the alarm again.
     */
    @Test
    fun testNewAlertAfterReturningToNormalActivatesAlarmAgain() {
        // 1. Initial alert
        val alert1 = VitalReading(rawBpm = 120f, adjustedBpm = 120f, temperatureCelsius = 30.0f)
        stateMachine.processReading(alert1)
        assertEquals(1, buzzerOnCount)
        stateMachine.acknowledgeAlert()

        // 2. Vitals return to normal (75 BPM, 31 C)
        val normalReading = VitalReading(rawBpm = 75f, adjustedBpm = 75f, temperatureCelsius = 31.0f)
        stateMachine.processReading(normalReading)

        assertEquals(AlarmCondition.NORMAL, stateMachine.condition.value)
        assertEquals(AlarmAckState.NOT_ACKNOWLEDGED, stateMachine.ackState.value) // Latch reset!

        // 3. New alert arrives later (High Temp: 36.5 C)
        val alert2 = VitalReading(rawBpm = 75f, adjustedBpm = 75f, temperatureCelsius = 36.5f)
        stateMachine.processReading(alert2)

        assertEquals(AlarmCondition.TEMP_HIGH, stateMachine.condition.value)
        assertEquals("Buzzer ON must be triggered again for new alert", 2, buzzerOnCount)
        assertEquals(PhoneSoundState.PLAYING, stateMachine.phoneSoundState.value)
        assertEquals(AlarmAckState.NOT_ACKNOWLEDGED, stateMachine.ackState.value)
    }

    /**
     * Test 11: STOP also silences the Arduino buzzer.
     */
    @Test
    fun testStopSilencesArduinoBuzzer() {
        val alertReading = VitalReading(rawBpm = 120f, adjustedBpm = 120f, temperatureCelsius = 30.0f)
        stateMachine.processReading(alertReading)
        assertEquals(1, buzzerOnCount)

        // Call stop()
        stateMachine.stop()

        assertEquals(AlarmCondition.NORMAL, stateMachine.condition.value)
        assertEquals(PhoneSoundState.STOPPED, stateMachine.phoneSoundState.value)
        assertTrue(buzzerOffCount >= 1)
    }
}

