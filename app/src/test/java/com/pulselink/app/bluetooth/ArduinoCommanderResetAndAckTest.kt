package com.pulselink.app.bluetooth

import com.pulselink.app.data.model.ArduinoResetState
import com.pulselink.app.data.model.BuzzerHardwareState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArduinoCommanderResetAndAckTest {

    private val sentCommands = mutableListOf<String>()
    private var sendSuccess = true
    private val testScope = TestScope()
    private lateinit var commander: ArduinoCommander

    @Before
    fun setUp() {
        sentCommands.clear()
        sendSuccess = true
        commander = ArduinoCommander(
            sendRawCommand = { cmd ->
                sentCommands.add(cmd)
                sendSuccess
            },
            scope = testScope
        )
    }

    /**
     * Test 6: RESET sends exactly one RESET command and debounces repeated presses.
     */
    @Test
    fun testResetSendsExactlyOneResetCommandAndDebounces() = testScope.runTest {
        val success1 = commander.sendReset()
        assertTrue(success1)
        assertEquals(1, sentCommands.size)
        assertEquals("RESET\n", sentCommands[0])

        // Attempt rapid second press while in progress
        val success2 = commander.sendReset()
        assertFalse("Second RESET press must be ignored while restart in progress", success2)
        assertEquals(1, sentCommands.size)
    }

    /**
     * Test 7: ACK:RESETTING transitions to RestartingArduino and does NOT falsely mark restart complete.
     */
    @Test
    fun testAckResettingDoesNotFalselyMarkRestartComplete() = testScope.runTest {
        commander.sendReset()
        assertEquals(ArduinoResetState.WaitingResetAck, commander.resetState.value)

        // Arduino returns ACK:RESETTING
        commander.onIncomingLine("ACK:RESETTING")

        // Must show RestartingArduino, NOT RestartedSuccess!
        assertEquals(ArduinoResetState.RestartingArduino, commander.resetState.value)
        assertTrue(commander.resetState.value.isRestartInProgress)
    }

    /**
     * Test 8: SYSTEM_READY confirms the reboot.
     */
    @Test
    fun testSystemReadyConfirmsReboot() = testScope.runTest {
        commander.sendReset()
        commander.onIncomingLine("ACK:RESETTING")
        assertEquals(ArduinoResetState.RestartingArduino, commander.resetState.value)

        // Arduino reboots and transmits SYSTEM_READY
        commander.onIncomingLine("SYSTEM_READY")

        // Confirms reboot completion
        assertEquals(ArduinoResetState.RestartedSuccess, commander.resetState.value)
        assertFalse(commander.resetState.value.isRestartInProgress)
    }

    /**
     * Test 9: Bluetooth disconnect during restart does not crash the app, and reconnect restores state.
     */
    @Test
    fun testBluetoothDisconnectDuringRestartHandledGracefully() = testScope.runTest {
        commander.sendReset()
        commander.onIncomingLine("ACK:RESETTING")
        assertEquals(ArduinoResetState.RestartingArduino, commander.resetState.value)

        // Simulate connection drop during reboot
        sendSuccess = false
        // Triggering connection restored when HC-05 comes back
        commander.onConnectionRestored()

        // Arduino completes setup and sends SYSTEM_READY
        commander.onIncomingLine("SYSTEM_READY")
        assertEquals(ArduinoResetState.RestartedSuccess, commander.resetState.value)
    }

    /**
     * Test 10: Missing ACK:BUZZER_OFF produces a visible warning and queues retry.
     */
    @Test
    fun testMissingAckBuzzerOffProducesVisibleWarningAndRetry() = testScope.runTest {
        commander.sendBuzzerOff()
        assertEquals(BuzzerHardwareState.PENDING_OFF, commander.buzzerState.value)

        // Advance virtual time past the 3.5 second timeout without receiving ACK:BUZZER_OFF
        testScope.advanceTimeBy(4000L)

        // State must transition to UNCONFIRMED_OFF and warning must be set
        assertEquals(BuzzerHardwareState.UNCONFIRMED_OFF, commander.buzzerState.value)
        assertNotNull("Visible warning must be generated when ACK:BUZZER_OFF is missing", commander.buzzerWarning.value)

        // When connection is restored, commander retries sending BUZZER_OFF
        commander.onConnectionRestored()
        assertTrue(sentCommands.contains("BUZZER_OFF\n"))

        // When ACK finally arrives
        commander.onIncomingLine("ACK:BUZZER_OFF")
        assertEquals(BuzzerHardwareState.OFF, commander.buzzerState.value)
        assertEquals(null, commander.buzzerWarning.value)
    }
}

