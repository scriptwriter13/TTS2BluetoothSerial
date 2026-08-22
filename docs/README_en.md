# Bike HUD - TTS to Bluetooth Serial Master

This project is an Android bridge that intercepts navigation announcements (via TTS engine) and GPS speed, sending them via Bluetooth Low Energy (BLE) to an ESP32-based display.

## 🚀 Core Features
- **Smart-Scan:** Automatically finds and connects to the Bike-HUD on app startup.
- **Log Storage:** Persistent logging (`bike_log.txt`) that survives app restarts.
- **Dark Design:** Professional blue-black interface for maximum readability in sunlight.
- **TTS Interception:** Custom TTS service that converts text from apps like Organic Maps directly into BLE packets.

## 🛠 Technical Specifications

### Bluetooth Low Energy (BLE)
- **Service UUID:** `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- **TX Characteristic UUID:** `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
- **MTU:** 512 Bytes (for long navigation texts).

### Data Protocol
Data is sent as plain text with a newline character (`\n`) at the end:
- `SPD:25` -> Speed is 25 km/h.
- `NAV:Turn left in 100m` -> Navigation instruction.

### Firmware Update (OTA)
Updates are performed via a dedicated OTA service over BLE.

- **Service UUID:** `1D14D6EE-FD63-4FA1-BFA4-8F47B42119F0`
- **Characteristic UUID:** `1D14D6EF-FD63-4FA1-BFA4-8F47B42119F0`
- **Protocol Flow:**
  1. **Handshake:** Send `START:BIKE_HUD_OTA_2026`. The ESP32 responds with `READY`.
  2. **Transfer:** The firmware file is sent in 256-byte chunks (Write No Response).
  3. **Finalization:** Send `END`. The ESP32 verifies the checksum and responds with `OTA_SUCCESS` or `OTA_FAIL`.
  4. **Reboot:** Send `reboot` to finalize the process.

## 🎨 Color Concept
Defined in `res/values/colors.xml`:
- **Background:** `#121212` (Dark) / `#000000` (Log)
- **Active Blue:** `#1976D2` (Business Blue)
- **Text:** `#FFFFFF` (Maximum contrast)

## 📂 Project Structure
- `MainActivity.kt`: Control, BLE logic, GPS updates, and UI management.
- `MyBluetoothTtsService.kt`: Interface to the Android system TTS.
- `AndroidManifest.xml`: Permissions for Bluetooth, GPS, and TTS queries.
