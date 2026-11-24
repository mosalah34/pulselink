package com.pulselink.app.bluetooth

import com.pulselink.app.data.model.PatientStatus
import com.pulselink.app.data.model.ThresholdSettings
import com.pulselink.app.engine.PhoneDecisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighVolumeStressTest {

    @Test
    fun testHighVolumeSimulationThroughput() {
        val parser = SerialProtocolParser()
        val engine = PhoneDecisionEngine(
            ThresholdSettings(
                hrMinBpm = 50.0f,
                hrMaxBpm = 100.0f,
                tempThresholdCelsius = 35.0f
            )
        )

        val totalPackets = 5000
        var parsedCount = 0
        var alertCount = 0

        val startTime = System.currentTimeMillis()

        for (i in 1..totalPackets) {
            // Alternate between normal vitals, high heart rate, and high temperature
            val hr = when {
                i % 20 == 0 -> 125.0f // High HR
                i % 50 == 0 -> 42.0f  // Low HR
                else -> 72.0f + (i % 15)
            }
            val temp = if (i % 30 == 0) 36.5f else 31.0f

            val chunk = "HR=${hr};TEMP=${temp}\n"
            val lines = parser.processIncomingChunk(chunk)
            for (line in lines) {
                val reading = parser.parseLine(line)
                if (reading != null) {
                    parsedCount++
                    val decision = engine.evaluateReading(reading)
                    if (decision.status != PatientStatus.NORMAL) {
                        alertCount++
                    }
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(totalPackets, parsedCount)
        assertTrue("Parsing and decision pipeline took under 2000ms (actual: ${elapsed}ms)", elapsed < 2000)
    }

    @Test
    fun testMalformedAndFragmentedDataUnderHighVolume() {
        val parser = SerialProtocolParser()
        val chunks = listOf(
            "HR=75.", "0;TEMP=32.0\n",
            "ACK:START_OK\n",
            "INVALID_CORRUPT_BYTES\n",
            "HR=80.0\n",
            "TEMP=33.5\n",
            "HR=999.0;TEMP=34.0\n",
            "OK\n"
        )

        var validReadings = 0
        for (repeat in 1..500) {
            for (chunk in chunks) {
                val lines = parser.processIncomingChunk(chunk)
                for (line in lines) {
                    val reading = parser.parseLine(line)
                    if (reading != null) {
                        validReadings++
                    }
                }
            }
        }

        // Each loop contains 4 valid lines: "HR=75.0;TEMP=32.0", "HR=80.0", "TEMP=33.5", "HR=999.0;TEMP=34.0" (temp valid)
        assertEquals(500 * 4, validReadings)
    }
}

