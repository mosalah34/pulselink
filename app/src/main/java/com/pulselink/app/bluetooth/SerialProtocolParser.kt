package com.pulselink.app.bluetooth

import com.pulselink.app.data.model.VitalReading

class SerialProtocolParser {

    private val lineBuffer = StringBuilder()

    /**
     * Checks if a line is an Arduino acknowledgment, error, or system message
     * rather than sensor telemetry.
     */
    fun isAcknowledgementOrSystemMessage(line: String): Boolean {
        val upper = line.trim().uppercase()
        return upper.startsWith("ACK:") ||
               upper.startsWith("ERR:") ||
               upper.startsWith("SYSTEM_READY") ||
               upper.startsWith("[PULSELINK") ||
               upper.startsWith("OK")
    }

    /**
     * Feeds incoming chunks of raw string data and emits complete lines detected.
     * Handles LF, CRLF, partial packets, and multiple coalesced packets safely.
     */
    @Synchronized
    fun processIncomingChunk(chunk: String): List<String> {
        val completeLines = mutableListOf<String>()
        try {
            lineBuffer.append(chunk)

            var newlineIndex = lineBuffer.indexOf("\n")
            while (newlineIndex != -1) {
                val line = lineBuffer.substring(0, newlineIndex).trimEnd('\r').trim()
                lineBuffer.delete(0, newlineIndex + 1)
                if (line.isNotEmpty()) {
                    completeLines.add(line)
                }
                newlineIndex = lineBuffer.indexOf("\n")
            }

            // Guard against buffer overflow if no newline arrives
            if (lineBuffer.length > 4096) {
                lineBuffer.clear()
            }
        } catch (e: Exception) {
            lineBuffer.clear()
        }

        return completeLines
    }

    /**
     * Parses a single line into a VitalReading.
     * Supports formats:
     * Format 1: "HR=75.0;TEMP=36.8"
     * Format 2: "HR=75.0"
     * Format 3: "TEMP=36.8"
     * Format 4: "HR:75.0,TEMP:36.8"
     * 
     * IMPORTANT:
     * HR received from Arduino firmware is ALREADY calibrated.
     * Do NOT apply 40 BPM calibration again.
     */
    fun parseLine(line: String): VitalReading? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // Ignore firmware acknowledgements and status messages
        if (isAcknowledgementOrSystemMessage(trimmed)) {
            return null
        }

        var rawBpm: Float? = null
        var temp: Float? = null

        try {
            // Semicolon-delimited key=value
            if (trimmed.contains(";") || trimmed.contains("=")) {
                val pairs = trimmed.split(";")
                for (pair in pairs) {
                    val kv = pair.split("=")
                    if (kv.size == 2) {
                        val key = kv[0].trim().uppercase()
                        val value = kv[1].trim()
                        when (key) {
                            "HR", "BPM", "HEARTRATE" -> {
                                value.toFloatOrNull()?.let { v ->
                                    if (v in 20.0f..250.0f) rawBpm = v
                                }
                            }
                            "TEMP", "TEMPERATURE" -> {
                                value.toFloatOrNull()?.let { v ->
                                    if (v in 10.0f..60.0f) temp = v
                                }
                            }
                        }
                    }
                }
            }

            // Comma/colon fallback: "HR:78.5,TEMP:36.8"
            if (rawBpm == null && temp == null) {
                val parts = trimmed.split(Regex("[,\\s]+"))
                for (part in parts) {
                    val kv = part.split(Regex("[:=]"))
                    if (kv.size == 2) {
                        val key = kv[0].trim().uppercase()
                        val value = kv[1].trim()
                        when (key) {
                            "HR", "BPM", "HEARTRATE" -> {
                                value.toFloatOrNull()?.let { v ->
                                    if (v in 20.0f..250.0f) rawBpm = v
                                }
                            }
                            "TEMP", "TEMPERATURE" -> {
                                value.toFloatOrNull()?.let { v ->
                                    if (v in 10.0f..60.0f) temp = v
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }

        // Neither measurement present -> invalid packet
        if (rawBpm == null && temp == null) {
            return null
        }

        val finalBpm = rawBpm ?: Float.NaN
        val finalTemp = temp ?: Float.NaN

        return VitalReading(
            timestamp = System.currentTimeMillis(),
            rawBpm = finalBpm,
            adjustedBpm = finalBpm, // ALREADY calibrated by Arduino; use directly
            temperatureCelsius = finalTemp,
            isStale = false,
            isValid = true
        )
    }

    @Synchronized
    fun clearBuffer() {
        lineBuffer.clear()
    }
}
