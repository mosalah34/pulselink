package com.pulselink.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SerialProtocolParserTest {

    private lateinit var parser: SerialProtocolParser

    @Before
    fun setUp() {
        parser = SerialProtocolParser()
    }

    @Test
    fun testChunkProcessingWithMultipleAndPartialLines() {
        // Feed partial line 1
        val lines1 = parser.processIncomingChunk("HR=80.0;")
        assertTrue(lines1.isEmpty())

        // Finish line 1 and start line 2
        val lines2 = parser.processIncomingChunk("TEMP=25.0\r\nHR=10")
        assertEquals(1, lines2.size)
        assertEquals("HR=80.0;TEMP=25.0", lines2[0])

        // Finish line 2
        val lines3 = parser.processIncomingChunk("5.0;TEMP=36.2\n")
        assertEquals(1, lines3.size)
        assertEquals("HR=105.0;TEMP=36.2", lines3[0])
    }

    @Test
    fun testParseFormat1SemicolonDelimited() {
        val line = "HR=80.8;TEMP=25.00;HR_ALARM=NO;TEMP_ALARM=NO"
        val reading = parser.parseLine(line)
        assertNotNull(reading)
        assertEquals(80.8f, reading!!.rawBpm, 0.01f)
        // BPM received from Arduino is treated as final calibrated BPM
        assertEquals(80.8f, reading.adjustedBpm, 0.01f)
        assertEquals(25.00f, reading.temperatureCelsius, 0.01f)
        assertTrue(reading.isValid)
    }

    @Test
    fun testHeartRateDirectUseWithoutDoubleCalibration() {
        // Arduino sends already-calibrated BPM: e.g. HR=70.0 (calibrated from 110)
        val line = "HR=70.0;TEMP=36.50"
        val reading = parser.parseLine(line)
        assertNotNull(reading)
        assertEquals(70.0f, reading!!.rawBpm, 0.01f)
        assertEquals(70.0f, reading.adjustedBpm, 0.01f)
    }

    @Test
    fun testSingleFieldTelemetryPackets() {
        // Single field: HR only
        val hrReading = parser.parseLine("HR=72.5")
        assertNotNull(hrReading)
        assertEquals(72.5f, hrReading!!.adjustedBpm, 0.01f)
        assertTrue(hrReading.temperatureCelsius.isNaN())

        // Single field: TEMP only
        val tempReading = parser.parseLine("TEMP=36.75")
        assertNotNull(tempReading)
        assertTrue(tempReading!!.adjustedBpm.isNaN())
        assertEquals(36.75f, tempReading.temperatureCelsius, 0.01f)
    }

    @Test
    fun testParseFormat2CommaColonDelimited() {
        val line = "HR:78.5,TEMP:36.8"
        val reading = parser.parseLine(line)
        assertNotNull(reading)
        assertEquals(78.5f, reading!!.rawBpm, 0.01f)
        assertEquals(78.5f, reading.adjustedBpm, 0.01f)
        assertEquals(36.8f, reading.temperatureCelsius, 0.01f)
    }

    @Test
    fun testAcknowledgementAndSystemMessagesIgnoredAsVitals() {
        val ackLine = "ACK:START_OK"
        assertTrue(parser.isAcknowledgementOrSystemMessage(ackLine))
        assertNull(parser.parseLine(ackLine))

        val readyLine = "SYSTEM_READY"
        assertTrue(parser.isAcknowledgementOrSystemMessage(readyLine))
        assertNull(parser.parseLine(readyLine))

        val promptLine = "[PULSELINK] System Initialized"
        assertTrue(parser.isAcknowledgementOrSystemMessage(promptLine))
        assertNull(parser.parseLine(promptLine))
    }

    @Test
    fun testMalformedAndOutOfRangeDataIgnoredSafely() {
        // Completely invalid text
        val invalidText = "UNKNOWN_MESSAGE_FROM_ARDUINO"
        assertNull(parser.parseLine(invalidText))

        // Heart rate out of physiological range (e.g. 999 BPM)
        val outOfRangeLine = "HR=999.0;TEMP=25.0"
        val reading = parser.parseLine(outOfRangeLine)
        assertNotNull(reading)
        // Invalid HR is NaN, but valid temp is preserved
        assertTrue(reading!!.rawBpm.isNaN())
        assertEquals(25.0f, reading.temperatureCelsius, 0.01f)
    }
}
