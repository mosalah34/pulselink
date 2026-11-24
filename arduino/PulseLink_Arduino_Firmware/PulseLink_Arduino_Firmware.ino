/*
 * ============================================================
 * PulseLink - Arduino UNO Firmware
 * Arduino UNO + HC-05 + Pulse Sensor + DS18B20 + Active Buzzer
 *
 * HC-05:
 * TXD -> D10
 * RXD <- D11 through voltage divider
 * Baud: 9600
 *
 * Pulse Sensor:
 * Signal -> A0
 *
 * DS18B20:
 * Data -> D2
 * 4.7K pull-up between D2 and 5V
 *
 * Active Buzzer:
 * + -> D8
 * - -> GND
 *
 * Android is the ONLY alarm decision maker.
 * Calibration is applied ONCE in Arduino:
 * adjustedBPM = rawBPM > 95 ? rawBPM - 40 : rawBPM;
 *
 * RESET uses ATmega328P hardware watchdog.
 * ============================================================
 */

#include <SoftwareSerial.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <math.h>
#include <avr/wdt.h>

// ---------------- PINS ----------------
const byte PIN_PULSE = A0;
const byte PIN_ONE_WIRE = 2;
const byte PIN_BUZZER = 8;
const byte PIN_BT_RX = 10;
const byte PIN_BT_TX = 11;

// ---------------- HARDWARE ----------------
SoftwareSerial btSerial(PIN_BT_RX, PIN_BT_TX);
OneWire oneWire(PIN_ONE_WIRE);
DallasTemperature tempSensor(&oneWire);

// ---------------- SYSTEM STATE ----------------
bool isMonitoring = false;
bool phoneBuzzerState = false;
bool isTestActive = false;

unsigned long testStartTime = 0;
const unsigned long TEST_DURATION = 1000;

unsigned long lastTelemetryTime = 0;
const unsigned long TELEMETRY_INTERVAL = 1000;

// ---------------- PULSE ALGORITHM ----------------
const unsigned long sampleInterval = 10;
unsigned long lastSampleTime = 0;

float filteredSignal = 0.0;
float baseline = 0.0;
float signalAmplitude = 0.0;

const float baselineAlpha = 0.01;
const float filterAlpha = 0.25;
const float amplitudeAlpha = 0.02;

bool aboveThreshold = false;

unsigned long lastBeatTime = 0;
unsigned long lastValidPulseTime = 0;

const unsigned long NO_SIGNAL_TIMEOUT = 5000;
const unsigned long minBeatInterval = 350;
const unsigned long maxBeatInterval = 1500;

const byte NUM_BEATS = 5;
unsigned long beatIntervals[NUM_BEATS] = {0};

byte beatIndex = 0;
byte beatCount = 0;

float rawBPM = 0.0;
float adjustedBPM = 0.0;
float previousBPM = 0.0;

byte stableCount = 0;
bool hasValidBpm = false;

// ---------------- TEMPERATURE ----------------
DeviceAddress tempDeviceAddress;

bool isTempSensorConnected = false;
bool isTempConversionPending = false;

unsigned long tempRequestTime = 0;

const unsigned long TEMP_CONV_DELAY_9BIT = 100;

float tempHistory[5] = {0};
byte tempIndex = 0;
byte tempCount = 0;

float filteredTemp = 0.0;
bool hasValidTemp = false;

// ---------------- COMMAND BUFFER ----------------
const byte CMD_BUFFER_SIZE = 64;

char btCmdBuffer[CMD_BUFFER_SIZE];
byte btCmdLen = 0;
bool overflowBT = false;

char usbCmdBuffer[CMD_BUFFER_SIZE];
byte usbCmdLen = 0;
bool overflowUSB = false;

// ---------------- FUNCTION DECLARATIONS ----------------
void processSerialStream(Stream &stream, char *buffer,
                         byte &bufLen, bool &overflow);

void handleCommand(const char *cmd);
void samplePulseSensor();
void processTemperatureSensor();
void transmitTelemetry();
void resetSensors();

// ============================================================
// SETUP
// ============================================================

void setup() {

  // Disable watchdog immediately after boot.
  wdt_disable();

  Serial.begin(9600);
  btSerial.begin(9600);

  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);

  // Initialize temperature sensor.
  tempSensor.begin();

  isTempSensorConnected =
      tempSensor.getAddress(tempDeviceAddress, 0);

  if (isTempSensorConnected) {
    tempSensor.setResolution(tempDeviceAddress, 9);
  }

  tempSensor.setWaitForConversion(false);

  if (isTempSensorConnected) {
    tempSensor.requestTemperatures();
    isTempConversionPending = true;
    tempRequestTime = millis();
  }

  resetSensors();

  Serial.println(F("[PulseLink] Arduino Initialized"));

  btSerial.println(F("SYSTEM_READY"));

  // Automatically restart if the main loop gets stuck.
  wdt_enable(WDTO_2S);
}

// ============================================================
// MAIN LOOP
// ============================================================

void loop() {

  wdt_reset();

  unsigned long now = millis();

  // Read Bluetooth commands.
  processSerialStream(
      btSerial,
      btCmdBuffer,
      btCmdLen,
      overflowBT
  );

  // Read USB Serial commands for debugging.
  processSerialStream(
      Serial,
      usbCmdBuffer,
      usbCmdLen,
      overflowUSB
  );

  // Automatic buzzer test shutdown.
  if (isTestActive &&
      now - testStartTime >= TEST_DURATION) {

    isTestActive = false;

    if (!phoneBuzzerState) {
      digitalWrite(PIN_BUZZER, LOW);
    }

    btSerial.println(F("ACK:TEST_COMPLETE"));
    Serial.println(F("ACK:TEST_COMPLETE"));
  }

  // Pulse sampling.
  if (isMonitoring) {
    samplePulseSensor();
  }

  // Temperature processing.
  if (isMonitoring) {
    processTemperatureSensor();
  }

  // Send telemetry every second.
  if (isMonitoring &&
      now - lastTelemetryTime >= TELEMETRY_INTERVAL) {

    lastTelemetryTime = now;
    transmitTelemetry();
  }
}

// ============================================================
// PULSE SENSOR - ORIGINAL WORKING ALGORITHM
// ============================================================

void samplePulseSensor() {

  unsigned long now = millis();

  if (now - lastSampleTime < sampleInterval) {
    return;
  }

  lastSampleTime = now;

  int rawValue = analogRead(PIN_PULSE);

  baseline += baselineAlpha * (rawValue - baseline);

  float signal = rawValue - baseline;

  filteredSignal +=
      filterAlpha * (signal - filteredSignal);

  signalAmplitude +=
      amplitudeAlpha *
      (fabsf(filteredSignal) - signalAmplitude);

  float threshold = signalAmplitude * 0.65f;

  if (threshold < 2.0f) {
    threshold = 2.0f;
  }

  // Detect rising edge.
  if (filteredSignal > threshold && !aboveThreshold) {

    if (lastBeatTime != 0) {

      unsigned long interval = now - lastBeatTime;

      if (interval >= minBeatInterval &&
          interval <= maxBeatInterval) {

        beatIntervals[beatIndex] = interval;

        beatIndex = (beatIndex + 1) % NUM_BEATS;

        if (beatCount < NUM_BEATS) {
          beatCount++;
        }

        if (beatCount >= 3) {

          unsigned long total = 0;

          for (byte i = 0; i < beatCount; i++) {
            total += beatIntervals[i];
          }

          float averageInterval =
              (float)total / beatCount;

          float newBPM =
              60000.0f / averageInterval;

          if (previousBPM == 0.0f ||
              fabsf(newBPM - previousBPM) <= 20.0f) {

            rawBPM = newBPM;
            previousBPM = rawBPM;

            stableCount++;

            lastValidPulseTime = now;

            // Apply calibration ONCE.
            adjustedBPM =
                (rawBPM > 95.0f)
                ? rawBPM - 40.0f
                : rawBPM;

            hasValidBpm = (stableCount >= 2);

          } else {

            stableCount = 0;
            hasValidBpm = false;
          }
        }
      }
    }

    lastBeatTime = now;
    aboveThreshold = true;
  }

  if (filteredSignal < threshold * 0.3f) {
    aboveThreshold = false;
  }

  // No valid pulse timeout.
  if (lastValidPulseTime != 0 &&
      now - lastValidPulseTime > NO_SIGNAL_TIMEOUT) {

    rawBPM = 0.0f;
    adjustedBPM = 0.0f;
    previousBPM = 0.0f;

    beatCount = 0;
    stableCount = 0;
    beatIndex = 0;

    lastBeatTime = 0;
    lastValidPulseTime = 0;

    hasValidBpm = false;

    for (byte i = 0; i < NUM_BEATS; i++) {
      beatIntervals[i] = 0;
    }
  }
}

// ============================================================
// DS18B20 TEMPERATURE SENSOR
// ============================================================

void processTemperatureSensor() {

  if (!isTempSensorConnected) {
    hasValidTemp = false;
    return;
  }

  unsigned long now = millis();

  if (isTempConversionPending &&
      now - tempRequestTime >= TEMP_CONV_DELAY_9BIT) {

    float rawTemp = tempSensor.getTempCByIndex(0);

    isTempConversionPending = false;

    if (rawTemp == DEVICE_DISCONNECTED_C ||
        rawTemp < -50.0f ||
        rawTemp > 85.0f) {

      hasValidTemp = false;
      tempCount = 0;

    } else {

      bool acceptSample = true;

      if (tempCount > 0 && hasValidTemp) {

        float jump = fabsf(rawTemp - filteredTemp);

        if (jump > 2.0f) {
          acceptSample = false;
        }
      }

      if (acceptSample) {

        tempHistory[tempIndex] = rawTemp;

        tempIndex = (tempIndex + 1) % 5;

        if (tempCount < 5) {
          tempCount++;
        }

        float sum = 0.0f;

        for (byte i = 0; i < tempCount; i++) {
          sum += tempHistory[i];
        }

        filteredTemp = sum / (float)tempCount;

        hasValidTemp = true;
      }
    }

    // Request next conversion without blocking.
    tempSensor.requestTemperatures();

    isTempConversionPending = true;
    tempRequestTime = millis();
  }
}

// ============================================================
// TELEMETRY
// ============================================================

void transmitTelemetry() {

  if (!isMonitoring) {
    return;
  }

  if (!hasValidBpm && !hasValidTemp) {
    return;
  }

  String packet = "";

  if (hasValidBpm) {

    packet += F("HR=");
    packet += String(adjustedBPM, 1);
  }

  if (hasValidTemp) {

    if (packet.length() > 0) {
      packet += F(";");
    }

    packet += F("TEMP=");
    packet += String(filteredTemp, 1);
  }

  if (packet.length() > 0) {

    btSerial.println(packet);
    Serial.println(packet);
  }
}

// ============================================================
// SERIAL COMMAND READER
// ============================================================

void processSerialStream(
    Stream &stream,
    char *buffer,
    byte &bufLen,
    bool &overflow
) {

  while (stream.available() > 0) {

    char c = (char)stream.read();

    if (c == '\n' || c == '\r') {

      if (!overflow && bufLen > 0) {

        buffer[bufLen] = '\0';

        handleCommand(buffer);
      }

      bufLen = 0;
      overflow = false;
    }

    else if (!overflow) {

      if (bufLen < CMD_BUFFER_SIZE - 1) {

        buffer[bufLen++] = c;

      } else {

        // Discard the entire oversized command.
        bufLen = 0;
        overflow = true;
      }
    }
  }
}

// ============================================================
// COMMAND HANDLER
// ============================================================

void handleCommand(const char *rawCmd) {

  while (*rawCmd == ' ' || *rawCmd == '\t') {
    rawCmd++;
  }

  String cmd = String(rawCmd);

  cmd.trim();
  cmd.toUpperCase();

  if (cmd.length() == 0) {
    return;
  }

  Serial.print(F("[CMD] "));
  Serial.println(cmd);

  // ---------------- START ----------------
  if (cmd == F("START")) {

    resetSensors();

    isMonitoring = true;

    lastTelemetryTime = millis();

    btSerial.println(F("ACK:START_OK"));
    Serial.println(F("ACK:START_OK"));
  }

  // ---------------- STOP ----------------
  else if (cmd == F("STOP")) {

    isMonitoring = false;

    isTestActive = false;
    phoneBuzzerState = false;

    digitalWrite(PIN_BUZZER, LOW);

    btSerial.println(F("ACK:STOP_OK"));
    Serial.println(F("ACK:STOP_OK"));
  }

  // ---------------- TEST ----------------
  else if (cmd == F("TEST")) {

    isTestActive = true;

    testStartTime = millis();

    digitalWrite(PIN_BUZZER, HIGH);

    btSerial.println(F("ACK:TEST_STARTED"));
    Serial.println(F("ACK:TEST_STARTED"));
  }

  // ---------------- RESET ----------------
  else if (cmd == F("RESET")) {

    isMonitoring = false;

    isTestActive = false;
    phoneBuzzerState = false;

    digitalWrite(PIN_BUZZER, LOW);

    // Tell Android that reboot is starting.
    btSerial.println(F("ACK:RESETTING"));
    Serial.println(F("ACK:RESETTING"));

    // Ensure outgoing serial data is transmitted.
    btSerial.flush();
    Serial.flush();

    // Trigger hardware watchdog reset.
    wdt_enable(WDTO_15MS);

    while (true) {
      // Watchdog will restart the MCU.
    }
  }

  // ---------------- BUZZER ON ----------------
  else if (cmd == F("BUZZER_ON")) {

    isTestActive = false;
    phoneBuzzerState = true;

    digitalWrite(PIN_BUZZER, HIGH);

    btSerial.println(F("ACK:BUZZER_ON"));
    Serial.println(F("ACK:BUZZER_ON"));
  }

  // ---------------- BUZZER OFF ----------------
  else if (cmd == F("BUZZER_OFF")) {

    // Cancel any active test as well.
    isTestActive = false;
    phoneBuzzerState = false;

    // Always silence the physical buzzer immediately.
    digitalWrite(PIN_BUZZER, LOW);

    btSerial.println(F("ACK:BUZZER_OFF"));
    Serial.println(F("ACK:BUZZER_OFF"));
  }

  // ---------------- UNKNOWN ----------------
  else {

    btSerial.println(F("ERR:UNKNOWN_COMMAND"));
    Serial.println(F("ERR:UNKNOWN_COMMAND"));
  }
}

// ============================================================
// RESET SENSOR MEASUREMENTS
// ============================================================

void resetSensors() {

  int initialValue = analogRead(PIN_PULSE);

  baseline = initialValue;

  filteredSignal = 0.0f;
  signalAmplitude = 0.0f;

  lastSampleTime = millis();

  aboveThreshold = false;

  lastBeatTime = 0;
  lastValidPulseTime = 0;

  rawBPM = 0.0f;
  adjustedBPM = 0.0f;
  previousBPM = 0.0f;

  beatIndex = 0;
  beatCount = 0;
  stableCount = 0;

  hasValidBpm = false;

  for (byte i = 0; i < NUM_BEATS; i++) {
    beatIntervals[i] = 0;
  }

  tempIndex = 0;
  tempCount = 0;

  filteredTemp = 0.0f;
  hasValidTemp = false;

  for (byte i = 0; i < 5; i++) {
    tempHistory[i] = 0.0f;
  }
}