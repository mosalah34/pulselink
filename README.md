# PulseLink

A prototype patient-monitoring system: an **Arduino UNO** reads pulse and temperature and streams them over **Bluetooth (HC-05)** to a native **Android app** that shows live charts, decides when to raise an alarm, and tells the Arduino to sound or silence its buzzer.

Built by Eng. Mohamed Salah (Elzehary).

> **Not a medical device.** This is an engineering and learning prototype. The pulse sensor is a hobby-grade optical sensor, the DS18B20 measures probe or surface temperature (not core body temperature), and the thresholds are configurable experimental values. Do not use it for real clinical decisions.

## What it does

- **Arduino firmware** samples a pulse sensor on A0, estimates BPM from the average of recent beat intervals, reads a DS18B20 temperature sensor, and sends one telemetry line per second.
- **Android app** (Kotlin, Jetpack Compose, Material 3) connects to the HC-05 over a Bluetooth Classic SPP socket, parses telemetry, evaluates it, and plays phone alarm tones with vibration.
- **The phone is the only alarm decision maker.** The Arduino just measures and obeys `BUZZER_ON` / `BUZZER_OFF`.
- Readings and alert events are stored in a local Room database. History can be exported as CSV.

## Architecture

| Part | Where | Role |
|---|---|---|
| Firmware | `arduino/PulseLink_Arduino_Firmware/` | Sensing, filtering, telemetry, command handling, watchdog reset |
| Bluetooth | `app/.../bluetooth/` | RFCOMM socket (`BluetoothManager`), line framing and parsing (`SerialProtocolParser`), commands with ACK tracking, retry and timeouts (`ArduinoCommander`), foreground service |
| Decision logic | `app/.../engine/` | `PhoneDecisionEngine` (thresholds, priority, stale data), `AlarmStateMachine` (alarm, acknowledge, buzzer), `AlarmSoundPlayer`, `NotificationHelper` |
| Storage | `app/.../data/` | Room entities and DAOs, repository |
| UI | `app/.../ui/` | Six screens: Dashboard, Terminal, History, Alerts, Settings, Device Connection |

## Hardware

Arduino UNO, HC-05 Bluetooth module, analog pulse sensor, DS18B20 temperature sensor with a 4.7 kΩ pull-up, active buzzer (5 V), breadboard and jumper wires.

| Component | Pin | Arduino UNO |
|---|---|---|
| HC-05 | VCC / GND | 5V / GND |
| HC-05 | TXD | D10 (software serial RX) |
| HC-05 | RXD | D11 through a 1 kΩ / 2 kΩ divider (HC-05 RX is 3.3 V logic) |
| Pulse sensor | Signal | A0 |
| DS18B20 | Data | D2 (4.7 kΩ pull-up to 5V) |
| Active buzzer | + | D8 |

Flash `arduino/PulseLink_Arduino_Firmware/PulseLink_Arduino_Firmware.ino` with the Arduino IDE. Install the `OneWire` and `DallasTemperature` libraries first. Board: Arduino Uno.

## Serial protocol

Telemetry from Arduino (one line per second while monitoring):

```text
HR=80.8;TEMP=25.00;HR_ALARM=NO;TEMP_ALARM=NO
```

The parser also accepts `HR:78.5,TEMP:36.8`, and single-field lines.

Commands from the phone (newline terminated): `START`, `STOP`, `TEST`, `RESET`, `BUZZER_ON`, `BUZZER_OFF`.

The firmware replies with `ACK:START_OK`, `ACK:STOP_OK`, `ACK:TEST_STARTED`, `ACK:TEST_COMPLETE`, `ACK:RESETTING`, `ACK:BUZZER_ON`, `ACK:BUZZER_OFF`, `SYSTEM_READY` after a reboot, and `ERR:UNKNOWN_COMMAND` for anything else.

## Alarm rules (defaults, all configurable in Settings)

| Condition | Default |
|---|---|
| Low heart rate | below 50 BPM |
| High heart rate | above 100 BPM |
| High temperature | 35.0 C or higher (highest priority) |
| Stale data | no valid reading for 5 s |

## Known limitations (read before trusting any number)

1. **Heart-rate calibration is a fixed correction, not a validated one.** The firmware applies `adjusted = raw > 95 ? raw - 40 : raw` once, in the Arduino, and the app deliberately does not apply it again. It is an empirical adjustment for the sensor used in development, not a clinical calibration.
2. The BPM algorithm is a simple adaptive threshold on a filtered signal with a 5-beat average. It is sensitive to finger pressure and motion.
3. The Arduino sends `HR_ALARM` and `TEMP_ALARM` fields in telemetry, but the app makes its own alarm decisions from the readings.
4. Android permissions requested include Bluetooth scan/connect, notifications, foreground service, vibrate and (on older Android) location, which Android requires for Bluetooth scanning.

## Tests

The repository includes JUnit tests under `app/src/test/` for the serial parser, the decision engine, the alarm state machine, the ACK/reset logic, and a high-volume parser stress test. Run them with:

```bash
./gradlew testDebugUnitTest
```

**Status of verification:** these tests were included with the project. They were not run when this repository was published, and the firmware was not compiled or tested on hardware by the publisher. Treat both as unverified until you run them on your own setup.

## Build the app

Requires Android Studio (or the Android SDK) and JDK 17. Min SDK 26, target SDK 34.

```bash
./gradlew assembleDebug
```

Create your own `local.properties` with your `sdk.dir`. It is intentionally not in the repository.

## License

MIT
